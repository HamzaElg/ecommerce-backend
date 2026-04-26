-- ========================
-- Categories
-- ========================
INSERT INTO categories (id, name, slug, parent_id, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'Smartphones', 'smartphones', NULL, NOW(), NOW()),
  (gen_random_uuid(), 'Laptops', 'laptops', NULL, NOW(), NOW()),
  (gen_random_uuid(), 'Audio', 'audio', NULL, NOW(), NOW()),
  (gen_random_uuid(), 'Gaming', 'gaming', NULL, NOW(), NOW())
ON CONFLICT DO NOTHING;

-- ========================
-- Products + Inventory
-- ========================
WITH inserted_products AS (
    INSERT INTO products (
        id, name, brand, description, price, category_id,
        specs, image_urls, is_active, created_at, updated_at
    )
    VALUES
    (
        gen_random_uuid(),
        'iPhone 15 Pro',
        'Apple',
        'Latest Apple smartphone',
        1299.99,
        (SELECT id FROM categories WHERE slug = 'smartphones' LIMIT 1),
        '{"ram_gb": 8, "storage_gb": 256, "color": "Titanium"}'::jsonb,
        ARRAY['https://example.com/iphone.jpg'],
        true,
        NOW(),
        NOW()
    ),
    (
        gen_random_uuid(),
        'Samsung Galaxy S24',
        'Samsung',
        'Flagship Android smartphone',
        899.99,
        (SELECT id FROM categories WHERE slug = 'smartphones' LIMIT 1),
        '{"ram_gb": 8, "storage_gb": 256, "color": "Black"}'::jsonb,
        ARRAY['https://example.com/s24.jpg'],
        true,
        NOW(),
        NOW()
    ),
    (
        gen_random_uuid(),
        'MacBook Pro 14',
        'Apple',
        'Powerful laptop for developers',
        1999.99,
        (SELECT id FROM categories WHERE slug = 'laptops' LIMIT 1),
        '{"ram_gb": 16, "storage_gb": 512, "cpu": "M3 Pro"}'::jsonb,
        ARRAY['https://example.com/macbook.jpg'],
        true,
        NOW(),
        NOW()
    ),
    (
        gen_random_uuid(),
        'Sony WH-1000XM5',
        'Sony',
        'Noise cancelling headphones',
        348.00,
        (SELECT id FROM categories WHERE slug = 'audio' LIMIT 1),
        '{"battery_hours": 30, "noise_cancelling": true}'::jsonb,
        ARRAY['https://example.com/sony.jpg'],
        true,
        NOW(),
        NOW()
    ),
    (
        gen_random_uuid(),
        'PlayStation 5',
        'Sony',
        'Next-gen gaming console',
        499.99,
        (SELECT id FROM categories WHERE slug = 'gaming' LIMIT 1),
        '{"storage_gb": 825, "resolution": "4K"}'::jsonb,
        ARRAY['https://example.com/ps5.jpg'],
        true,
        NOW(),
        NOW()
    )
    RETURNING id
)
INSERT INTO inventory (
    product_id, stock_qty, reserved_qty, version, created_at, updated_at
)
SELECT id, 20, 0, 0, NOW(), NOW()
FROM inserted_products;