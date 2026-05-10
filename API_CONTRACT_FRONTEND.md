# API Contract for Frontend Integration

Backend base URL:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

All API responses use this envelope:

```json
{
  "status": "success",
  "data": {},
  "pagination": {}
}
```

Error responses usually use:

```json
{
  "status": "error",
  "code": "ERROR_CODE",
  "message": "Human-readable message",
  "timestamp": "2026-05-09T00:00:00Z"
}
```

Use `message` for user-facing error display and `code` for frontend logic.

---

## 1. Authentication

### Login

```http
POST /auth/login
```

Request:

```json
{
  "email": "customer@customer.com",
  "password": "customer"
}
```

Admin test credentials:

```json
{
  "email": "test@test.com",
  "password": "123456789"
}
```

Response:

```json
{
  "status": "success",
  "data": {
    "userId": "e6c8c4a0-15c0-40d0-a996-3c8f8112fcbb",
    "email": "customer@customer.com",
    "role": "CUSTOMER",
    "accessToken": "eyJ...",
    "refreshToken": "eyJ..."
  }
}
```

Frontend behavior:
- Store `accessToken`.
- Store `refreshToken` if implementing refresh flow.
- Send access token on protected requests:

```http
Authorization: Bearer <accessToken>
```

---

### Register

```http
POST /auth/register
```

Request:

```json
{
  "email": "newuser@example.com",
  "password": "password123",
  "firstName": "John",
  "lastName": "Doe"
}
```

Response: same shape as login.

Common errors:
- `EMAIL_ALREADY_EXISTS`
- `VALIDATION_ERROR`

---

### Refresh token

```http
POST /auth/refresh
```

Request:

```json
{
  "refreshToken": "eyJ..."
}
```

Response:

```json
{
  "status": "success",
  "data": {
    "userId": "uuid",
    "email": "customer@customer.com",
    "role": "CUSTOMER",
    "accessToken": "new-access-token",
    "refreshToken": "new-refresh-token"
  }
}
```

Frontend behavior:
- If access token expires, call refresh.
- If refresh fails, clear auth state and redirect to login.

---

### Logout

```http
POST /auth/logout
```

Request:

```json
{
  "refreshToken": "eyJ..."
}
```

Response:

```json
{
  "status": "success",
  "data": null
}
```

Frontend behavior:
- Always remove tokens locally after logout.
- Refresh token becomes invalid after logout.
- Existing access token may remain technically valid until it expires, which is normal for stateless JWT.

---

## 2. Public Product APIs

### Search products

```http
GET /products/search
```

Query parameters:

| Param | Type | Required | Description |
|---|---:|---:|---|
| `q` | string | no | Search text |
| `category` | UUID | no | Category ID |
| `brand` | string | no | Brand filter |
| `minPrice` | decimal | no | Minimum price |
| `maxPrice` | decimal | no | Maximum price |
| `minRam` | integer | no | Minimum RAM from JSONB specs |
| `page` | integer | no | Default `0` |
| `size` | integer | no | Default `20`, max should be treated as `100` |

Examples:

```http
GET /products/search
GET /products/search?q=iphone
GET /products/search?brand=Apple
GET /products/search?minPrice=500&maxPrice=1500
GET /products/search?category=ce5863ed-76b7-468c-8813-01beabbcc4de
GET /products/search?minRam=8
```

Response:

