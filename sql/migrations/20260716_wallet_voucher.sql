use queuemate;

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

alter table wallet_transactions
  drop index idx_wallet_transactions_biz,
  add unique key uk_wallet_transactions_biz_type (biz_type, biz_no, type);

insert into booking_vouchers (
  id,
  booking_id,
  user_id,
  venue_id,
  consumption_code,
  amount,
  status,
  valid_from,
  valid_until
)
select
  8000 + b.id - 6000,
  b.id,
  b.user_id,
  b.venue_id,
  concat('QMDEMO', b.slot_id),
  b.paid_amount,
  'AVAILABLE',
  timestamp(bs.slot_date, bs.start_time) - interval 30 minute,
  timestamp(bs.slot_date, bs.end_time)
from bookings b
join booking_slots bs on bs.id = b.slot_id
where b.pay_status = 'PAID'
  and not exists (
    select 1
    from booking_vouchers bv
    where bv.booking_id = b.id
  );
