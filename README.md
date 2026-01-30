# Document Repository

A high-performance Java-based document repository system using MinIO for object storage and MongoDB for metadata storage, with role-based access control (RBAC).

## Features

- **Document Management**: Upload, download, update, and delete documents
- **Object Storage**: MinIO (S3-compatible) for scalable file storage
- **Metadata Storage**: MongoDB for fast metadata queries
- **Role-Based Access Control**: ADMIN, EDITOR, VIEWER roles
- **Performance Optimized**: Streaming I/O, caching, connection pooling
- **JWT Authentication**: Secure stateless authentication

## Technology Stack

- Java 17
- Spring Boot 3.2.2
- MongoDB 7
- MinIO (S3-compatible)
- JWT (jjwt 0.12.3)
- Caffeine Cache

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- MongoDB on port 27017
- MinIO on ports 9000 (API) and 9001 (Console)

### 2. Build and Run

```bash
mvn clean install
mvn spring-boot:run
```

The application starts on http://localhost:8080

### 3. Access MinIO Console

Open http://localhost:9001 and login with:
- Username: `minioadmin`
- Password: `minioadmin`

## API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login and get JWT |

### Documents

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/documents` | Upload document | ADMIN, EDITOR |
| GET | `/api/documents` | List documents (paginated) | All authenticated |
| GET | `/api/documents/{id}` | Get document metadata | All authenticated |
| GET | `/api/documents/{id}/download` | Download document | All authenticated |
| PUT | `/api/documents/{id}` | Update document metadata | ADMIN, Owner |
| DELETE | `/api/documents/{id}` | Delete document | ADMIN, Owner |
| GET | `/api/documents/my` | Get my documents | All authenticated |
| GET | `/api/documents/search?q=term` | Search documents | All authenticated |
| GET | `/api/documents/tag/{tag}` | Filter by tag | All authenticated |

### Users (Admin only)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | List all users |
| GET | `/api/users/{id}` | Get user details |
| PUT | `/api/users/{id}/role?role=EDITOR` | Update user role |
| DELETE | `/api/users/{id}` | Delete user |

## Usage Examples

### Register a User

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "email": "john@example.com", "password": "password123"}'
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "john", "password": "password123"}'
```

### Upload Document

```bash
curl -X POST http://localhost:8080/api/documents \
  -H "Authorization: Bearer <token>" \
  -F "file=@/path/to/document.pdf" \
  -F "tags=report,2024" \
  -F "description=Annual report"
```

### Download Document

```bash
curl -X GET http://localhost:8080/api/documents/{id}/download \
  -H "Authorization: Bearer <token>" \
  -o downloaded_file.pdf
```

### List Documents with Pagination

```bash
curl -X GET "http://localhost:8080/api/documents?page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer <token>"
```

## Role Permissions

| Role | Upload | View | Update | Delete | Manage Users |
|------|--------|------|--------|--------|--------------|
| ADMIN | Yes | Yes | All | All | Yes |
| EDITOR | Yes | Yes | Own | Own | No |
| VIEWER | No | Yes | No | No | No |

## Configuration

Environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGODB_URI` | `mongodb://localhost:27017/docrepo` | MongoDB connection string |
| `MINIO_ENDPOINT` | `http://localhost:9000` | MinIO endpoint |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |
| `MINIO_BUCKET` | `documents` | MinIO bucket name |
| `JWT_SECRET` | (base64 encoded) | JWT signing key |
| `JWT_EXPIRATION` | `86400000` | JWT expiration (24h) |

## Project Structure

```
src/main/java/com/docrepo/
├── config/           # Configuration classes
├── controller/       # REST controllers
├── dto/              # Data transfer objects
├── exception/        # Exception handling
├── model/            # Domain models
├── repository/       # MongoDB repositories
├── security/         # JWT & RBAC
└── service/          # Business logic
```

## License

MIT
