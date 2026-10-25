-- Schéma initial SQL Server, généré depuis les entités JPA (Hibernate schema-generation, dialecte SQLServerDialect).
-- Les migrations PostgreSQL historiques (db/migrations) ne sont pas traduites : ce baseline les remplace.

create table audit_logs (
    timestamp datetime2(6) not null,
    entity_id uniqueidentifier not null,
    id uniqueidentifier not null,
    pharmacy_id uniqueidentifier,
    user_id uniqueidentifier,
    status varchar(20),
    ip_address varchar(45),
    entity_type varchar(50) not null,
    action varchar(100) not null,
    user_agent varchar(255),
    username varchar(255),
    details varchar(max),
    primary key (id)
);

create table deliveries (
    current_latitude numeric(10,8),
    current_longitude numeric(11,8),
    assigned_at datetime2(6) not null,
    created_at datetime2(6) not null,
    delivered_at datetime2(6),
    picked_up_at datetime2(6),
    updated_at datetime2(6) not null,
    delivery_person_id uniqueidentifier not null,
    id uniqueidentifier not null,
    order_id uniqueidentifier not null,
    status varchar(20) not null check (status in ('ASSIGNED','PICKED_UP','IN_TRANSIT','DELIVERED','FAILED')),
    photo_proof_url varchar(500),
    primary key (id)
);

create table employee_permissions (
    can_assign_deliveries bit not null,
    can_manage_inventory bit not null,
    can_prepare_orders bit not null,
    can_process_payments bit not null,
    can_view_customer_info bit not null,
    can_view_statistics bit not null,
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    employee_id uniqueidentifier not null,
    id uniqueidentifier not null,
    last_modified_by uniqueidentifier,
    primary key (id)
);

create table medications (
    requires_prescription bit not null,
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    id uniqueidentifier not null,
    therapeutic_class varchar(50) not null check (therapeutic_class in ('ANTALGIQUE','ANTIBIOTIQUE','ANTIPALUDEEN','ANTIHYPERTENSEUR','ANTIINFLAMMATOIRE','ANTIDIABETIQUE','ANTIHISTAMINIQUE','ANTIPYRETIQUE','VITAMINE','AUTRE')),
    dosage varchar(100),
    generic_name varchar(200),
    name varchar(200) not null,
    notice_pdf_url varchar(500),
    photo_url varchar(500),
    description varchar(max),
    symptoms varchar(max),
    primary key (id)
);

create table notifications (
    is_read bit not null,
    created_at datetime2(6) not null,
    read_at datetime2(6),
    id uniqueidentifier not null,
    user_id uniqueidentifier not null,
    type varchar(20) not null check (type in ('ORDER','PAYMENT','DELIVERY','SYSTEM')),
    title varchar(200) not null,
    message varchar(max) not null,
    primary key (id)
);

create table order_items (
    quantity int not null,
    subtotal numeric(10,2) not null,
    unit_price numeric(10,2) not null,
    created_at datetime2(6) not null,
    id uniqueidentifier not null,
    medication_id uniqueidentifier not null,
    order_id uniqueidentifier not null,
    primary key (id)
);

create table orders (
    delivery_fee numeric(10,2) not null,
    delivery_latitude numeric(10,8),
    delivery_longitude numeric(11,8),
    total_amount numeric(10,2) not null,
    confirmed_at datetime2(6),
    created_at datetime2(6) not null,
    delivered_at datetime2(6),
    updated_at datetime2(6) not null,
    id uniqueidentifier not null,
    patient_id uniqueidentifier not null,
    pharmacy_id uniqueidentifier not null,
    delivery_phone varchar(20) not null,
    order_number varchar(20) not null,
    status varchar(20) not null check (status in ('PENDING','PAID','CONFIRMED','PREPARING','READY','IN_DELIVERY','DELIVERED','CANCELLED')),
    delivery_city varchar(100) not null,
    delivery_address varchar(max) not null,
    notes varchar(max),
    primary key (id)
);

create table payments (
    amount numeric(10,2) not null,
    created_at datetime2(6) not null,
    paid_at datetime2(6),
    updated_at datetime2(6) not null,
    id uniqueidentifier not null,
    order_id uniqueidentifier not null,
    payment_method varchar(20) not null check (payment_method in ('MTN_MOMO','ORANGE_MONEY','CASH')),
    phone_number varchar(20),
    status varchar(20) not null check (status in ('PENDING','SUCCESS','FAILED','REFUNDED')),
    transaction_id varchar(100),
    receipt_url varchar(500),
    primary key (id)
);

