use queuemate;

insert into users (id, username, password_hash, role, display_name, phone, status) values
(1001, 'admin', '{noop}Admin123456', 'ADMIN', '平台管理员', '13800000000', 'ACTIVE'),
(2001, 'merchant_tea', '{noop}Merchant123456', 'MERCHANT', '奶茶店商家', '13800002001', 'ACTIVE'),
(2002, 'merchant_sport', '{noop}Merchant123456', 'MERCHANT', '运动场商家', '13800002002', 'ACTIVE'),
(3001, 'alice', '{noop}User123456', 'USER', 'Alice', '13800003001', 'ACTIVE'),
(3002, 'bob', '{noop}User123456', 'USER', 'Bob', '13800003002', 'ACTIVE'),
(3003, 'carol', '{noop}User123456', 'USER', 'Carol', '13800003003', 'ACTIVE');

insert into wallets (id, user_id, balance, status) values
(9001, 3001, 200.00, 'ACTIVE'),
(9002, 3002, 80.00, 'ACTIVE'),
(9003, 3003, 0.00, 'ACTIVE');

insert into venues (id, name, category, description, merchant_id, address_text, queue_enabled, booking_enabled, default_price, status) values
(4001, 'QueueMate 奶茶店 A', 'TEA_SHOP', '模拟奶茶店，支持现场取号', 2001, '模拟商业街 1 号', 1, 0, 0.00, 'ACTIVE'),
(4002, 'QueueMate 自习室 A', 'STUDY_ROOM', '安静自习卡座，支持收费预约', 2001, '模拟学习中心 2 楼', 0, 1, 10.00, 'ACTIVE'),
(4003, 'QueueMate 羽毛球场 A', 'BADMINTON_COURT', '晚间热门场地，支持收费预约', 2002, '模拟体育馆 3 号场', 0, 1, 30.00, 'ACTIVE');

insert into booking_slots (id, venue_id, slot_date, start_time, end_time, capacity, reserved_count, price, status, created_by) values
(5001, 4002, '2026-07-20', '09:00:00', '10:00:00', 20, 1, 10.00, 'OPEN', 2001),
(5002, 4002, '2026-07-20', '10:00:00', '11:00:00', 20, 0, 10.00, 'OPEN', 2001),
(5003, 4003, '2026-07-20', '19:00:00', '20:00:00', 4, 1, 30.00, 'OPEN', 2002),
(5004, 4003, '2026-07-20', '20:00:00', '21:00:00', 4, 0, 30.00, 'OPEN', 2002);

insert into bookings (id, booking_no, user_id, venue_id, slot_id, status, pay_status, paid_amount, booked_at, paid_at) values
(6001, 'BK202607200001', 3001, 4002, 5001, 'BOOKED', 'PAID', 10.00, '2026-07-15 10:00:00', '2026-07-15 10:00:00'),
(6002, 'BK202607200002', 3002, 4003, 5003, 'BOOKED', 'PAID', 30.00, '2026-07-15 10:05:00', '2026-07-15 10:05:00');

insert into wallet_transactions (id, transaction_no, wallet_id, user_id, type, amount, balance_before, balance_after, biz_type, biz_no, status, remark, created_at) values
(9101, 'WT202607150001', 9001, 3001, 'RECHARGE', 210.00, 0.00, 210.00, 'WALLET', null, 'SUCCESS', 'initial mock balance', '2026-07-15 09:50:00'),
(9102, 'WT202607150002', 9001, 3001, 'PAYMENT', 10.00, 210.00, 200.00, 'BOOKING', 'BK202607200001', 'SUCCESS', 'booking payment', '2026-07-15 10:00:00'),
(9103, 'WT202607150003', 9002, 3002, 'RECHARGE', 110.00, 0.00, 110.00, 'WALLET', null, 'SUCCESS', 'initial mock balance', '2026-07-15 09:55:00'),
(9104, 'WT202607150004', 9002, 3002, 'PAYMENT', 30.00, 110.00, 80.00, 'BOOKING', 'BK202607200002', 'SUCCESS', 'booking payment', '2026-07-15 10:05:00');

insert into queue_tickets (id, ticket_no, venue_id, user_id, queue_date, queue_no, status, taken_at, called_at) values
(7001, 'QT202607150001', 4001, 3001, '2026-07-15', 1, 'CALLED', '2026-07-15 11:00:00', '2026-07-15 11:05:00'),
(7002, 'QT202607150002', 4001, 3002, '2026-07-15', 2, 'WAITING', '2026-07-15 11:01:00', null);

