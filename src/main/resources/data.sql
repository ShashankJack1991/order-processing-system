-- Sample data inserted only if table is empty
INSERT INTO orders (customer_id, total_amount, status, created_at, updated_at)
SELECT 1001, 72000.00, 'PENDING', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders LIMIT 1);

INSERT INTO orders (customer_id, total_amount, status, created_at, updated_at)
SELECT 1002, 15000.00, 'PROCESSING', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE customer_id = 1002);

INSERT INTO orders (customer_id, total_amount, status, created_at, updated_at)
SELECT 1003, 3500.00, 'SHIPPED', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM orders WHERE customer_id = 1003);

INSERT INTO order_items (product_name, quantity, price, order_id)
SELECT 'Laptop', 1, 70000.00, (SELECT id FROM orders WHERE customer_id = 1001 LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM order_items LIMIT 1);

INSERT INTO order_items (product_name, quantity, price, order_id)
SELECT 'Mouse', 2, 1000.00, (SELECT id FROM orders WHERE customer_id = 1001 LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE product_name = 'Mouse');

INSERT INTO order_items (product_name, quantity, price, order_id)
SELECT 'Keyboard', 1, 15000.00, (SELECT id FROM orders WHERE customer_id = 1002 LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE product_name = 'Keyboard');

INSERT INTO order_items (product_name, quantity, price, order_id)
SELECT 'USB Hub', 1, 3500.00, (SELECT id FROM orders WHERE customer_id = 1003 LIMIT 1)
WHERE NOT EXISTS (SELECT 1 FROM order_items WHERE product_name = 'USB Hub');
