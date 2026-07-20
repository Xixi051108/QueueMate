use queuemate;

insert into users (id, username, password_hash, role, display_name, phone, status) values
(1001, 'admin', '{bcrypt}$2a$10$NbUYcsZe4AgAJMgGY2.BfOYU38nRNmsiWEmWptKSrvECCIhXh89ZW', 'ADMIN', '平台管理员', '13800000000', 'ACTIVE'),
(2001, 'merchant_tea', '{bcrypt}$2a$10$UOg5BWFnaBa0qbpafVexi.wQcrdRofZwgPejJbErqrO5wDCEQ9QkG', 'MERCHANT', '奶茶店商家', '13800002001', 'ACTIVE'),
(2002, 'merchant_sport', '{bcrypt}$2a$10$UOg5BWFnaBa0qbpafVexi.wQcrdRofZwgPejJbErqrO5wDCEQ9QkG', 'MERCHANT', '运动场商家', '13800002002', 'ACTIVE'),
(3001, 'alice', '{bcrypt}$2a$10$UeeDl4Q10kiVbaMczFrvLeGz4XilMsAdlh2kwND8pQv043HM2l0a.', 'USER', 'Alice', '13800003001', 'ACTIVE'),
(3002, 'bob', '{bcrypt}$2a$10$UeeDl4Q10kiVbaMczFrvLeGz4XilMsAdlh2kwND8pQv043HM2l0a.', 'USER', 'Bob', '13800003002', 'ACTIVE'),
(3003, 'carol', '{bcrypt}$2a$10$UeeDl4Q10kiVbaMczFrvLeGz4XilMsAdlh2kwND8pQv043HM2l0a.', 'USER', 'Carol', '13800003003', 'ACTIVE');

insert into wallets (id, user_id, balance, status) values
(9001, 3001, 200.00, 'ACTIVE'),
(9002, 3002, 80.00, 'ACTIVE'),
(9003, 3003, 0.00, 'ACTIVE');

insert into venues (id, name, category, description, merchant_id, address_text, queue_enabled, booking_enabled, default_price, status) values
(4001, 'QueueMate 奶茶店 A', 'TEA_SHOP', '模拟奶茶店，支持现场取号', 2001, '模拟商业街 1 号', 1, 0, 0.00, 'ACTIVE'),
(4002, 'QueueMate 自习室 A', 'STUDY_ROOM', '安静自习卡座，支持收费预约', 2001, '模拟学习中心 2 楼', 0, 1, 10.00, 'ACTIVE'),
(4003, 'QueueMate 羽毛球场 A', 'BADMINTON_COURT', '晚间热门场地，支持收费预约', 2002, '模拟体育馆 3 号场', 0, 1, 30.00, 'ACTIVE'),
(4004, '茶屿·滨江店', 'TEA_SHOP', '临江休憩型奶茶店，主打鲜果茶和低糖茶饮，午后及周末客流较集中。', 2001, '滨江新区云帆路 88 号星河广场 1 层', 1, 0, 0.00, 'ACTIVE'),
(4005, '茶屿·大学城店', 'TEA_SHOP', '面向学生客群的快捷取餐门店，提供现场排队进度，晚课结束后通常较繁忙。', 2001, '大学城知行路 16 号青春里 B 区', 1, 0, 0.00, 'ACTIVE'),
(4006, '茶屿·科创园店', 'TEA_SHOP', '园区办公人群服务点，当前处于装修升级状态，用于演示停用地点。', 2001, '科创园启航路 66 号创新中心 A 座', 1, 0, 0.00, 'INACTIVE'),
(4007, '静界自习室·滨江店', 'STUDY_ROOM', '配备静音单人位、护眼灯和电源插座，适合长时间备考与远程学习。', 2001, '滨江新区听潮路 28 号云谷中心 3 层', 0, 1, 12.00, 'ACTIVE'),
(4008, '晨光自习室·大学城店', 'STUDY_ROOM', '自然采光充足，设有开放座位和安静专区，支持半日学习时段预约。', 2001, '大学城书香街 9 号学术交流中心 2 层', 0, 1, 15.00, 'ACTIVE'),
(4009, '深夜书桌·科创园店', 'STUDY_ROOM', '提供晚间学习时段、饮水区和储物柜，适合下班后的集中学习。', 2001, '科创园星火路 21 号创客公寓裙楼 2 层', 0, 1, 16.00, 'ACTIVE'),
(4010, '飞羽羽毛球馆·奥体店', 'BADMINTON_COURT', '专业塑胶场地与分区照明，提供晚间热门时段预约及现场候场取号。', 2002, '奥体新区竞速路 10 号全民运动中心 1 层', 1, 1, 40.00, 'ACTIVE'),
(4011, '跃动羽毛球馆·湖滨店', 'BADMINTON_COURT', '四片标准场地，配有休息区和淋浴间，适合朋友约球和社群活动。', 2002, '湖滨新区逐风路 35 号运动汇 2 层', 0, 1, 32.00, 'ACTIVE'),
(4012, '邻里羽毛球馆·社区店', 'BADMINTON_COURT', '社区型小场馆，价格亲民，提供工作日晨练和下午错峰时段。', 2002, '悦邻社区和悦路 6 号文体中心', 1, 1, 22.00, 'ACTIVE');

