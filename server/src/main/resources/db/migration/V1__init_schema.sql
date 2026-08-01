-- QMarket v0.1 initial schema

-- ===================== IDENTITY =====================

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    phone           VARCHAR(30),
    is_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_users_email ON users(email);

-- ===================== CATALOG =====================

CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    slug        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(255),
    parent_id   UUID REFERENCES categories(id) ON DELETE SET NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent ON categories(parent_id);

CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    description     VARCHAR(255),
    short_description VARCHAR(500),
    sku             VARCHAR(100) UNIQUE,
    price           NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    compare_at_price NUMERIC(12, 2) CHECK (compare_at_price IS NULL OR compare_at_price >= 0),
    cost_price      NUMERIC(12, 2) CHECK (cost_price IS NULL OR cost_price >= 0),
    stock_quantity  INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured     BOOLEAN NOT NULL DEFAULT FALSE,
    category_id     UUID REFERENCES categories(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(is_active);
CREATE INDEX idx_products_featured ON products(is_featured) WHERE is_featured = TRUE;

-- Seed default roles
INSERT INTO roles (name, description) VALUES
    ('ROLE_USER', 'Regular customer'),
    ('ROLE_ADMIN', 'Administrator with full access'),
    ('ROLE_MANAGER', 'Store manager');
