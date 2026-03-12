# Business Logic Documentation

## 1. Authentication Flow

### Register
1. Validate email format and uniqueness (throws `EMAIL_ALREADY_EXISTS` if taken)
2. Hash password with BCrypt (strength 10 - safe default)
3. Create User with role `CUSTOMER` (admins created manually/via migration)
4. Issue access + refresh tokens
5. Persist Session (refresh token) in DB

### Login
1. Find user by email
2. Compare provided password with BCrypt hash
3. Both wrong-user and wrong-password cases return `BadCredentialsException` (prevents email enumeration)
4. Issue tokens and create Session

## 2. Refresh Token Flow

```
Login → [accessToken (15min), refreshToken (7days)] persisted to sessions table

When access expires:
POST /auth/refresh { refreshToken }
→ Validate token: not revoked, not expired
→ Revoke old session
→ Issue new access + refresh tokens
→ Persist new session

On logout:
POST /auth/logout { refreshToken }
→ Mark session.revoked = true
→ Access token expires naturally (cannot be actively revoked without blocklist)
```

**Why DB-stored refresh tokens?** Allows server-side revocation on logout, security breach, or suspicious activity. Access tokens are stateless (no DB lookup on every request).

## 3. Product Search Flow

Search combines multiple optional PostgreSQL features:

1. **Full-text search** (`q` param): uses `tsvector` column with `plainto_tsquery`. GIN indexed.
2. **Trigram search** (fallback): `ILIKE '%query%'` for partial matches
3. **Category filter**: includes subcategories via `parent_id` check
4. **Brand filter**: case-insensitive exact match
5. **Price range**: `minPrice`, `maxPrice` with NUMERIC comparison
6. **JSONB spec filter**: `minRam` maps to `(specs->>'ram_gb')::integer >= minRam`

Results ranked by `ts_rank` when text query is provided.
Results cached in Redis for 5 minutes, evicted on product changes.

## 4. Cart Flow

- **Cart creation**: lazy - created on first item add, not at registration
- **Price snapshot**: when an item is added, the current product price is snapshotted into `cart_items.unit_price_snapshot`
  - This is displayed to the user in the cart
  - However, the final order uses current price at checkout (see Checkout section)
- **Adding existing item**: increments quantity (no duplicate entries)
- **Updating to 0**: removes the item
- Cart is linked to `user_id`. Guest cart support can be added via `session_id` field.

## 5. Checkout Flow

### Sequence
```
1. Validate: cart not empty, all products active
2. For each cart item:
   a. Acquire PESSIMISTIC_WRITE lock on inventory row
   b. Check availableQty >= requested quantity
   c. reservedQty += quantity (reserve stock)
   d. Capture current product price (live price, not snapshot)
3. Create Order in PENDING_PAYMENT status with idempotency key
4. Create OrderItem records with name/price snapshots
5. Clear cart (user must re-add if they want to order again)
6. Return: orderId, totalAmount, status=PENDING_PAYMENT
```

### Idempotency
If the same `Idempotency-Key` header is used twice, the second request returns the already-created order without re-executing checkout. Prevents double orders from network retries.

### Price Policy
**Design choice**: order total uses **current product prices** (not cart snapshots). Rationale: prevents billing at stale prices if items were in cart for a long time. Users see snapshot prices in cart UI, but are charged current price at checkout. This is standard in most e-commerce platforms.

## 6. Stock Reservation Logic

```
availableQty = stockQty - reservedQty

At checkout:
  reservedQty += qty        (stock reserved, not yet deducted)

At payment SUCCESS:
  stockQty -= qty           (stock actually consumed)
  reservedQty -= qty        (reservation fulfilled)

At payment FAILURE or timeout:
  reservedQty -= qty        (reservation released, stock available again)
  stockQty unchanged        (never deducted)
```

**Anti-overselling**: PostgreSQL row-level `SELECT FOR UPDATE` lock during reservation. Two concurrent checkouts for the last item: the second transaction blocks until the first commits, then sees 0 available stock and fails with `INSUFFICIENT_STOCK`.

**Reservation timeout**: Scheduled job runs every 5 minutes. Orders stuck in `PENDING_PAYMENT` for > 30 minutes (configurable) are cancelled and reservations released.

## 7. Payment Flow

```
1. Idempotency check: if key already used, return existing payment
2. Validate order belongs to requesting user
3. Validate order is in PENDING_PAYMENT status (can't pay PAID or CANCELLED order)
4. Call payment provider (currently simulated; replace body of simulatePaymentProvider())
5a. On SUCCESS:
    - stockQty -= qty, reservedQty -= qty (for each order item)
    - order.status = PAID
    - payment.status = SUCCESS
5b. On FAILURE:
    - reservedQty -= qty (release reservation)
    - order.status = FAILED
    - payment.status = FAILED
```

**Idempotency**: the `Idempotency-Key` header must be unique per payment attempt. Same key returns existing result without re-processing.

## 8. Review Logic

**Verified purchase requirement**: users must have a `PAID` order containing the product before they can review it. This prevents fake reviews.

**One review per user per product**: enforced at both DB level (`UNIQUE (user_id, product_id)`) and service level (check before save).

**Reviewer anonymization**: reviews show `FirstName L.` (last name initial) for privacy.

## 9. Admin Logic

Admin users (role = `ADMIN`) can:
- Create, update, soft-delete products
- Advance order statuses (SHIPPED, DELIVERED, REFUNDED, etc.)
- View all analytics

Admin role must be assigned manually in the database or via a dedicated admin creation script. It is not assignable via the public API.

## 10. Audit Logging

Important user actions are logged to `user_activity_logs` with:
- `action`: string identifier (e.g. `USER_LOGIN`, `ORDER_CREATED`, `PAYMENT_SUCCESS`)
- `metadata`: JSONB with action-specific context (orderId, amount, etc.)
- Logs are append-only (no updates/deletes)

This provides an audit trail for security and business analysis.
