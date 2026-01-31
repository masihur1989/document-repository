# Large File Handling Strategies

This document explains three approaches for handling large file uploads (up to 1GB+) in the Document Repository application, with detailed analysis of each option's trade-offs.

## Table of Contents

1. [Overview](#overview)
2. [Option 1: Presigned URL Upload (Recommended)](#option-1-presigned-url-upload-recommended)
3. [Option 2: Chunked Upload API](#option-2-chunked-upload-api)
4. [Option 3: Enhanced Streaming Upload](#option-3-enhanced-streaming-upload)
5. [Comparison Matrix](#comparison-matrix)
6. [Recommendation](#recommendation)

---

## Overview

When handling large files (100MB to 1GB+), the traditional approach of uploading through the application server becomes problematic due to:

- **Memory pressure**: Buffering large files consumes significant RAM
- **Thread blocking**: Each upload blocks a server thread for minutes
- **Bandwidth doubling**: Data travels Client → App Server → MinIO (2x bandwidth)
- **No resume capability**: Network interruption requires complete restart
- **Scalability limits**: Server becomes the bottleneck

This document presents three solutions, each with different trade-offs between complexity, performance, and features.

---

## Option 1: Presigned URL Upload (Recommended)

### How It Works

The presigned URL approach bypasses the application server for file data transfer. Instead, the server generates a time-limited, cryptographically signed URL that allows the client to upload directly to MinIO/S3.

```
┌──────────┐                                    ┌──────────────┐
│  Client  │  1. Request upload permission      │  App Server  │
│          │ ──────────────────────────────────►│              │
│          │                                    │              │
│          │  2. Return presigned URL           │              │
│          │ ◄──────────────────────────────────│              │
└──────────┘                                    └──────────────┘
     │
     │  3. Upload file directly (1GB)
     │     App Server NOT involved
     ▼
┌──────────┐
│  MinIO   │
│          │
└──────────┘
     │
     │  4. Upload complete
     ▼
┌──────────┐                                    ┌──────────────┐
│  Client  │  5. Confirm upload + metadata      │  App Server  │
│          │ ──────────────────────────────────►│   + MongoDB  │
│          │                                    │              │
│          │  6. Return DocumentDTO             │              │
│          │ ◄──────────────────────────────────│              │
└──────────┘                                    └──────────────┘
```

### Implementation

#### Server-Side Endpoints

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @PostMapping("/upload-url")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<PresignedUrlResponse> getUploadUrl(
            @RequestParam String filename,
            @RequestParam String contentType,
            @RequestParam Long size,
            @AuthenticationPrincipal UserPrincipal user) {

        // Generate unique storage key
        String storageKey = UUID.randomUUID() + "-" + sanitizeFilename(filename);

        // Generate presigned URL valid for 1 hour
        String presignedUrl = minioClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .bucket(bucketName)
                .object(storageKey)
                .method(Method.PUT)
                .expiry(1, TimeUnit.HOURS)
                .extraHeaders(Map.of("Content-Type", contentType))
                .build()
        );

        return ResponseEntity.ok(new PresignedUrlResponse(
            presignedUrl,
            storageKey,
            Instant.now().plus(1, ChronoUnit.HOURS)
        ));
    }

    @PostMapping("/confirm-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<DocumentDTO> confirmUpload(
            @Valid @RequestBody UploadConfirmRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        // Verify file exists in MinIO
        StatObjectResponse stat = minioClient.statObject(
            StatObjectArgs.builder()
                .bucket(bucketName)
                .object(request.getStorageKey())
                .build()
        );

        // Create metadata record
        Document document = Document.builder()
            .filename(request.getFilename())
            .originalFilename(request.getFilename())
            .contentType(stat.contentType())
            .size(stat.size())
            .storageKey(request.getStorageKey())
            .ownerId(user.getId())
            .ownerUsername(user.getUsername())
            .tags(request.getTags())
            .description(request.getDescription())
            .build();

        Document saved = documentRepository.save(document);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved));
    }
}
```

#### Client-Side Implementation (JavaScript Example)

```javascript
async function uploadLargeFile(file, tags, description) {
    // Step 1: Get presigned URL
    const urlResponse = await fetch('/api/documents/upload-url?' + new URLSearchParams({
        filename: file.name,
        contentType: file.type,
        size: file.size
    }), {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
    });
    const { presignedUrl, storageKey } = await urlResponse.json();

    // Step 2: Upload directly to MinIO
    await fetch(presignedUrl, {
        method: 'PUT',
        body: file,
        headers: { 'Content-Type': file.type }
    });

    // Step 3: Confirm upload and create metadata
    const confirmResponse = await fetch('/api/documents/confirm-upload', {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            storageKey,
            filename: file.name,
            tags,
            description
        })
    });

    return await confirmResponse.json();
}
```

### Advantages

| Advantage | Description |
|-----------|-------------|
| Zero server bandwidth | App server handles only metadata (~1KB per upload) |
| Unlimited scalability | MinIO handles all file transfers |
| Resume capability | MinIO supports multipart uploads natively |
| Thread efficiency | Server threads blocked for milliseconds, not minutes |
| Memory efficiency | No file buffering on app server |

### Disadvantages

| Disadvantage | Description |
|--------------|-------------|
| Client complexity | Requires client-side changes |
| No server-side validation | Cannot scan/validate file content before storage |
| CORS configuration | MinIO must allow direct browser uploads |
| Two-step process | Upload and confirmation are separate operations |

### Security Considerations

Presigned URLs are secure because:

1. **Cryptographic signature**: URL contains HMAC-SHA256 signature using MinIO's secret key
2. **Time-limited**: URLs expire after configured duration (typically 15-60 minutes)
3. **Scope-limited**: URL is valid only for specific bucket, object key, and HTTP method
4. **Tamper-proof**: Any modification invalidates the signature

Example presigned URL structure:
```
https://minio.example.com/documents/abc123-file.pdf
  ?X-Amz-Algorithm=AWS4-HMAC-SHA256
  &X-Amz-Credential=minioadmin/20260131/us-east-1/s3/aws4_request
  &X-Amz-Date=20260131T120000Z
  &X-Amz-Expires=3600
  &X-Amz-SignedHeaders=host
  &X-Amz-Signature=a1b2c3d4e5f6789...
