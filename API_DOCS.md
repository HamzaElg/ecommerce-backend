# API Documentation

Base URL: `/api/v1`  
All responses use the standard envelope: `{ status, data, pagination?, code?, message?, timestamp? }`

---

## Authentication

### POST /auth/register
**Auth**: None

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response 201:**
```json
{
  "status": "success",
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "role": "CUSTOMER",
    "accessToken": "eyJ...",
    "refreshToken": "eyJ..."
  }
}
```

**Errors:** `409 EMAIL_ALREADY_EXISTS`, `400 VALIDATION_ERROR`

---

### POST /auth/login
**Auth**: None

**Request:** `{ "email": "...", "password": "..." }`

**Response 200:** Same as register response

**Errors:** `401 INVALID_CREDENTIALS`

---

### POST /auth/refresh
**Auth**: None

**Request:** `{ "refreshToken": "eyJ..." }`

**Response 200:** New access + refresh tokens

**Errors:** `401 INVALID_REFRESH_TOKEN`

---

### POST /auth/logout
**Auth**: None (just sends refresh token)

**Request:** `{ "refreshToken": "eyJ..." }`

**Response 200:** `{ "status": "success", "data": null }`

---

## Products

### GET /products/search
**Auth**: None

**Query Parameters:**
| Param | Type | Description |
|-------|------|-------------|
| q | string | Full-text search query |
| category | UUID | Category ID (includes subcategories) |
| brand | string | Exact brand name (case-insensitive) |
| minPrice | decimal | Minimum price |
| maxPrice | decimal | Maximum price |
| minRam | integer | Minimum RAM in GB (JSONB filter) |
| page | int | Page number (default: 0) |
| size | int | Page size (default: 20, max: 100) |

**Response 200:**
```json
{
  "status": "success",
  "data": [
    {
      "id": "uuid",
      "name": "MacBook Pro 16",
      "brand": "Apple",
      "price": 2499.99,
      "categoryId": "uuid",
      "categoryName": "Laptops",
      "specs": { "ram_gb": 32, "cpu": "M3 Pro" },
      "imageUrls": ["https://..."],
      "availableStock": 15,
      "averageRating": 4.7,
      "reviewCount": 23
    }
  ],
  "pagination": { "page": 0, "size": 20, "totalElements": 150, "totalPages": 8 }
}
```

---

### GET /products/{id}
**Auth**: None  
**Response 200:** Single product with full specs, stock, review summary  
**Errors:** `404 RESOURCE_NOT_FOUND`

---

### POST /products
**Auth**: ADMIN only  
**Request:** `{ "name", "brand", "description", "price", "categoryId", "specs": {}, "imageUrls": [] }`  
**Response 201:** Created product  
**Errors:** `403 ACCESS_DENIED`, `400 VALIDATION_ERROR`

---

### PUT /products/{id}
**Auth**: ADMIN only  
**Request:** Same as POST  
**Response 200:** Updated product

---

### DELETE /products/{id}
**Auth**: ADMIN only  
**Response 200:** Soft delete (isActive = false)

---

## Cart

### GET /cart
**Auth**: Required  
**Response 200:**
```json
{
  "status": "success",
  "data": {
    "cartId": "uuid",
    "items": [
      {
        "itemId": "uuid",
        "productId": "uuid",
        "productName": "Dell XPS 15",
        "quantity": 2,
        "unitPrice": 1299.99,
        "subtotal": 2599.98
      }
    ],
    "totalAmount": 2599.98
  }
}
```

---

### POST /cart/items
**Auth**: Required  
**Request:** `{ "productId": "uuid", "quantity": 2 }`  
**Response 200:** Updated cart  
**Errors:** `404 RESOURCE_NOT_FOUND` (product not found or inactive)

---

### PUT /cart/items/{productId}?quantity=3
**Auth**: Required  
**Response 200:** Updated cart (quantity 0 removes item)

---

### DELETE /cart/items/{productId}
**Auth**: Required  
**Response 200:** Cart without the item

---

