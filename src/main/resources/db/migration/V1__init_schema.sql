-- V1__init_schema.sql
-- Initial schema for electronics e-commerce backend
-- Uses UUID primary keys for all entities (better for distributed systems & URL obfuscation)
-- JSONB columns for flexible attributes (specs, shippingAddress, metadata)

-- Enable required PostgreSQL extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";  -- For trigram full-text search on product names

-- ===========================
-- USERS
-- ===========================
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name   VARCHAR(100) NOT NULL,
    last_name    VARCHAR(100) NOT NULL,
    role         VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- SESSIONS (refresh tokens)
-- ===========================
-- Design choice: store refresh tokens in DB so we can revoke them server-side.
-- This is crucial for logout and security breach response.
CREATE TABLE sessions (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token VARCHAR(512) NOT NULL UNIQUE,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- CATEGORIES
-- ===========================
-- Self-referencing hierarchy: parent_id allows unlimited nesting (e.g. Electronics > Laptops > Gaming Laptops)
CREATE TABLE categories (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(150) NOT NULL UNIQUE,
    parent_id  UUID REFERENCES categories(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- PRODUCTS
-- ===========================
-- specs is JSONB: flexible per-category attributes (laptop: cpu/ram/storage, phone: battery/camera, etc.)
-- image_urls is TEXT[] - PostgreSQL native array for ordered list of image URLs
CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    brand       VARCHAR(100) NOT NULL,
    description TEXT,
    price       NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    category_id UUID NOT NULL REFERENCES categories(id),
    specs       JSONB NOT NULL DEFAULT '{}',
    image_urls  TEXT[] NOT NULL DEFAULT '{}',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- INVENTORY
-- ===========================
-- One-to-one with Product. Using productId as PK enforces the 1:1 relationship.
-- version column for optimistic locking (JPA @Version) - prevents overselling under concurrency.
CREATE TABLE inventory (
    product_id   UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    stock_qty    INTEGER NOT NULL DEFAULT 0 CHECK (stock_qty >= 0),
    reserved_qty INTEGER NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    version      BIGINT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    -- Ensures we never have more reserved than available
    CONSTRAINT chk_reserved_lte_stock CHECK (reserved_qty <= stock_qty)
);

-- ===========================
-- CARTS
-- ===========================
-- user_id nullable for future guest cart support via session_id
CREATE TABLE carts (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
    session_id VARCHAR(255),  -- For future guest cart support
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- CART ITEMS
-- ===========================
-- unit_price_snapshot: price at time of adding to cart.
-- Design choice: snapshot price at add-time so cart reflects actual price seen by user,
-- but final order uses current price or snapshot depending on business policy.
-- We use cart snapshot for display, but recalculate at checkout from live prices.
CREATE TABLE cart_items (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cart_id             UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    quantity            INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_snapshot NUMERIC(12, 2) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (cart_id, product_id)  -- One entry per product per cart
);

-- ===========================
-- ORDERS
-- ===========================
-- shipping_address is JSONB: flexible address format (street, city, country, zip, etc.)
-- idempotency_key: prevents duplicate orders from network retries
CREATE TABLE orders (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID NOT NULL REFERENCES users(id),
    status           VARCHAR(30) NOT NULL DEFAULT 'CREATED'
                         CHECK (status IN ('CREATED','PENDING_PAYMENT','PAID','SHIPPED','DELIVERED','FAILED','CANCELLED','REFUNDED')),
    shipping_address JSONB NOT NULL DEFAULT '{}',
    total_amount     NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    idempotency_key  VARCHAR(255) NOT NULL UNIQUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- ORDER ITEMS
-- ===========================
-- Snapshot product name and price at order time - products can be renamed/repriced later
CREATE TABLE order_items (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id              UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id            UUID NOT NULL REFERENCES products(id),
    product_name_snapshot VARCHAR(255) NOT NULL,
    unit_price            NUMERIC(12, 2) NOT NULL,
    quantity              INTEGER NOT NULL CHECK (quantity > 0),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- PAYMENTS
-- ===========================
-- idempotency_key: prevents duplicate payment processing from retries
-- provider_reference: external payment gateway transaction ID (nullable for simulated payments)
CREATE TABLE payments (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id           UUID NOT NULL REFERENCES orders(id),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    method             VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(255),
    idempotency_key    VARCHAR(255) NOT NULL UNIQUE,
    amount             NUMERIC(12, 2) NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- ===========================
-- REVIEWS
-- ===========================
-- Unique constraint on (user_id, product_id) enforces one review per user per product at DB level
CREATE TABLE reviews (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES users(id),
    product_id UUID NOT NULL REFERENCES products(id),
    rating     SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment    TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, product_id)
);

-- ===========================
-- USER ACTIVITY LOGS
-- ===========================
-- Audit trail for important user actions. metadata is JSONB for flexible action-specific data.
CREATE TABLE user_activity_logs (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES users(id),
    action     VARCHAR(100) NOT NULL,
    metadata   JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
