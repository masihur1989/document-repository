# Document Repository - Architecture & Plan

## Overview

A high-performance Java-based document repository system using MinIO for object storage and MongoDB for metadata storage, with role-based access control (RBAC).

## Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| Backend | Java 17 + Spring Boot 3.x | REST API, Business Logic |
| Object Storage | MinIO (S3-compatible) | Document binary storage |
| Metadata Storage | MongoDB | Document metadata, user data |
| Authentication | Spring Security + JWT | Secure API access |
| Caching | Caffeine Cache | Performance optimization |

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT APPLICATIONS                             │
│                    (Web Browser, Mobile App, API Clients)                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SPRING BOOT APPLICATION                            │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         REST API LAYER                                 │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │  Document   │  │    Auth     │  │    User     │  │   Search    │  │  │
│  │  │ Controller  │  │ Controller  │  │ Controller  │  │ Controller  │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                       │                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                      SECURITY LAYER (RBAC)                            │  │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐   │  │
│  │  │  JWT Filter     │  │ Role Validator  │  │ Permission Checker  │   │  │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                       │                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                        SERVICE LAYER                                   │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │  Document   │  │   Storage   │  │    User     │  │   Search    │  │  │
│  │  │  Service    │  │   Service   │  │   Service   │  │   Service   │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                       │                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                     CACHING LAYER (Caffeine)                          │  │
│  │              Metadata Cache │ User Cache │ Permission Cache           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                          │                              │
                          ▼                              ▼
┌─────────────────────────────────┐    ┌─────────────────────────────────────┐
│           MONGODB               │    │              MINIO                   │
│  ┌───────────────────────────┐  │    │  ┌─────────────────────────────┐    │
│  │   documents collection    │  │    │  │     documents bucket        │    │
│  │   - id                    │  │    │  │     (binary files)          │    │
│  │   - filename              │  │    │  └─────────────────────────────┘    │
│  │   - contentType           │  │    │                                     │
│  │   - size                  │  │    │  Features:                          │
│  │   - storageKey            │──┼────┼──► S3-compatible API                │
│  │   - ownerId               │  │    │  ► Streaming uploads/downloads      │
│  │   - tags[]                │  │    │  ► Multipart upload support         │
│  │   - createdAt             │  │    │                                     │
│  │   - updatedAt             │  │    └─────────────────────────────────────┘
│  └───────────────────────────┘  │
│  ┌───────────────────────────┐  │
│  │     users collection      │  │
│  │   - id                    │  │
│  │   - username              │  │
│  │   - password (hashed)     │  │
│  │   - email                 │  │
│  │   - role                  │  │
│  │   - createdAt             │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘

```

## Role-Based Access Control (RBAC)

### Roles and Permissions

| Role | Description | Permissions |
|------|-------------|-------------|
| ADMIN | Full system access | All operations + User management |
| EDITOR | Content management | Upload, View, Update, Delete own documents |
| VIEWER | Read-only access | View and Download documents only |

### Permission Matrix

| Operation | ADMIN | EDITOR | VIEWER |
|-----------|-------|--------|--------|
| Upload Document | Yes | Yes | No |
| View Document Metadata | Yes | Yes | Yes |
| Download Document | Yes | Yes | Yes |
| Update Document | Yes | Own only | No |
| Delete Document | Yes | Own only | No |
| List All Documents | Yes | Yes | Yes |
| Manage Users | Yes | No | No |

## Document Upload Workflow

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌─────────┐     ┌─────────┐
│  Client  │     │  Controller  │     │   Service    │     │  MinIO  │     │ MongoDB │
└────┬─────┘     └──────┬───────┘     └──────┬───────┘     └────┬────┘     └────┬────┘
     │                  │                    │                  │               │
     │  POST /documents │                    │                  │               │
     │  + file + metadata                    │                  │               │
     │─────────────────►│                    │                  │               │
     │                  │                    │                  │               │
     │                  │  Validate JWT      │                  │               │
     │                  │  Check RBAC        │                  │               │
     │                  │──────────────────► │                  │               │
     │                  │                    │                  │               │
     │                  │                    │  Stream upload   │               │
     │                  │                    │  (async)         │               │
     │                  │                    │─────────────────►│               │
     │                  │                    │                  │               │
     │                  │                    │  Storage key     │               │
     │                  │                    │◄─────────────────│               │
     │                  │                    │                  │               │
     │                  │                    │  Save metadata   │               │
     │                  │                    │─────────────────────────────────►│
     │                  │                    │                  │               │
     │                  │                    │  Document ID     │               │
     │                  │                    │◄─────────────────────────────────│
     │                  │                    │                  │               │
     │                  │  Document response │                  │               │
     │                  │◄───────────────────│                  │               │
     │                  │                    │                  │               │
     │  201 Created     │                    │                  │               │
     │  + document info │                    │                  │               │
     │◄─────────────────│                    │                  │               │
     │                  │                    │                  │               │
```