```

---

## Option 2: Chunked Upload API

### How It Works

The chunked upload approach splits large files into smaller pieces (typically 5-10MB each) that are uploaded separately and reassembled on the server.

```
┌──────────┐                                    ┌──────────────┐
│  Client  │  1. Initialize upload              │  App Server  │
│          │ ──────────────────────────────────►│              │
│          │  ◄─────────────────────────────────│              │
│          │     Return uploadId                │              │
│          │                                    │              │
│          │  2. Upload chunk 1 (10MB)          │              │
│          │ ──────────────────────────────────►│  ──► Temp    │
│          │                                    │      Storage │
│          │  3. Upload chunk 2 (10MB)          │              │
│          │ ──────────────────────────────────►│  ──► Temp    │
│          │                                    │      Storage │
│          │  ... (repeat for all chunks)       │              │
│          │                                    │              │
│          │  N. Complete upload                │              │
│          │ ──────────────────────────────────►│  Assemble    │
│          │                                    │  ──► MinIO   │
│          │  Return DocumentDTO                │              │
│          │ ◄──────────────────────────────────│              │
└──────────┘                                    └──────────────┘
```

### Implementation

#### Server-Side Endpoints

```java
@RestController
@RequestMapping("/api/documents/chunked")
public class ChunkedUploadController {

    private final Map<String, ChunkedUploadSession> sessions = new ConcurrentHashMap<>();

    @PostMapping("/init")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<ChunkedUploadInitResponse> initUpload(
            @RequestBody ChunkedUploadInitRequest request,
            @AuthenticationPrincipal UserPrincipal user) {

        String uploadId = UUID.randomUUID().toString();
        int totalChunks = (int) Math.ceil((double) request.getFileSize() / CHUNK_SIZE);

        ChunkedUploadSession session = new ChunkedUploadSession(
            uploadId,
            request.getFilename(),
            request.getContentType(),
            request.getFileSize(),
            totalChunks,
            user.getId(),
            user.getUsername()
        );

        sessions.put(uploadId, session);

        return ResponseEntity.ok(new ChunkedUploadInitResponse(
            uploadId,
            totalChunks,
            CHUNK_SIZE
        ));
    }

