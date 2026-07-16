use queuemate;

create table queue_daily_sequences (
  venue_id bigint not null,
  queue_date date not null,
  last_no int not null,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  primary key (venue_id, queue_date),
  constraint fk_queue_daily_sequences_venue foreign key (venue_id) references venues (id),
  constraint ck_queue_daily_sequences_last_no_positive check (last_no > 0)
) engine = InnoDB default charset = utf8mb4;

insert into queue_daily_sequences (venue_id, queue_date, last_no)
select venue_id, queue_date, max(queue_no)
from queue_tickets
group by venue_id, queue_date;

alter table queue_tickets
  add column active_flag tinyint generated always as (
    case when status in ('WAITING', 'CALLED') then 1 else null end
  ) stored after missed_at,
  add unique key uk_queue_tickets_user_active (
    venue_id,
    queue_date,
    user_id,
    active_flag
  );
