-- Add-only housekeeping sample seed; apply in HeidiSQL after all migrations (including v4_15).
-- Adds 4 room types, 24 rooms (20 dirty + 4 clean), 20 pending housekeeping tasks.
-- Safe to rerun: existing rooms, bookings, tasks, and task progress are left untouched.
-- The synthetic guest has no identity_card value, to satisfy the identity-vault privacy constraint.

START TRANSACTION;

INSERT IGNORE INTO roles (name, description)
VALUES ('housekeeping', 'Nhân viên buồng phòng');

INSERT IGNORE INTO room_types
    (name, description, base_price, capacity, amenities, display_order, is_visible)
VALUES
    ('Superior', 'Phòng superior tiện nghi cho 2 khách', 750000, 2, 'Wifi, TV, Air Conditioning, Hot Water', 4, 1),
    ('Executive', 'Phòng executive rộng rãi với khu vực làm việc', 1200000, 2, 'Wifi, TV, Air Conditioning, Hot Water, Work Desk', 5, 1),
    ('Suite', 'Phòng suite có khu vực tiếp khách riêng', 2000000, 3, 'Wifi, TV, Air Conditioning, Hot Water, Living Area, Mini Bar', 6, 1),
    ('Family Plus', 'Phòng gia đình dành cho tối đa 4 khách', 1800000, 4, 'Wifi, TV, Air Conditioning, Hot Water, Extra Bed', 7, 1);

INSERT IGNORE INTO rooms (room_number, room_type_id, price, status)
SELECT demo.room_number, room_type.id, demo.price, demo.status
FROM (
    SELECT '401' AS room_number, 'Superior' AS type_name, 750000 AS price, 'vacant_dirty' AS status UNION ALL
    SELECT '402', 'Superior', 750000, 'vacant_dirty' UNION ALL
    SELECT '403', 'Superior', 750000, 'vacant_dirty' UNION ALL
    SELECT '404', 'Superior', 750000, 'vacant_dirty' UNION ALL
    SELECT '405', 'Superior', 750000, 'vacant_dirty' UNION ALL
    SELECT '406', 'Superior', 750000, 'vacant_dirty' UNION ALL
    SELECT '407', 'Executive', 1200000, 'vacant_dirty' UNION ALL
    SELECT '408', 'Executive', 1200000, 'vacant_dirty' UNION ALL
    SELECT '409', 'Executive', 1200000, 'vacant_dirty' UNION ALL
    SELECT '410', 'Executive', 1200000, 'vacant_dirty' UNION ALL
    SELECT '411', 'Executive', 1200000, 'vacant_dirty' UNION ALL
    SELECT '412', 'Executive', 1200000, 'vacant_dirty' UNION ALL
    SELECT '413', 'Suite', 2000000, 'vacant_dirty' UNION ALL
    SELECT '414', 'Suite', 2000000, 'vacant_dirty' UNION ALL
    SELECT '415', 'Suite', 2000000, 'vacant_dirty' UNION ALL
    SELECT '416', 'Suite', 2000000, 'vacant_dirty' UNION ALL
    SELECT '417', 'Suite', 2000000, 'vacant_dirty' UNION ALL
    SELECT '418', 'Suite', 2000000, 'vacant_dirty' UNION ALL
    SELECT '419', 'Family Plus', 1800000, 'vacant_dirty' UNION ALL
    SELECT '420', 'Family Plus', 1800000, 'vacant_dirty' UNION ALL
    SELECT '421', 'Family Plus', 1800000, 'vacant_clean' UNION ALL
    SELECT '422', 'Family Plus', 1800000, 'vacant_clean' UNION ALL
    SELECT '423', 'Family Plus', 1800000, 'vacant_clean' UNION ALL
    SELECT '424', 'Family Plus', 1800000, 'vacant_clean'
) AS demo
JOIN room_types AS room_type ON room_type.name = demo.type_name;

-- One synthetic, already-checked-out stay per demo room provides the required booking FK.
INSERT INTO customers
    (full_name, identity_card, phone, email, address)
SELECT 'Khách demo housekeeping', NULL, '0000000000',
       'demo-housekeeping@example.invalid', 'Dữ liệu mẫu'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM customers WHERE email = 'demo-housekeeping@example.invalid'
);

INSERT INTO bookings
    (customer_id, room_id, check_in_date, check_out_date, total_price, status, customer_type)
SELECT guest.id, room.id,
       DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY),
       DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR),
       room.price, 'checked_out', 'Corporate'
FROM rooms AS room
JOIN customers AS guest ON guest.email = 'demo-housekeeping@example.invalid'
WHERE room.room_number BETWEEN '401' AND '420'
  AND NOT EXISTS (
      SELECT 1
      FROM bookings AS existing
      WHERE existing.room_id = room.id
        AND existing.customer_id = guest.id
        AND existing.status = 'checked_out'
        AND existing.customer_type = 'Corporate'
  );

INSERT INTO cleaning_tasks
    (booking_id, room_id, rental_type, estimated_duration_minutes, status, due_at, expected_finish_at)
SELECT booking.id, booking.room_id, booking.rental_type,
       CASE MOD(CAST(room.room_number AS UNSIGNED) - 401, 4)
           WHEN 0 THEN 25 WHEN 1 THEN 35 WHEN 2 THEN 45 ELSE 55
       END,
       'pending',
       TIMESTAMPADD(HOUR, 2, CURRENT_TIMESTAMP(6)),
       TIMESTAMPADD(
           MINUTE,
           CASE MOD(CAST(room.room_number AS UNSIGNED) - 401, 4)
               WHEN 0 THEN 25 WHEN 1 THEN 35 WHEN 2 THEN 45 ELSE 55
           END,
           CURRENT_TIMESTAMP(6)
       )
FROM bookings AS booking
JOIN rooms AS room ON room.id = booking.room_id
JOIN customers AS guest ON guest.id = booking.customer_id
WHERE guest.email = 'demo-housekeeping@example.invalid'
  AND room.room_number BETWEEN '401' AND '420'
  AND NOT EXISTS (
      SELECT 1 FROM cleaning_tasks AS existing WHERE existing.booking_id = booking.id
  );

INSERT IGNORE INTO housekeeping_checklist_items
    (task_id, item_key, item_label, is_required)
SELECT task.id, item.item_key, item.item_label, 1
FROM cleaning_tasks AS task
JOIN rooms AS room ON room.id = task.room_id
CROSS JOIN (
    SELECT 'linens' AS item_key, 'Thay ga giường và vỏ gối' AS item_label UNION ALL
    SELECT 'bathroom', 'Vệ sinh phòng tắm' UNION ALL
    SELECT 'towels', 'Bổ sung khăn và vật dụng' UNION ALL
    SELECT 'trash', 'Đổ rác và thay túi rác' UNION ALL
    SELECT 'equipment', 'Kiểm tra thiết bị và đèn' UNION ALL
    SELECT 'final_check', 'Kiểm tra tổng thể trước bàn giao'
) AS item
WHERE room.room_number BETWEEN '401' AND '420'
  AND task.status IN ('pending', 'in_progress');

INSERT INTO housekeeping_task_events
    (task_id, actor_user_id, event_type, details, created_at)
SELECT task.id, NULL, 'task_created', 'Tạo tác vụ demo dọn phòng', task.created_at
FROM cleaning_tasks AS task
JOIN rooms AS room ON room.id = task.room_id
WHERE room.room_number BETWEEN '401' AND '420'
  AND NOT EXISTS (
      SELECT 1
      FROM housekeeping_task_events AS existing
      WHERE existing.task_id = task.id AND existing.event_type = 'task_created'
  );

COMMIT;