    @PostMapping("/{uploadId}/chunk/{chunkIndex}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) {

        ChunkedUploadSession session = sessions.get(uploadId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        // Save chunk to temp storage
        Path chunkPath = tempDir.resolve(uploadId).resolve("chunk-" + chunkIndex);
        Files.createDirectories(chunkPath.getParent());
        chunk.transferTo(chunkPath);

        session.markChunkComplete(chunkIndex);

        return ResponseEntity.ok(new ChunkUploadResponse(
            chunkIndex,
            session.getCompletedChunks(),
            session.getTotalChunks()
        ));
    }

    @PostMapping("/{uploadId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<DocumentDTO> completeUpload(
            @PathVariable String uploadId,
            @RequestBody ChunkedUploadCompleteRequest request) {

        ChunkedUploadSession session = sessions.get(uploadId);
        if (session == null || !session.isComplete()) {
            return ResponseEntity.badRequest().build();
        }

        // Assemble chunks and upload to MinIO
        String storageKey = assembleAndUpload(session);

        // Create metadata record
        Document document = createDocument(session, storageKey, request);

        // Cleanup
        cleanupTempFiles(uploadId);
        sessions.remove(uploadId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(document));
    }

    @GetMapping("/{uploadId}/status")
    public ResponseEntity<ChunkedUploadStatusResponse> getStatus(
            @PathVariable String uploadId) {

        ChunkedUploadSession session = sessions.get(uploadId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new ChunkedUploadStatusResponse(
            session.getCompletedChunks(),
            session.getTotalChunks(),
            session.getMissingChunks()
        ));
    }
}
```

#### Client-Side Implementation (JavaScript Example)

```javascript
async function uploadLargeFileChunked(file, tags, description) {
    const CHUNK_SIZE = 10 * 1024 * 1024; // 10MB chunks

    // Step 1: Initialize upload
    const initResponse = await fetch('/api/documents/chunked/init', {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            filename: file.name,
            contentType: file.type,
            fileSize: file.size
        })
    });
    const { uploadId, totalChunks } = await initResponse.json();

    // Step 2: Upload chunks (can be parallelized)
    for (let i = 0; i < totalChunks; i++) {
        const start = i * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        const chunk = file.slice(start, end);

        const formData = new FormData();
        formData.append('chunk', chunk);

        await fetch(`/api/documents/chunked/${uploadId}/chunk/${i}`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData
        });

        // Update progress: ((i + 1) / totalChunks * 100)%
    }

    // Step 3: Complete upload
    const completeResponse = await fetch(`/api/documents/chunked/${uploadId}/complete`, {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ tags, description })
    });

    return await completeResponse.json();
}
```

### Advantages

| Advantage | Description |
|-----------|-------------|
| Resume capability | Failed uploads can resume from last successful chunk |
| Progress tracking | Precise progress updates per chunk |
| Parallel uploads | Multiple chunks can upload simultaneously |
| Standard HTTP | Works with any HTTP client |
| Server-side validation | Can validate each chunk or final file |

### Disadvantages

| Disadvantage | Description |
|--------------|-------------|
| Server bandwidth | All data still flows through app server |
| Temp storage required | Need disk space for chunk assembly |
| Complex implementation | More endpoints and state management |
| Memory pressure | Assembly process can be memory-intensive |
| Thread blocking | Still blocks threads during chunk uploads |

---

## Option 3: Enhanced Streaming Upload

### How It Works

This is an enhancement of the current upload approach, optimized for larger files through configuration tuning and streaming optimizations.

```
┌──────────┐                                    ┌──────────────┐
│  Client  │  Upload file (streaming)           │  App Server  │
│          │ ══════════════════════════════════►│              │
│          │                                    │   │          │
│          │                                    │   │ Stream   │
│          │                                    │   ▼          │
│          │                                    │ ┌──────────┐ │
│          │                                    │ │  MinIO   │ │
│          │                                    │ └──────────┘ │
│          │  Return DocumentDTO                │              │
│          │ ◄──────────────────────────────────│              │
└──────────┘                                    └──────────────┘
```

### Implementation

#### Configuration (application.yml)

```yaml
server:
  port: 8080
  tomcat:
    # Thread pool for handling concurrent uploads
    threads:
      max: 500
      min-spare: 50
    # Connection settings
    max-connections: 10000
    accept-count: 1000
    connection-timeout: 300000  # 5 minutes for large uploads
    max-swallow-size: -1  # No limit on request body swallowing

