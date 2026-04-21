# Electronics E-Commerce Project Backend

A production-grade Spring Boot 3 backend for an electronics e-commerce platform.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Caching | Redis |
| Auth | JWT (JJWT 0.12) + BCrypt |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Flyway |
| JSONB | hypersistence-utils |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |
| Testing | JUnit 5 + Mockito + Testcontainers |

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for PostgreSQL + Redis)

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
```

Or manually:
```bash
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_DB=ecommerce -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  postgres:16-alpine

docker run -d --name redis -p 6379:6379 redis:7-alpine
```

### 2. Configure environment

Copy and edit the environment file:
```bash
cp .env.example .env
```

Required variables:
```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ecommerce
DB_USER=postgres
DB_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=your-256-bit-secret-minimum-32-characters-long
CORS_ORIGINS=http://localhost:3000
```

### 3. Run the application

```bash
mvn spring-boot:run
```

Or with environment variables:
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-DJWT_SECRET=your-secret-key"
```

### 4. Access Swagger UI

Open: http://localhost:8080/swagger-ui.html

## JWT Authentication

This backend uses a two-token strategy:

**Access Token** (15 minutes)
- Short-lived JWT signed with HMAC-SHA256
- Contains: userId, email, role
- Sent as `Authorization: Bearer <token>` header
- Not stored server-side (stateless)

**Refresh Token** (7 days)  
- Longer-lived JWT
- Stored in `sessions` table (allows server-side revocation)
- Used to issue new access tokens
- Rotated on each refresh (old token invalidated)
- Revoked on logout

### Refresh Token Flow
```
Client -> POST /auth/login -> { accessToken, refreshToken }
(15 min later, access token expires)
Client -> POST /auth/refresh { refreshToken } -> { new accessToken, new refreshToken }
Client -> POST /auth/logout { refreshToken } -> session revoked
```

## Modules

| Module | Description |
|--------|-------------|
| `auth` | Register, login, JWT token management, logout |
| `user` | User entity and repository |
| `category` | Product category hierarchy |
| `product` | Product catalog with JSONB specs, full-text search |
| `inventory` | Stock management with optimistic locking |
| `cart` | Shopping cart with price snapshots |
| `checkout` | Transactional checkout with stock reservation |
| `payment` | Idempotent payment processing |
| `order` | Order history and status management |
| `review` | Verified purchase reviews |
| `analytics` | Admin sales and product analytics |
| `chatbot` | AI assistant placeholder |

## API Overview

All endpoints are under `/api/v1/` except health check.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /auth/register | Public | Register new account |
| POST | /auth/login | Public | Get JWT tokens |
| POST | /auth/refresh | Public | Rotate refresh token |
| POST | /auth/logout | Auth | Revoke session |
| GET | /products/search | Public | Search with filters |
| GET | /products/{id} | Public | Product detail |
| POST | /products | Admin | Create product |
| GET | /cart | Auth | View cart |
| POST | /cart/items | Auth | Add to cart |
| POST | /checkout | Auth | Checkout |
| POST | /payments | Auth | Process payment |
| GET | /orders | Auth | Order history |
| GET | /orders/{id} | Auth | Order detail |
| GET | /products/{id}/reviews | Public | Product reviews |
| POST | /products/{id}/reviews | Auth | Submit review |
| GET | /analytics/sales/daily | Admin | Daily sales |
| POST | /chat | Public/Auth | Chatbot |

## Database

Schema managed by Flyway migrations in `src/main/resources/db/migration/`:

- `V1__init_schema.sql` - All tables with constraints
- `V2__indexes_and_search.sql` - GIN indexes, full-text search, triggers

Key design decisions:
- UUID primary keys throughout
- JSONB for flexible product specs and shipping addresses
- Optimistic locking (`@Version`) on Inventory to prevent overselling
- Idempotency keys on orders and payments

## Running Tests

```bash
# Unit tests only (fast, no Docker needed)
mvn test -Dtest="*Test"

# Integration tests (requires Docker for Testcontainers)
mvn test -Dtest="*IntegrationTest"

# All tests
mvn test
```
