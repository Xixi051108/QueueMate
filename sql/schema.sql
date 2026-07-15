create database if not exists queuemate
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

use queuemate;

drop table if exists queue_tickets;
drop table if exists wallet_transactions;
drop table if exists bookings;
drop table if exists booking_slots;
drop table if exists venues;
drop table if exists wallets;
drop table if exists users;

create table users (
  id bigint primary key,
  username varchar(50) not null,
  password_hash varchar(255) not null,
  role varchar(20) not null,
  display_name varchar(100) not null,
  phone varchar(20) null,
  status varchar(20) not null default 'ACTIVE',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_users_username (username),
  key idx_users_role (role)
) engine = InnoDB default charset = utf8mb4;

create table wallets (
  id bigint primary key,
  user_id bigint not null,
  balance decimal(10,2) not null default 0.00,
  status varchar(20) not null default 'ACTIVE',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_wallets_user_id (user_id),
  constraint fk_wallets_user foreign key (user_id) references users (id),
  constraint ck_wallets_balance_non_negative check (balance >= 0)
) engine = InnoDB default charset = utf8mb4;

create table venues (
  id bigint primary key,
  name varchar(100) not null,
  category varchar(30) not null,
  description varchar(500) null,
  merchant_id bigint not null,
  address_text varchar(255) null,
  queue_enabled tinyint not null default 1,
  booking_enabled tinyint not null default 1,
  default_price decimal(10,2) not null default 0.00,
  status varchar(20) not null default 'ACTIVE',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_venues_name_merchant (name, merchant_id),
  key idx_venues_category (category),
  key idx_venues_merchant_id (merchant_id),
  constraint fk_venues_merchant foreign key (merchant_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table booking_slots (
  id bigint primary key,
  venue_id bigint not null,
  slot_date date not null,
  start_time time not null,
  end_time time not null,
  capacity int not null,
  reserved_count int not null default 0,
  price decimal(10,2) not null default 0.00,
  status varchar(20) not null default 'OPEN',
  created_by bigint not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_booking_slots_unique (venue_id, slot_date, start_time, end_time),
  key idx_booking_slots_venue_date (venue_id, slot_date),
  constraint fk_booking_slots_venue foreign key (venue_id) references venues (id),
  constraint fk_booking_slots_created_by foreign key (created_by) references users (id),
  constraint ck_booking_slots_capacity_positive check (capacity > 0),
  constraint ck_booking_slots_reserved_count check (reserved_count >= 0 and reserved_count <= capacity),
  constraint ck_booking_slots_time_range check (start_time < end_time)
) engine = InnoDB default charset = utf8mb4;

create table bookings (
  id bigint primary key,
  booking_no varchar(50) not null,
  user_id bigint not null,
  venue_id bigint not null,
  slot_id bigint not null,
  status varchar(20) not null default 'BOOKED',
  pay_status varchar(20) not null default 'UNPAID',
  paid_amount decimal(10,2) not null default 0.00,
  cancel_reason varchar(255) null,
  booked_at datetime not null default current_timestamp,
  paid_at datetime null,
  cancelled_at datetime null,
  refunded_at datetime null,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_bookings_user_slot (user_id, slot_id),
  unique key uk_bookings_no (booking_no),
  key idx_bookings_venue_id (venue_id),
  key idx_bookings_status (status),
  constraint fk_bookings_user foreign key (user_id) references users (id),
  constraint fk_bookings_venue foreign key (venue_id) references venues (id),
  constraint fk_bookings_slot foreign key (slot_id) references booking_slots (id)
) engine = InnoDB default charset = utf8mb4;

create table wallet_transactions (
  id bigint primary key,
  transaction_no varchar(50) not null,
  wallet_id bigint not null,
  user_id bigint not null,
  type varchar(20) not null,
  amount decimal(10,2) not null,
  balance_before decimal(10,2) not null,
  balance_after decimal(10,2) not null,
  biz_type varchar(30) null,
  biz_no varchar(50) null,
  status varchar(20) not null,
  remark varchar(255) null,
  created_at datetime not null default current_timestamp,
  unique key uk_wallet_transactions_no (transaction_no),
  key idx_wallet_transactions_user_id (user_id),
  key idx_wallet_transactions_biz (biz_type, biz_no),
  constraint fk_wallet_transactions_wallet foreign key (wallet_id) references wallets (id),
  constraint fk_wallet_transactions_user foreign key (user_id) references users (id),
  constraint ck_wallet_transactions_amount_positive check (amount > 0)
) engine = InnoDB default charset = utf8mb4;

create table queue_tickets (
  id bigint primary key,
  ticket_no varchar(50) not null,
  venue_id bigint not null,
  user_id bigint null,
  queue_date date not null,
  queue_no int not null,
  status varchar(20) not null default 'WAITING',
  taken_at datetime not null default current_timestamp,
  called_at datetime null,
  completed_at datetime null,
  missed_at datetime null,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_queue_tickets_no (ticket_no),
  unique key uk_queue_tickets_daily_no (venue_id, queue_date, queue_no),
  key idx_queue_tickets_venue_status (venue_id, status),
  constraint fk_queue_tickets_venue foreign key (venue_id) references venues (id),
  constraint fk_queue_tickets_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;

