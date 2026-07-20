use queuemate;
set names utf8mb4;

create table if not exists user_roles (
  user_id bigint not null,
  role varchar(20) not null,
  granted_by bigint null,
  granted_at datetime not null default current_timestamp,
  primary key (user_id, role),
  key idx_user_roles_role (role),
  constraint fk_user_roles_user foreign key (user_id) references users (id),
  constraint fk_user_roles_granted_by foreign key (granted_by) references users (id)
) engine = InnoDB default charset = utf8mb4;

insert ignore into user_roles (user_id, role, granted_by)
select id, role, id from users;

create table if not exists merchant_applications (
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
