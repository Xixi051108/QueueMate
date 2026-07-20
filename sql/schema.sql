create database if not exists queuemate
  default character set utf8mb4
  default collate utf8mb4_unicode_ci;

use queuemate;

drop table if exists queue_tickets;
drop table if exists queue_daily_sequences;
drop table if exists wallet_transactions;
drop table if exists booking_vouchers;
drop table if exists bookings;
drop table if exists booking_slots;
drop table if exists venues;
drop table if exists wallets;
drop table if exists merchant_applications;
drop table if exists user_roles;
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

create table user_roles (
  user_id bigint not null,
  role varchar(20) not null,
  granted_by bigint null,
  granted_at datetime not null default current_timestamp,
  primary key (user_id, role),
  key idx_user_roles_role (role),
  constraint fk_user_roles_user foreign key (user_id) references users (id),
  constraint fk_user_roles_granted_by foreign key (granted_by) references users (id)
) engine = InnoDB default charset = utf8mb4;

create table merchant_applications (
  id bigint primary key,
  applicant_id bigint not null,
  business_name varchar(100) not null,
  contact_name varchar(100) not null,
  contact_phone varchar(20) not null,
  venue_name varchar(100) not null,
  venue_category varchar(30) not null,
  address_text varchar(255) not null,
  description varchar(500) null,
  status varchar(20) not null default 'PENDING',
  review_note varchar(500) null,
  reviewer_id bigint null,
  submitted_at datetime not null default current_timestamp,
  reviewed_at datetime null,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  pending_applicant_id bigint generated always as (
    case when status = 'PENDING' then applicant_id else null end
  ) stored,
  unique key uk_merchant_applications_pending (pending_applicant_id),
  key idx_merchant_applications_applicant (applicant_id, submitted_at),
  key idx_merchant_applications_status (status, submitted_at),
  constraint fk_merchant_applications_applicant foreign key (applicant_id) references users (id),
  constraint fk_merchant_applications_reviewer foreign key (reviewer_id) references users (id)
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
  active_slot_id bigint generated always as (
    case when status = 'BOOKED' then slot_id else null end
  ) stored,
  unique key uk_bookings_user_active_slot (user_id, active_slot_id),
  unique key uk_bookings_no (booking_no),
  key idx_bookings_venue_id (venue_id),
  key idx_bookings_status (status),
  constraint fk_bookings_user foreign key (user_id) references users (id),
  constraint fk_bookings_venue foreign key (venue_id) references venues (id),
  constraint fk_bookings_slot foreign key (slot_id) references booking_slots (id)
) engine = InnoDB default charset = utf8mb4;

create table booking_vouchers (
  id bigint primary key,
  booking_id bigint not null,
  user_id bigint not null,
  venue_id bigint not null,
  consumption_code varchar(32) not null,
  amount decimal(10,2) not null,
  status varchar(20) not null default 'AVAILABLE',
  valid_from datetime not null,
  valid_until datetime not null,
  redeemed_by bigint null,
  redeemed_at datetime null,
  voided_at datetime null,
  expired_at datetime null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_booking_vouchers_booking_id (booking_id),
  unique key uk_booking_vouchers_consumption_code (consumption_code),
  key idx_booking_vouchers_venue_status (venue_id, status),
  constraint fk_booking_vouchers_booking foreign key (booking_id) references bookings (id),
  constraint fk_booking_vouchers_user foreign key (user_id) references users (id),
  constraint fk_booking_vouchers_venue foreign key (venue_id) references venues (id),
  constraint fk_booking_vouchers_redeemed_by foreign key (redeemed_by) references users (id),
  constraint ck_booking_vouchers_amount_positive check (amount > 0),
  constraint ck_booking_vouchers_time_range check (valid_from < valid_until)
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
  unique key uk_wallet_transactions_biz_type (biz_type, biz_no, type),
  key idx_wallet_transactions_user_id (user_id),
  constraint fk_wallet_transactions_wallet foreign key (wallet_id) references wallets (id),
  constraint fk_wallet_transactions_user foreign key (user_id) references users (id),
  constraint ck_wallet_transactions_amount_positive check (amount > 0)
) engine = InnoDB default charset = utf8mb4;

create table queue_daily_sequences (
  venue_id bigint not null,
  queue_date date not null,
  last_no int not null,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  primary key (venue_id, queue_date),
  constraint fk_queue_daily_sequences_venue foreign key (venue_id) references venues (id),
  constraint ck_queue_daily_sequences_last_no_positive check (last_no > 0)
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
  active_flag tinyint generated always as (
    case when status in ('WAITING', 'CALLED') then 1 else null end
  ) stored,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_queue_tickets_no (ticket_no),
  unique key uk_queue_tickets_daily_no (venue_id, queue_date, queue_no),
  unique key uk_queue_tickets_user_active (venue_id, queue_date, user_id, active_flag),
  key idx_queue_tickets_venue_status (venue_id, status),
  constraint fk_queue_tickets_venue foreign key (venue_id) references venues (id),
  constraint fk_queue_tickets_user foreign key (user_id) references users (id)
) engine = InnoDB default charset = utf8mb4;