spring:
  servlet:
    multipart:
      # File size limits
      max-file-size: 1GB
      max-request-size: 1GB
      # Threshold for writing to disk (files larger than this go to disk)
      file-size-threshold: 50MB
      # Temp directory (should be on fast SSD)
      location: /data/uploads/tmp
      # Enable lazy resolution for streaming
      resolve-lazily: true

  # Async configuration for non-blocking uploads
  task:
    execution:
      pool:
        core-size: 50
        max-size: 200
        queue-capacity: 1000
```

#### Optimized Controller

```java
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<DocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal UserPrincipal userPrincipal) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Stream directly to MinIO without loading entire file into memory
        try (InputStream inputStream = file.getInputStream()) {
            String storageKey = storageService.uploadFile(
                inputStream,
                file.getContentType(),
                file.getSize(),
                file.getOriginalFilename()
            );

            Document document = Document.builder()
                .filename(file.getOriginalFilename())
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .storageKey(storageKey)
                .ownerId(userPrincipal.getId())
                .ownerUsername(userPrincipal.getUsername())
                .tags(tags)
                .description(description)
                .build();

            Document saved = documentRepository.save(document);
            return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(saved));
        }
    }
}
```

#### Optimized Storage Service

```java
@Service
public class StorageService {

    private final MinioClient minioClient;

    // Use larger part size for better throughput with large files
    private static final long PART_SIZE = 50 * 1024 * 1024; // 50MB parts