insert into booking_slots (id, venue_id, slot_date, start_time, end_time, capacity, reserved_count, price, status, created_by) values
(5001, 4002, '2026-07-20', '09:00:00', '10:00:00', 20, 1, 10.00, 'OPEN', 2001),
(5002, 4002, '2026-07-20', '10:00:00', '11:00:00', 20, 0, 10.00, 'OPEN', 2001),
(5003, 4003, '2026-07-20', '19:00:00', '20:00:00', 4, 1, 30.00, 'OPEN', 2002),
(5004, 4003, '2026-07-20', '20:00:00', '21:00:00', 4, 0, 30.00, 'OPEN', 2002),
(5005, 4007, '2026-07-21', '08:00:00', '10:00:00', 36, 0, 12.00, 'OPEN', 2001),
(5006, 4007, '2026-07-21', '19:00:00', '22:00:00', 36, 0, 15.00, 'OPEN', 2001),
(5007, 4008, '2026-07-21', '09:00:00', '12:00:00', 24, 0, 15.00, 'OPEN', 2001),
(5008, 4008, '2026-07-22', '14:00:00', '18:00:00', 24, 0, 18.00, 'OPEN', 2001),
(5009, 4009, '2026-07-21', '18:00:00', '22:00:00', 18, 0, 20.00, 'OPEN', 2001),
(5010, 4009, '2026-07-22', '08:00:00', '12:00:00', 18, 0, 16.00, 'OPEN', 2001),
(5011, 4010, '2026-07-21', '18:00:00', '19:00:00', 6, 0, 40.00, 'OPEN', 2002),
(5012, 4010, '2026-07-21', '19:00:00', '20:00:00', 6, 0, 45.00, 'OPEN', 2002),
(5013, 4011, '2026-07-21', '10:00:00', '11:00:00', 4, 0, 32.00, 'OPEN', 2002),
(5014, 4011, '2026-07-22', '20:00:00', '21:00:00', 4, 0, 38.00, 'OPEN', 2002),
(5015, 4012, '2026-07-21', '14:00:00', '15:00:00', 3, 0, 25.00, 'OPEN', 2002),
(5016, 4012, '2026-07-22', '09:00:00', '10:00:00', 3, 0, 22.00, 'OPEN', 2002);

insert into bookings (id, booking_no, user_id, venue_id, slot_id, status, pay_status, paid_amount, booked_at, paid_at) values
(6001, 'BK202607200001', 3001, 4002, 5001, 'BOOKED', 'PAID', 10.00, '2026-07-15 10:00:00', '2026-07-15 10:00:00'),
(6002, 'BK202607200002', 3002, 4003, 5003, 'BOOKED', 'PAID', 30.00, '2026-07-15 10:05:00', '2026-07-15 10:05:00');

insert into booking_vouchers (id, booking_id, user_id, venue_id, consumption_code, amount, status, valid_from, valid_until) values
(8001, 6001, 3001, 4002, 'QMDEMO5001', 10.00, 'AVAILABLE', '2026-07-20 08:30:00', '2026-07-20 10:00:00'),
(8002, 6002, 3002, 4003, 'QMDEMO5003', 30.00, 'AVAILABLE', '2026-07-20 18:30:00', '2026-07-20 20:00:00');

insert into wallet_transactions (id, transaction_no, wallet_id, user_id, type, amount, balance_before, balance_after, biz_type, biz_no, status, remark, created_at) values
(9101, 'WT202607150001', 9001, 3001, 'RECHARGE', 210.00, 0.00, 210.00, 'WALLET', null, 'SUCCESS', 'initial mock balance', '2026-07-15 09:50:00'),
(9102, 'WT202607150002', 9001, 3001, 'PAYMENT', 10.00, 210.00, 200.00, 'BOOKING', 'BK202607200001', 'SUCCESS', 'booking payment', '2026-07-15 10:00:00'),
(9103, 'WT202607150003', 9002, 3002, 'RECHARGE', 110.00, 0.00, 110.00, 'WALLET', null, 'SUCCESS', 'initial mock balance', '2026-07-15 09:55:00'),
(9104, 'WT202607150004', 9002, 3002, 'PAYMENT', 30.00, 110.00, 80.00, 'BOOKING', 'BK202607200002', 'SUCCESS', 'booking payment', '2026-07-15 10:05:00');

insert into queue_tickets (id, ticket_no, venue_id, user_id, queue_date, queue_no, status, taken_at, called_at) values
(7001, 'QT202607150001', 4001, 3001, '2026-07-15', 1, 'CALLED', '2026-07-15 11:00:00', '2026-07-15 11:05:00'),
(7002, 'QT202607150002', 4001, 3002, '2026-07-15', 2, 'WAITING', '2026-07-15 11:01:00', null);

insert into queue_daily_sequences (venue_id, queue_date, last_no) values
(4001, '2026-07-15', 2);