## Document Download Workflow

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌─────────┐     ┌─────────┐
│  Client  │     │  Controller  │     │   Service    │     │ MongoDB │     │  MinIO  │
└────┬─────┘     └──────┬───────┘     └──────┬───────┘     └────┬────┘     └────┬────┘
     │                  │                    │                  │               │
     │ GET /documents   │                    │                  │               │
     │    /{id}/download│                    │                  │               │
     │─────────────────►│                    │                  │               │
     │                  │                    │                  │               │
     │                  │  Validate JWT      │                  │               │
     │                  │  Check RBAC        │                  │               │
     │                  │──────────────────► │                  │               │
     │                  │                    │                  │               │
     │                  │                    │  Get metadata    │               │
     │                  │                    │  (check cache)   │               │
     │                  │                    │─────────────────►│               │
     │                  │                    │                  │               │
     │                  │                    │  Document meta   │               │
     │                  │                    │◄─────────────────│               │
     │                  │                    │                  │               │
     │                  │                    │  Stream download │               │
     │                  │                    │─────────────────────────────────►│
     │                  │                    │                  │               │
     │                  │                    │  File stream     │               │
     │                  │                    │◄─────────────────────────────────│
     │                  │                    │                  │               │
     │  200 OK          │                    │                  │               │
     │  + file stream   │                    │                  │               │
     │◄─────────────────│                    │                  │               │
     │                  │                    │                  │               │
```

## Authentication Workflow

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌─────────┐
│  Client  │     │  Controller  │     │ Auth Service │     │ MongoDB │
└────┬─────┘     └──────┬───────┘     └──────┬───────┘     └────┬────┘
     │                  │                    │                  │
     │ POST /auth/login │                    │                  │
     │ {username, pass} │                    │                  │
     │─────────────────►│                    │                  │
     │                  │                    │                  │
     │                  │  Authenticate      │                  │
     │                  │──────────────────► │                  │
     │                  │                    │                  │
     │                  │                    │  Find user       │
     │                  │                    │─────────────────►│
     │                  │                    │                  │
     │                  │                    │  User data       │
     │                  │                    │◄─────────────────│
     │                  │                    │                  │
     │                  │                    │  Verify password │
     │                  │                    │  Generate JWT    │
     │                  │                    │                  │
     │                  │  JWT Token         │                  │
     │                  │◄───────────────────│                  │
     │                  │                    │                  │
     │  200 OK          │                    │                  │
     │  {token, role}   │                    │                  │
     │◄─────────────────│                    │                  │
     │                  │                    │                  │
```

## API Endpoints

### Authentication

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | /api/auth/register | Register new user | Public |
| POST | /api/auth/login | Login and get JWT | Public |

### Documents

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | /api/documents | Upload document | ADMIN, EDITOR |
| GET | /api/documents | List documents (paginated) | All authenticated |
| GET | /api/documents/{id} | Get document metadata | All authenticated |
| GET | /api/documents/{id}/download | Download document | All authenticated |
| PUT | /api/documents/{id} | Update document metadata | ADMIN, EDITOR (own) |
| DELETE | /api/documents/{id} | Delete document | ADMIN, EDITOR (own) |

### Users (Admin only)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | /api/users | List all users | ADMIN |
| GET | /api/users/{id} | Get user details | ADMIN |
| PUT | /api/users/{id}/role | Update user role | ADMIN |
| DELETE | /api/users/{id} | Delete user | ADMIN |

## Performance Optimizations

### 1. Streaming I/O
Documents are streamed directly between client and MinIO without loading entire files into memory, enabling handling of large files efficiently.

### 2. Async Operations
Upload operations use async processing with CompletableFuture to avoid blocking threads during I/O operations.

### 3. Connection Pooling
Both MongoDB and MinIO clients use connection pooling to minimize connection overhead.

### 4. Caching Strategy
Caffeine cache is used for frequently accessed metadata with configurable TTL to reduce database queries.

### 5. Pagination
All list operations support pagination to prevent memory issues with large datasets.

### 6. Indexing
MongoDB indexes on frequently queried fields (ownerId, tags, createdAt) for fast lookups.

## Project Structure

```
document-repository/
├── src/main/java/com/docrepo/
│   ├── config/
│   │   ├── MinioConfig.java
│   │   ├── MongoConfig.java
│   │   ├── SecurityConfig.java
│   │   └── CacheConfig.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── DocumentController.java
│   │   └── UserController.java
│   ├── model/
│   │   ├── Document.java
│   │   ├── User.java
│   │   └── Role.java
│   ├── dto/
│   │   ├── DocumentDTO.java
│   │   ├── UserDTO.java
│   │   └── AuthRequest.java
│   ├── repository/
│   │   ├── DocumentRepository.java
│   │   └── UserRepository.java
│   ├── service/
│   │   ├── DocumentService.java
│   │   ├── StorageService.java
│   │   ├── UserService.java
│   │   └── AuthService.java
│   ├── security/
│   │   ├── JwtTokenProvider.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── CustomUserDetailsService.java
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       └── DocumentNotFoundException.java
├── src/main/resources/
│   └── application.yml
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Docker Compose Setup (for testing)

```yaml
services:
  mongodb:
    image: mongo:7
    ports:
      - "27017:27017"
    volumes:
      - mongodb_data:/data/db

  minio:
    image: minio/minio
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