create table payouts (
    amount numeric(38,2) not null,
    processed_at datetime2(6) not null,
    id uniqueidentifier not null,
    pharmacy_id uniqueidentifier not null,
    method varchar(255) not null check (method in ('MTN_MOMO','ORANGE_MONEY','CASH')),
    notes varchar(255),
    transaction_reference varchar(255) not null,
    primary key (id)
);

create table pharmacies (
    average_rating float(53),
    latitude numeric(10,8) not null,
    longitude numeric(11,8) not null,
    rating_count int,
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    validated_at datetime2(6),
    id uniqueidentifier not null,
    user_id uniqueidentifier not null,
    phone varchar(20) not null,
    status varchar(20) not null check (status in ('PENDING','APPROVED','REJECTED','SUSPENDED')),
    license_number varchar(50) not null,
    city varchar(100) not null,
    name varchar(200) not null,
    license_document_url varchar(500) not null,
    opening_hours varchar(255),
    address varchar(max) not null,
    description varchar(max),
    primary key (id)
);

create table pharmacy_medications (
    expiry_date date,
    is_available bit not null,
    price numeric(10,2) not null,
    stock_quantity int not null,
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    id uniqueidentifier not null,
    medication_id uniqueidentifier not null,
    pharmacy_id uniqueidentifier not null,
    primary key (id)
);

create table prescriptions (
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    id uniqueidentifier not null,
    order_id uniqueidentifier,
    user_id uniqueidentifier not null,
    status varchar(20) not null check (status in ('PENDING','APPROVED','REJECTED')),
    pharmacist_comment varchar(max),
    photo_url varchar(max) not null,
    primary key (id)
);

create table refresh_tokens (
    created_at datetime2(6) not null,
    expires_at datetime2(6) not null,
    id uniqueidentifier not null,
    user_id uniqueidentifier not null,
    token varchar(500) not null,
    primary key (id)
);

create table return_requests (
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    id uniqueidentifier not null,
    order_id uniqueidentifier not null,
    user_id uniqueidentifier not null,
    status varchar(20) not null check (status in ('PENDING','APPROVED','REJECTED','REFUNDED')),
    admin_comment varchar(max),
    reason varchar(max) not null,
    primary key (id)
);

create table reviews (
    courier_rating int,
    rating int not null,
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    courier_id uniqueidentifier,
    id uniqueidentifier not null,
    order_id uniqueidentifier not null,
    patient_id uniqueidentifier not null,
    pharmacy_id uniqueidentifier not null,
    status varchar(20) not null check (status in ('PENDING','APPROVED','REJECTED')),
    comment varchar(max),
    courier_comment varchar(max),
    primary key (id)
);

create table search_logs (
    searched_at datetime2(6) not null,
    id uniqueidentifier not null,
    user_id uniqueidentifier,
    query varchar(255) not null,
    primary key (id)
);

create table users (
    is_active bit not null,
    is_verified bit not null,
    latitude numeric(10,8),
    longitude numeric(11,8),
    created_at datetime2(6) not null,
    updated_at datetime2(6) not null,
    id uniqueidentifier not null,
    pharmacy_id uniqueidentifier,
    phone varchar(20) not null,
    role varchar(20) not null check (role in ('SUPER_ADMIN','PHARMACY_ADMIN','PHARMACY_EMPLOYEE','PATIENT','DELIVERY')),
    city varchar(100),
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    email varchar(255) not null,
    fcm_token varchar(255),
    password varchar(255) not null,
    address varchar(max),
    primary key (id)
);

create index idx_user_id 
   on audit_logs (user_id);

create index idx_action 
   on audit_logs (action);

create index idx_entity_type 
   on audit_logs (entity_type);

create index idx_timestamp 
   on audit_logs (timestamp);

alter table deliveries 
   add constraint UKk36n9p5v7dd96hpgkwybvbogt unique (order_id);

alter table employee_permissions 
   add constraint UK3q6oleptsnisgqxshio2op8hs unique (employee_id);

alter table order_items 
   add constraint UKghqxqb19bhbojhmynl6iqbp6w unique (order_id, medication_id);

alter table orders 
   add constraint UKnthkiu7pgmnqnu86i2jyoe2v7 unique (order_number);

alter table payments 
   add constraint UK8vo36cen604as7etdfwmyjsxt unique (order_id);

