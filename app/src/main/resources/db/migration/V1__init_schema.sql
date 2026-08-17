-- Companies (tenants)
CREATE TABLE companies (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Internal materials (per company, usually from 1C)
CREATE TABLE materials (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    code            VARCHAR(100) NOT NULL,
    name            VARCHAR(500) NOT NULL,
    unit_of_measure VARCHAR(50),
    category        VARCHAR(100),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_materials_company_code UNIQUE (company_id, code)
);

CREATE INDEX idx_materials_company ON materials(company_id);

-- External products (LemanaPro catalog)
CREATE TABLE external_products (
    id              BIGSERIAL PRIMARY KEY,
    source          VARCHAR(50) NOT NULL,
    external_sku    VARCHAR(100) NOT NULL,
    name            VARCHAR(1000) NOT NULL,
    unit_of_measure VARCHAR(50),
    product_url     VARCHAR(1000),
    last_synced_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_external_products_source_sku UNIQUE (source, external_sku)
);

-- Mapping internal material <-> external product
CREATE TABLE material_mappings (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT NOT NULL REFERENCES companies(id),
    material_id         BIGINT NOT NULL REFERENCES materials(id),
    external_product_id BIGINT NOT NULL REFERENCES external_products(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confidence          NUMERIC(5,4),
    matched_by          VARCHAR(50),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    CONSTRAINT uk_material_mappings_material UNIQUE (material_id)
);

CREATE INDEX idx_material_mappings_company ON material_mappings(company_id);
CREATE INDEX idx_material_mappings_status ON material_mappings(company_id, status);

-- Price history / snapshots
CREATE TABLE material_prices (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT NOT NULL REFERENCES companies(id),
    material_id         BIGINT NOT NULL REFERENCES materials(id),
    external_product_id BIGINT REFERENCES external_products(id),
    price               NUMERIC(19,4) NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'RUB',
    source              VARCHAR(50) NOT NULL,
    fetched_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_material_prices_material_fetched ON material_prices(material_id, fetched_at DESC);
CREATE INDEX idx_material_prices_company ON material_prices(company_id);