```json
{
  "status": "success",
  "data": [
    {
      "id": "c73101ce-daa4-4f84-9def-1079a55947d8",
      "name": "iPhone 15 Pro",
      "brand": "Apple",
      "description": "Latest Apple smartphone",
      "price": 1299.99,
      "categoryId": "ce5863ed-76b7-468c-8813-01beabbcc4de",
      "categoryName": "Smartphones",
      "specs": {
        "color": "Titanium",
        "ram_gb": 8,
        "storage_gb": 256
      },
      "imageUrls": ["https://example.com/iphone.jpg"],
      "active": true,
      "availableStock": 44,
      "averageRating": 5.0,
      "reviewCount": 1,
      "createdAt": "2026-04-24T10:01:14.247145Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Frontend behavior:
- Use `availableStock` to disable out-of-stock products or limit quantity selection.
- Use `imageUrls[0]` as the main product image.
- Use `specs` dynamically; do not hardcode all possible spec fields.

---

### Get product detail

```http
GET /products/{id}
```

Response: same product object as search item.

Errors:
- `RESOURCE_NOT_FOUND`

---

## 3. Categories

### Get active categories

```http
GET /categories
```

Response:

```json
{
  "status": "success",
  "data": [
    {
      "id": "ce5863ed-76b7-468c-8813-01beabbcc4de",
      "name": "Smartphones",
      "slug": "smartphones",
      "parentId": null,
      "parentName": null,
      "active": true,
      "createdAt": "2026-04-19T20:47:18.453443Z",
      "updatedAt": "2026-04-19T20:47:18.453443Z"
    }
  ]
}
```

### Get root categories

```http
GET /categories/root
```

### Get category by ID

```http
GET /categories/{id}
```

### Get category children

```http
GET /categories/{id}/children
```

Frontend behavior:
- Use `/categories` for filters/dropdowns.
- Public endpoints return only active categories.
- Admin endpoint returns inactive categories too.

---

## 4. Cart APIs

All cart APIs require:

```http
Authorization: Bearer <customerAccessToken>
```

### Get cart

```http
GET /cart
```

Response:

```json
{
  "status": "success",
  "data": {
    "cartId": "2cd776bd-d577-431f-a70a-588d860b4c69",
    "items": [
      {
        "itemId": "uuid",
        "productId": "c73101ce-daa4-4f84-9def-1079a55947d8",
        "productName": "iPhone 15 Pro",
        "brand": "Apple",
        "imageUrl": "https://example.com/iphone.jpg",
        "quantity": 2,
        "unitPrice": 1299.99,
        "subtotal": 2599.98
      }
    ],
    "totalAmount": 2599.98
  }
}
```

### Add item to cart

```http
POST /cart/items
```

Request:

```json
{
  "productId": "c73101ce-daa4-4f84-9def-1079a55947d8",
  "quantity": 2
}
```

Errors:
- `RESOURCE_NOT_FOUND`
- `INSUFFICIENT_STOCK`
- `INVALID_QUANTITY`

Frontend behavior:
- Do not allow quantity greater than product `availableStock`.
- Still handle backend `INSUFFICIENT_STOCK`, because stock may change between page load and add-to-cart.

---

### Update item quantity

```http
PUT /cart/items/{productId}?quantity=3
```

Behavior:
- Quantity greater than stock fails with `INSUFFICIENT_STOCK`.
- Quantity `0` removes item.
- Negative quantity should fail.

---

### Remove item

```http
DELETE /cart/items/{productId}
```

### Clear cart

```http
DELETE /cart
```

---

## 5. Checkout

Requires customer token and idempotency key.

```http
POST /checkout
Authorization: Bearer <customerAccessToken>
Idempotency-Key: <unique-random-uuid>
```

Request:

```json
{
  "shippingAddress": {
    "street": "123 Main Street",
    "city": "Rabat",
    "state": "Rabat-Sale-Kenitra",
    "zipCode": "10000",
    "country": "MA"
  }
}
```

Response:

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

Frontend behavior:
- Generate a new idempotency key for each checkout attempt:

```js
const idempotencyKey = crypto.randomUUID();
```

- Checkout creates a `PENDING_PAYMENT` order and reserves stock.
- Cart is cleared after checkout.
- Do not call checkout twice for the same cart with different keys; the cart may already be empty.

Common errors:
- `CART_EMPTY`
- `INSUFFICIENT_STOCK`
- `PRODUCT_UNAVAILABLE`

---

## 6. Payments

Requires customer token and idempotency key.

```http
POST /payments
Authorization: Bearer <customerAccessToken>
Idempotency-Key: <unique-random-uuid>
```

Request:

```json
{
  "orderId": "uuid",
  "method": "CREDIT_CARD",
  "paymentDetails": {
    "token": "tok_test_success"
  }
}
```

Development test tokens:

| Token | Behavior |
|---|---|
| `tok_test_success` | Payment succeeds |
| `tok_test_fail` | Payment fails |

Success response:

```json
{
  "status": "success",
  "data": {
    "paymentId": "uuid",
    "status": "SUCCESS",
    "amount": 2599.98,
    "providerReference": "SIM-c73f9182-55dc-4aa5-9c82-b0e077bfe3c6"
  }
}
```

Failed payment response still uses success envelope because the payment attempt was processed successfully, but the business result is failed:

```json
{
  "status": "success",
  "data": {
    "paymentId": "uuid",
    "status": "FAILED",
    "amount": 2599.98,
    "providerReference": "SIM-313c9a15-9526-4270-adab-eaebdb43e9a8"
  }
}
```

Frontend behavior:
- If `data.status === "SUCCESS"`: show confirmation page.
- If `data.status === "FAILED"`: show payment failed message.
- Current backend behavior after failed payment: order becomes `FAILED` and reservation is released. User must checkout again.
- Repeating the same payment request with the same idempotency key returns the same payment.

Common errors:
- `ORDER_ACCESS_DENIED`
- `ORDER_NOT_PAYABLE`

---

## 7. Orders

Requires customer token.

### Get order history

```http
GET /orders?page=0&size=20
```

Response:

```json
{
  "status": "success",
  "data": [
    {
      "orderId": "uuid",
      "status": "PAID",
      "totalAmount": 2599.98,
      "createdAt": "2026-04-24T10:39:04.780189Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Get order detail

```http
GET /orders/{orderId}
```

Response:

```json
{
  "status": "success",
  "data": {
    "orderId": "uuid",
    "status": "PAID",
    "shippingAddress": {
      "street": "123 Main Street",
      "city": "Rabat",
      "state": "Rabat-Sale-Kenitra",
      "zipCode": "10000",
      "country": "MA"
    },
    "totalAmount": 2599.98,
    "items": [
      {
        "itemId": "uuid",
        "productId": "c73101ce-daa4-4f84-9def-1079a55947d8",
        "productName": "iPhone 15 Pro",
        "unitPrice": 1299.99,
        "quantity": 2,
        "subtotal": 2599.98
      }
    ],
    "createdAt": "2026-04-24T10:39:04.780189Z",
    "updatedAt": "2026-04-24T10:50:45.220506Z"
  }
}
```

Possible statuses:

```text
PENDING_PAYMENT
PAID
FAILED
CANCELLED
SHIPPED
DELIVERED
REFUNDED
```

Frontend behavior:
- `PENDING_PAYMENT`: payment not completed.
- `PAID`: confirmed order.
- `FAILED`: payment failed.
- `CANCELLED`: checkout timed out/expired.
- `SHIPPED`, `DELIVERED`, `REFUNDED`: admin/order lifecycle statuses.

---

## 8. Reviews

### Get product reviews

```http
GET /products/{productId}/reviews?page=0&size=20
```

Response:

```json
{
  "status": "success",
  "data": [
    {
      "reviewId": "uuid",
      "reviewerName": "customer c.",
      "rating": 5,
      "comment": "Excellent product, smooth purchase and delivery experience.",
      "createdAt": "2026-04-26T15:15:37.754904Z",
      "averageRating": 5.0,
      "totalReviews": 1
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Submit review

Requires customer token.

```http
POST /products/{productId}/reviews
Authorization: Bearer <customerAccessToken>
```

Request:

```json
{
  "rating": 5,
  "comment": "Excellent product"
}
```

Rules:
- User must have purchased the product.
- One review per user per product.
- Rating must be between 1 and 5.

Common errors:
- `REVIEW_ALREADY_EXISTS`
- `REVIEW_NOT_VERIFIED`
- `VALIDATION_ERROR`

Frontend behavior:
- Hide or disable review form if user has not purchased, or handle `REVIEW_NOT_VERIFIED`.
- Handle duplicate review with `REVIEW_ALREADY_EXISTS`.

---

## 9. Admin Product APIs

Requires admin token.

```http
Authorization: Bearer <adminAccessToken>
```

### Create product

```http
POST /products
```

Request:

```json
{
  "name": "Samsung Galaxy S24",
  "brand": "Samsung",
  "description": "Flagship Android smartphone",
  "price": 899.99,
  "categoryId": "ce5863ed-76b7-468c-8813-01beabbcc4de",
  "specs": {
    "ram_gb": 8,
    "storage_gb": 256,
    "color": "Black"
  },
  "imageUrls": ["https://example.com/s24.jpg"],
  "initialStockQty": 20
}
```

Important:
- Product creation now includes `initialStockQty`.
- Product update does not update stock. Stock is managed through inventory endpoint.

### Update product

```http
PUT /products/{id}
```

Request:

```json
{
  "name": "Samsung Galaxy S24 Updated",
  "brand": "Samsung",
  "description": "Updated description",
  "price": 849.99,
  "categoryId": "ce5863ed-76b7-468c-8813-01beabbcc4de",
  "specs": {
    "ram_gb": 8,
    "storage_gb": 256,
    "color": "Black"
  },
  "imageUrls": ["https://example.com/s24-updated.jpg"]
}
```

### Soft delete product

```http
DELETE /products/{id}
```

Behavior:
- Sets product inactive.
- Public product search/detail should hide inactive products.

---

## 10. Admin Inventory APIs

Requires admin token.

### List inventory

```http
GET /admin/inventory
```

### Get inventory by product ID

```http
GET /admin/inventory/{productId}
```

Response:

```json
{
  "status": "success",
  "data": {
    "productId": "c73101ce-daa4-4f84-9def-1079a55947d8",
    "productName": "iPhone 15 Pro",
    "brand": "Apple",
    "stockQty": 44,
    "reservedQty": 0,
    "availableQty": 44,
    "version": 15,
    "updatedAt": "2026-05-09T00:24:28.020566Z"
  }
}
```

### Update stock

```http
PATCH /admin/inventory/{productId}/stock
```

Request:

```json
{
  "stockQty": 50
}
```

Rules:
- Stock cannot be negative.
- Stock cannot be less than currently reserved quantity.

---

## 11. Admin Category APIs

Requires admin token.

### List all categories including inactive

```http
GET /admin/categories
```

### Create category

```http
POST /admin/categories
```

Request:

```json
{
  "name": "Accessories",
  "slug": "accessories",
  "parentId": null
}
```

### Update category

```http
PUT /admin/categories/{id}
```

Request:

```json
{
  "name": "Mobile Accessories",
  "slug": "mobile-accessories",
  "parentId": "optional-parent-category-id"
}
```

Rules:
- Slug must be unique.
- Category cannot be its own parent.
- Category cannot be moved under one of its descendants.
- Parent must exist and be active.

### Soft delete category

```http
DELETE /admin/categories/{id}
```

Behavior:
- Sets category inactive.
- Public category list hides inactive category.
- Admin list still shows it.

### Reactivate category

```http
PATCH /admin/categories/{id}/activate
```

---

## 12. Admin CSV Product Import

Requires admin token.

```http
POST /admin/products/import
Content-Type: multipart/form-data
```

Form field:

```text
file
```

CSV columns:

```csv
name,brand,description,price,categorySlug,stockQty,ram_gb,storage_gb,color,imageUrls
```

Example:

```csv
name,brand,description,price,categorySlug,stockQty,ram_gb,storage_gb,color,imageUrls
Google Pixel 8 Pro,Google,"Flagship phone with strong camera",799.99,smartphones,15,12,256,Black,https://example.com/pixel8.jpg
Lenovo Legion 5,Lenovo,"Gaming laptop with RTX GPU",1299.99,laptops,8,16,512,Gray,https://example.com/legion5.jpg
```

If multiple images are supported in one CSV field, separate them using `|`:

```csv
https://example.com/1.jpg|https://example.com/2.jpg
```

Response:

```json
{
  "status": "success",
  "data": {
    "totalRows": 3,
    "created": 2,
    "failed": 1,
    "errors": [
      {
        "row": 4,
        "message": "Active category slug not found: wrong-category"
      }
    ]
  }
}
```

Important:
- CSV import is BOM-safe, so Excel/Windows UTF-8 CSVs should work.
- Valid rows are created even if some rows fail.
- Category is matched by `categorySlug`.
- Inventory is initialized from `stockQty`.

---

## 13. Admin Orders

Requires admin token.

### Update order status

```http
PATCH /admin/orders/{id}/status?status=SHIPPED
```

Allowed statuses:

```text
CREATED
PENDING_PAYMENT
PAID
SHIPPED
DELIVERED
FAILED
CANCELLED
REFUNDED
```

Response: order detail.

Frontend/admin warning:
- Do not allow random status transitions without thinking.
- Example: `PAID -> SHIPPED -> DELIVERED` is valid.
- `FAILED -> DELIVERED` is nonsense.
- Backend currently may not enforce strict transition rules yet, so admin UI should be careful.

---

## 14. Reservation Timeout Behavior

Backend automatically cancels abandoned checkout orders.

Rule:

```text
If order stays PENDING_PAYMENT longer than reservation timeout,
the scheduled job cancels it and releases reserved stock.
```

Result:

```text
PENDING_PAYMENT -> CANCELLED
reservedQty decreases back to 0
availableQty increases back
```

Frontend behavior:
- If order detail returns `CANCELLED`, user must checkout again.
- Do not try to pay a `CANCELLED` order.

---

## 15. Chatbot API

Current old docs mention:

```http
POST /chat
```

But chatbot integration should be rechecked before frontend relies on it.

Recommended future contract:

```http
POST /chat
```

Request:

```json
{
  "message": "Recommend me a laptop under 1500",
  "sessionId": "optional-session-id"
}
```

Response:

```json
{
  "status": "success",
  "data": {
    "reply": "Here are some laptops that match your budget.",
    "recommendedProducts": [
      {
        "id": "uuid",
        "name": "Lenovo Legion 5",
        "price": 1299.99,
        "imageUrls": ["https://example.com/legion5.jpg"]
      }
    ]
  }
}
```

Before frontend integration, verify actual chatbot backend implementation.

---

## 16. Common Error Codes

| Code | Meaning | Frontend behavior |
|---|---|---|
| `VALIDATION_ERROR` | Invalid request body | Show validation messages |
| `RESOURCE_NOT_FOUND` | Resource missing/inactive | Show not found |
| `EMAIL_ALREADY_EXISTS` | Register email duplicate | Show email already exists |
| `INVALID_CREDENTIALS` | Login failed | Show invalid credentials |
| `INVALID_REFRESH_TOKEN` | Refresh failed | Redirect to login |
| `INSUFFICIENT_STOCK` | Requested quantity unavailable | Show stock message and refresh product/cart |
| `CART_EMPTY` | Checkout with empty cart | Redirect to cart |
| `PRODUCT_UNAVAILABLE` | Product inactive/unavailable | Refresh cart/product |
| `ORDER_ACCESS_DENIED` | User tried accessing another user order | Show forbidden |
| `ORDER_NOT_PAYABLE` | Order not in `PENDING_PAYMENT` | Show cannot pay this order |
| `REVIEW_ALREADY_EXISTS` | Duplicate review | Disable review form |
| `REVIEW_NOT_VERIFIED` | User has not purchased product | Hide/disable review form |
| `CATEGORY_SLUG_EXISTS` | Duplicate category slug | Show slug exists |
| `INVALID_CATEGORY_PARENT` | Category parent is itself | Show invalid hierarchy |
| `INVALID_CATEGORY_HIERARCHY` | Category cycle detected | Show invalid hierarchy |
| `INVALID_STOCK_QUANTITY` | Negative/invalid stock | Show invalid stock |

---

## 17. Frontend Integration Checklist

### Required `.env`

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

### Axios setup example

```js
import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("accessToken");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export default api;
```

### Idempotency key helper

```js
const createIdempotencyKey = () => crypto.randomUUID();
```

Use it for:
- checkout
- payment

### Recommended pages to wire first

```text
1. Product listing/search
2. Product detail
3. Login/register
4. Cart
5. Checkout
6. Payment
7. Orders
8. Reviews
9. Admin product/category/inventory
10. CSV import
```

---

## 18. Known Not-Final Areas

These are not blockers for frontend integration, but should be hardened later:

```text
1. Strict admin order status transition rules
2. Chatbot API final verification
3. Rate limiting for auth endpoints
4. More consistent validation error details
5. Production Docker deployment
6. Flyway migrations for all manual DB changes
7. Distributed locking for ReservationTimeoutJob if multiple backend instances are deployed
```
