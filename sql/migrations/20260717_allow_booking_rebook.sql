use queuemate;

alter table bookings
  drop index uk_bookings_user_slot,
  add column active_slot_id bigint generated always as (
    case when status = 'BOOKED' then slot_id else null end
  ) stored after updated_at,
  add unique key uk_bookings_user_active_slot (user_id, active_slot_id);
