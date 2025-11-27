-- Create schema for the pricing domain (database creation is typically managed outside app migrations)
create schema if not exists pricing;

-- Product table
create table if not exists pricing.product
(
    id          bigserial primary key,
    sku         varchar(255)   not null unique,
    name        varchar(255)   not null,
    base_price  numeric(19, 2) not null
);

-- Price adjustment table
create table if not exists pricing.price_adjustment
(
    id         bigserial primary key,
    productid  bigint         not null references pricing.product (id) on delete cascade,
    type       varchar(10)    not null,
    value      numeric(19, 2) not null,
    mode       varchar(10)    not null,
    updated_at timestamptz    not null default now(),
    constraint chk_price_adjustment_type check (type in ('PROMO', 'TAX', 'FEE')),
    constraint chk_price_adjustment_mode check (mode in ('ABSOLUTE', 'PERCENT'))
);

create index if not exists idx_price_adjustment_product on pricing.price_adjustment (productid);