create unique nonclustered index UKlryndveuwa4k5qthti0pkmtlx 
   on payments (transaction_id) where transaction_id is not null;

alter table pharmacies 
   add constraint UKidb4iqtyt8vw86ngjh6scj3qp unique (user_id);

alter table pharmacies 
   add constraint UKias0wno0h3b9fsxcof16sifo0 unique (license_number);

alter table pharmacy_medications 
   add constraint UK2td6wgpimhaxgiqn86okyq0eg unique (pharmacy_id, medication_id);

alter table refresh_tokens 
   add constraint UKghpmfn23vmxfu3spu3lfg4r2d unique (token);

alter table return_requests 
   add constraint UKmlyu5yxuty6xy4bndr5rrnfr0 unique (order_id);

alter table reviews 
   add constraint UKsbkc1fll14ly5y6yxxk2jwlef unique (order_id);

alter table users 
   add constraint UKdu5v5sr43g5bfnji4vb8hg5s3 unique (phone);

alter table users 
   add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

alter table deliveries 
   add constraint FKj83vua77cbq1e1u1dwo1fuwlh 
   foreign key (delivery_person_id) 
   references users;

alter table deliveries 
   add constraint FK7isx0rnbgqr1dcofd5putl6jw 
   foreign key (order_id) 
   references orders;

alter table notifications 
   add constraint FK9y21adhxn0ayjhfocscqox7bh 
   foreign key (user_id) 
   references users;

alter table order_items 
   add constraint FKiu183tc0p7bgi0u4v15f8lus3 
   foreign key (medication_id) 
   references medications;

alter table order_items 
   add constraint FKbioxgbv59vetrxe0ejfubep1w 
   foreign key (order_id) 
   references orders;

alter table orders 
   add constraint FKdkk3672x0oqs4lrc2pxcvmu2l 
   foreign key (patient_id) 
   references users;

alter table orders 
   add constraint FKhexqog8hk3m7u4g7slprn2wfs 
   foreign key (pharmacy_id) 
   references pharmacies;

alter table payments 
   add constraint FK81gagumt0r8y3rmudcgpbk42l 
   foreign key (order_id) 
   references orders;

alter table payouts 
   add constraint FKgoimix7totugm6nfloxqbtogc 
   foreign key (pharmacy_id) 
   references pharmacies;

alter table pharmacies 
   add constraint FKmetjb871tcig2ijxurh7qop5b 
   foreign key (user_id) 
   references users;

alter table pharmacy_medications 
   add constraint FKkpix8hxk8cb90fk3nrnbonh9m 
   foreign key (medication_id) 
   references medications;

alter table pharmacy_medications 
   add constraint FKkfm3dyvjxc3qr3108u7tw9kah 
   foreign key (pharmacy_id) 
   references pharmacies;

alter table prescriptions 
   add constraint FKamg8mabgywbd9af09hxff4jqv 
   foreign key (order_id) 
   references orders;

alter table prescriptions 
   add constraint FK9sqwg2opdx0r4ts1vq7ei3q1c 
   foreign key (user_id) 
   references users;

alter table refresh_tokens 
   add constraint FK1lih5y2npsf8u5o3vhdb9y0os 
   foreign key (user_id) 
   references users;

alter table return_requests 
   add constraint FKbski88d6kewx0cbj5pk7nes01 
   foreign key (order_id) 
   references orders;

alter table return_requests 
   add constraint FK6pd9hi2rbbct43io2pgcma1sh 
   foreign key (user_id) 
   references users;

alter table reviews 
   add constraint FKjsqlx0iwm9p6jk3sunguavmwx 
   foreign key (courier_id) 
   references users;

alter table reviews 
   add constraint FKqwgq1lxgahsxdspnwqfac6sv6 
   foreign key (order_id) 
   references orders;

alter table reviews 
   add constraint FKp6ff3lit060ehcuyc5artangi 
   foreign key (patient_id) 
   references users;

alter table reviews 
   add constraint FKokbcohwwhomumcaj89c5abh6o 
   foreign key (pharmacy_id) 
   references pharmacies;

alter table search_logs 
   add constraint FKaxl330wos94o6v1a13oyvgqr0 
   foreign key (user_id) 
   references users 
   on delete cascade;

alter table users 
   add constraint FKbgq15hbsk1ltdpiymvf3hob7l 
   foreign key (pharmacy_id) 
   references pharmacies 
   on delete cascade;