### DELETE /cart
**Auth**: Required  
**Response 200:** Empty cart

---

## Checkout

### POST /checkout
**Auth**: Required  
**Headers**: `Idempotency-Key: <unique-uuid>` (required)

**Request:**
```json
{
  "shippingAddress": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "US"
  }
}
```

**Response 201:**
```json
{
  "status": "success",
  "data": {
    "orderId": "uuid",
    "status": "PENDING_PAYMENT",
    "totalAmount": 2599.98
  }
}
```

**Errors:**
- `400 CART_EMPTY` - Cart has no items
- `409 INSUFFICIENT_STOCK` - Not enough stock
- `422 PRODUCT_UNAVAILABLE` - Product no longer active

---

## Payments

### POST /payments
**Auth**: Required  
**Headers**: `Idempotency-Key: <unique-uuid>` (required)

**Request:**
```json
{
  "orderId": "uuid",
  "method": "CREDIT_CARD",
  "paymentDetails": { "token": "..." }
}
```

**Response 200:**
```json
{
  "status": "success",
  "data": {
    "paymentId": "uuid",
    "status": "SUCCESS",
    "amount": 2599.98,
    "providerReference": "SIM-abc123"
  }
}
```

**Errors:**
- `403 ORDER_ACCESS_DENIED` - Order belongs to another user
- `422 ORDER_NOT_PAYABLE` - Order not in PENDING_PAYMENT status

> Use method `"FAIL_TEST"` to simulate a payment failure in development.

---

## Orders

### GET /orders
**Auth**: Required  
**Query**: `page=0&size=20`  
**Response 200:** Paginated order history

### GET /orders/{id}
**Auth**: Required  
**Response 200:**
```json
{
  "status": "success",
  "data": {
    "orderId": "uuid",
    "status": "PAID",
    "shippingAddress": { "street": "..." },
    "totalAmount": 2599.98,
    "items": [
      {
        "productName": "Dell XPS 15",
        "unitPrice": 1299.99,
        "quantity": 2,
        "subtotal": 2599.98
      }
    ]
  }
}
```

### PATCH /admin/orders/{id}/status?status=SHIPPED
**Auth**: ADMIN only  
**Response 200:** Updated order

---

## Reviews

### GET /products/{productId}/reviews
**Auth**: None  
**Query**: `page=0&size=20`  
**Response 200:** Paginated reviews with average rating summary

### POST /products/{productId}/reviews
**Auth**: Required (user must have purchased product)  
**Request:** `{ "rating": 5, "comment": "Great laptop!" }`  
**Response 201:** Created review  
**Errors:**
- `409 REVIEW_ALREADY_EXISTS`
- `403 REVIEW_NOT_VERIFIED` - User has not purchased this product

---

## Analytics (Admin Only)

### GET /analytics/sales/daily
Last 30 days: `[{ date, orderCount, revenue }]`

### GET /analytics/sales/weekly  
Last 12 weeks

### GET /analytics/products/top
**Query**: `limit=10&days=30`  
Returns: `[{ productId, productName, totalSold, revenue }]`

### GET /analytics/traffic
Placeholder - connect to your analytics platform

---

## Chatbot

### POST /chat
**Auth**: Optional (authenticated gets personalized context)

**Request:** `{ "message": "Tell me about laptops under $1000" }`

**Response 200:**
```json
{
  "status": "success",
  "data": {
    "reply": "We have great laptops under $1000...",
    "mode": "guest"
  }
}
```

---

## Standard Error Response

```json
{
  "status": "error",
  "code": "INSUFFICIENT_STOCK",
  "message": "Only 2 units available for 'Dell XPS 15', but 5 requested",
  "timestamp": "2026-03-10T10:30:00Z"
}
```

## HTTP Status Codes Used

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Validation error |
| 401 | Authentication required |
| 403 | Access denied (authenticated but forbidden) |
| 404 | Resource not found |
| 409 | Conflict (duplicate email, duplicate review) |
| 422 | Business rule violation |
| 500 | Internal server error |