volumes:
  mongodb_data:
  minio_data:
```

## Implementation Plan

1. **Phase 1: Project Setup**
   - Initialize Spring Boot project with dependencies
   - Configure Docker Compose for MinIO and MongoDB
   - Set up application configuration

2. **Phase 2: Core Models & Repositories**
   - Create Document and User models
   - Implement MongoDB repositories
   - Add database indexes

3. **Phase 3: Storage Layer**
   - Implement MinIO integration
   - Add streaming upload/download support
   - Configure connection pooling

4. **Phase 4: Security Layer**
   - Implement JWT authentication
   - Create RBAC system
   - Add security filters

5. **Phase 5: Service Layer**
   - Implement DocumentService with caching
   - Implement UserService
   - Add async operations

6. **Phase 6: REST API**
   - Create controllers
   - Add validation
   - Implement error handling

7. **Phase 7: Testing**
   - Integration tests
   - Performance testing
   - End-to-end testing

## Java Dependencies (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.2</version>
        <relativePath/>
    </parent>

    <groupId>com.docrepo</groupId>
    <artifactId>document-repository</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>Document Repository</name>

    <properties>
        <java.version>17</java.version>
        <minio.version>8.5.7</minio.version>
        <jjwt.version>0.12.3</jjwt.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>

        <!-- MinIO Client (S3-compatible) -->
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
            <version>${minio.version}</version>
        </dependency>

        <!-- JWT for Authentication -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Caffeine Cache for Performance -->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>

        <!-- Lombok (optional - reduces boilerplate) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### Dependency Breakdown

| Package | Purpose | Used In |
|---------|---------|---------|
| `spring-boot-starter-web` | REST API, HTTP handling, Jackson JSON | Controllers, DTOs |
| `spring-boot-starter-data-mongodb` | MongoDB integration, repositories | Document/User repositories |
| `spring-boot-starter-security` | Authentication, authorization | Security config, RBAC |
| `spring-boot-starter-validation` | Request validation (@Valid, @NotNull) | Controllers, DTOs |
| `spring-boot-starter-cache` | Caching abstraction | Service layer caching |
| `io.minio:minio` | MinIO/S3 client for object storage | StorageService |
| `io.jsonwebtoken:jjwt-*` | JWT token creation/validation | JwtTokenProvider |
| `com.github.ben-manes.caffeine:caffeine` | High-performance caching | CacheConfig |
| `org.projectlombok:lombok` | Reduces boilerplate (@Data, @Builder) | Models, DTOs |

## Document Controller Focus

The DocumentController is the core API layer for document operations. Here's the detailed design:

### DocumentController.java - Key Annotations & Packages

```java
// Spring Web
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

// Spring Security
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// Spring Data
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Validation
import jakarta.validation.Valid;

// Streaming
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

// Java
import java.util.concurrent.CompletableFuture;
```

### Controller Implementation Overview

```java
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final StorageService storageService;

    // Upload - ADMIN and EDITOR only
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<DocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @AuthenticationPrincipal UserDetails userDetails) {
        // Streams file to MinIO, saves metadata to MongoDB
    }

    // List with pagination - All authenticated users
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> listDocuments(Pageable pageable) {
        // Returns paginated document list
    }

    // Get metadata - All authenticated users
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable String id) {
        // Returns document metadata (cached)
    }

    // Download - All authenticated users (streaming)
    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String id) {
        // Streams file directly from MinIO
    }

    // Update metadata - ADMIN or document owner
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @documentSecurity.isOwner(#id, authentication)")
    public ResponseEntity<DocumentDTO> updateDocument(
            @PathVariable String id,
            @Valid @RequestBody DocumentUpdateDTO updateDTO) {
        // Updates metadata only
    }

    // Delete - ADMIN or document owner
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @documentSecurity.isOwner(#id, authentication)")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        // Deletes from both MinIO and MongoDB
    }
}
```

### Key Performance Features in Controller

1. **Streaming Upload**: `MultipartFile.getInputStream()` streams directly to MinIO
2. **Streaming Download**: Returns `InputStreamResource` for zero-copy transfer
3. **Pagination**: Uses Spring Data's `Pageable` for efficient list queries
4. **Caching**: Service layer caches metadata lookups
5. **Async**: Upload can be made async with `@Async` and `CompletableFuture`

## Next Steps

Once this plan is approved, I will proceed with implementation following the phases outlined above.
