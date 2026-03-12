-- V2__indexes_and_search.sql
-- Performance indexes, full-text search, and JSONB optimizations

-- ===========================
-- PRODUCT SEARCH INDEXES
-- ===========================

-- Full-text search: tsvector column computed from name + brand + description
-- We store the vector for performance rather than computing on every query
ALTER TABLE products ADD COLUMN search_vector tsvector;

UPDATE products
SET search_vector = to_tsvector('english', coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,''));

CREATE INDEX idx_products_search_vector ON products USING GIN(search_vector);

-- Trigger to auto-update search_vector when product is modified
CREATE OR REPLACE FUNCTION update_product_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.name, '') || ' ' ||
        coalesce(NEW.brand, '') || ' ' ||
        coalesce(NEW.description, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_search_vector
BEFORE INSERT OR UPDATE OF name, brand, description
ON products
FOR EACH ROW EXECUTE FUNCTION update_product_search_vector();

-- Trigram index for fuzzy/partial name search (e.g. "macbo" matches "MacBook")
CREATE INDEX idx_products_name_trgm ON products USING GIN(name gin_trgm_ops);
CREATE INDEX idx_products_brand_trgm ON products USING GIN(brand gin_trgm_ops);

-- JSONB GIN index for flexible spec filtering (e.g. specs->>'ram_gb' >= '16')
CREATE INDEX idx_products_specs ON products USING GIN(specs);

-- Composite index for category + price range queries (most common filter combination)
CREATE INDEX idx_products_category_price ON products(category_id, price) WHERE is_active = TRUE;

-- Brand filtering
CREATE INDEX idx_products_brand ON products(brand) WHERE is_active = TRUE;

-- Active products (most queries filter is_active = true)
CREATE INDEX idx_products_active ON products(is_active, created_at DESC);

-- ===========================
-- INVENTORY
-- ===========================
-- Fast available stock lookup: availableQty = stock_qty - reserved_qty
CREATE INDEX idx_inventory_available ON inventory((stock_qty - reserved_qty));

-- ===========================
-- CARTS
-- ===========================
CREATE INDEX idx_carts_user_id ON carts(user_id);
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);

-- ===========================
-- ORDERS
-- ===========================
CREATE INDEX idx_orders_user_id ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_idempotency ON orders(idempotency_key);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- ===========================
-- PAYMENTS
-- ===========================
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);

-- ===========================
-- SESSIONS
-- ===========================
CREATE INDEX idx_sessions_user_id ON sessions(user_id);
CREATE INDEX idx_sessions_refresh_token ON sessions(refresh_token);
CREATE INDEX idx_sessions_expires_at ON sessions(expires_at) WHERE revoked = FALSE;

-- ===========================
-- REVIEWS
-- ===========================
CREATE INDEX idx_reviews_product_id ON reviews(product_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);

-- ===========================
-- CATEGORIES
-- ===========================
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_slug ON categories(slug);

-- ===========================
-- ACTIVITY LOGS
-- ===========================
CREATE INDEX idx_activity_logs_user_id ON user_activity_logs(user_id, created_at DESC);
CREATE INDEX idx_activity_logs_action ON user_activity_logs(action, created_at DESC);

-- ===========================
-- AUTO-UPDATE TIMESTAMPS
-- ===========================
-- Generic trigger function for updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply to all tables with updated_at
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_categories_updated_at BEFORE UPDATE ON categories FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_inventory_updated_at BEFORE UPDATE ON inventory FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_carts_updated_at BEFORE UPDATE ON carts FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_cart_items_updated_at BEFORE UPDATE ON cart_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON orders FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_reviews_updated_at BEFORE UPDATE ON reviews FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