    public String uploadFile(InputStream inputStream, String contentType,
                            long size, String originalFilename) {
        String storageKey = generateStorageKey(originalFilename);

        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storageKey)
                    .stream(inputStream, size, PART_SIZE)
                    .contentType(contentType)
                    .build()
            );
            return storageKey;
        } catch (Exception e) {
            throw new StorageException("Failed to upload file", e);
        }
    }
}
```

### Advantages

| Advantage | Description |
|-----------|-------------|
| Simple implementation | Minimal code changes from current approach |
| Single request | One HTTP request for entire upload |
| Server-side validation | Full access to file content |
| Familiar API | Standard multipart form upload |

### Disadvantages

| Disadvantage | Description |
|--------------|-------------|
| No resume capability | Network failure requires complete restart |
| Bandwidth doubling | Data flows through app server to MinIO |
| Thread blocking | Each upload blocks a thread for minutes |
| Limited scalability | App server becomes bottleneck |
| Memory/disk pressure | Large files buffered before streaming |

---

## Comparison Matrix

| Feature | Presigned URL | Chunked Upload | Streaming |
|---------|---------------|----------------|-----------|
| **Implementation Complexity** | Moderate | High | Low |
| **Client Changes Required** | Yes | Yes | No |
| **Server Bandwidth Usage** | Minimal (~1KB) | Full file size | Full file size |
| **Resume Capability** | Yes (native) | Yes (manual) | No |
| **Progress Tracking** | Client-side | Per chunk | Limited |
| **Max Concurrent 1GB Uploads** | Unlimited* | 50-100 | 10-20 |
| **Thread Efficiency** | Excellent | Poor | Poor |
| **Memory Efficiency** | Excellent | Moderate | Poor |
| **Server-side Validation** | No | Yes | Yes |
| **Scalability** | Excellent | Moderate | Poor |

*Limited only by MinIO/S3 capacity

### Performance Estimates (5000 concurrent users, 1000 req/s)

| Metric | Presigned URL | Chunked Upload | Streaming |
|--------|---------------|----------------|-----------|
| Max throughput | 10,000+ req/s | 500-1000 req/s | 100-200 req/s |
| Avg latency (1GB) | 30-60s | 60-120s | 60-300s |
| Error rate at peak | <1% | 5-10% | 30-50% |
| Server CPU usage | 5-10% | 60-80% | 80-100% |
| Server memory | 1-2GB | 10-50GB | 50-200GB |

---

## Recommendation

### For Production Systems (Recommended: Presigned URL)

If you need to support:
- 1GB+ file uploads
- 5000+ concurrent users
- High availability and scalability

**Use Presigned URL Upload** because:
1. App server handles zero file data
2. Scales horizontally without bottleneck
3. Native resume support via S3 multipart
4. Industry standard (AWS, GCP, Azure all use this)

### For Internal/Enterprise Systems (Alternative: Chunked Upload)

If you need:
- Server-side file validation/scanning
- Detailed audit logging
- Moderate scale (100-500 concurrent users)

**Use Chunked Upload** because:
1. Full control over file content
2. Resume capability
3. Progress tracking

### For Simple/Low-Scale Systems (Acceptable: Streaming)

If you have:
- Small user base (<100 concurrent)
- Files under 100MB typically
- Simple deployment requirements

**Use Enhanced Streaming** because:
1. Minimal implementation effort
2. Single request simplicity
3. Works with existing clients

---

## Implementation Checklist

### Presigned URL Implementation
- [ ] Add `getUploadUrl` endpoint
- [ ] Add `confirmUpload` endpoint
- [ ] Configure MinIO CORS for direct browser uploads
- [ ] Update client application
- [ ] Add cleanup job for orphaned uploads
- [ ] Add monitoring for presigned URL generation

### Chunked Upload Implementation
- [ ] Add `initUpload` endpoint
- [ ] Add `uploadChunk` endpoint
- [ ] Add `completeUpload` endpoint
- [ ] Add `getStatus` endpoint
- [ ] Implement chunk assembly logic
- [ ] Add session timeout/cleanup
- [ ] Configure temp storage
- [ ] Update client application

### Streaming Enhancement Implementation
- [ ] Update `application.yml` with optimized settings
- [ ] Configure temp directory on fast storage
- [ ] Increase Tomcat thread pool
- [ ] Add connection timeout handling
- [ ] Monitor memory usage
- [ ] Add upload progress endpoint (optional)

---

## References

### S3-Compatible Storage Providers

- [MinIO Presigned URLs Documentation](https://min.io/docs/minio/linux/developers/java/API.html#getPresignedObjectUrl) - Official MinIO Java SDK documentation for generating presigned URLs
- [PUSHR Sonic S3 API Compatibility](https://pushr.io/knowledgebase/sonic-s3-api-compatibility/) - Sonic S3-compatible object storage API reference, supports presigned URLs via standard S3 SDK
- [PUSHR Sonic with Node.js](https://pushr.io/knowledgebase/use-sonic-object-storage-with-nodejs/) - Using Sonic S3 storage with AWS SDK for JavaScript/Node.js

### AWS S3 Documentation

- [AWS S3 Presigned URLs Overview](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) - Comprehensive guide on presigned URL concepts and usage
- [AWS S3 Uploading with Presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/PresignedUrlUploadObject.html) - Detailed documentation for upload presigned URLs
- [AWS S3 Multipart Upload](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html) - Multipart upload for large files (5GB+)
- [AWS SDK for Java S3Presigner](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html) - Java SDK presigner API reference

### Spring Boot & Server Configuration

- [Spring Boot File Upload Best Practices](https://spring.io/guides/gs/uploading-files/) - Official Spring guide for file uploads
- [Tomcat Performance Tuning](https://tomcat.apache.org/tomcat-10.0-doc/config/http.html) - Tomcat HTTP connector configuration

### Additional Resources

- [S3 Uploads: Proxies vs Presigned URLs vs Presigned POSTs](https://zaccharles.medium.com/s3-uploads-proxies-vs-presigned-urls-vs-presigned-posts-9661e2b37932) - Detailed comparison of S3 upload strategies
- [Securing S3 Presigned URLs for Serverless Applications](https://aws.amazon.com/blogs/compute/securing-amazon-s3-presigned-urls-for-serverless-applications) - AWS best practices for secure presigned URL implementation
