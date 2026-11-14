CREATE TABLE IF NOT EXISTS designers (
    id BIGSERIAL PRIMARY KEY,
    canonical_name TEXT NOT NULL UNIQUE,
    canonical_slug TEXT NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS designer_aliases (
    id BIGSERIAL PRIMARY KEY,
    designer_id BIGINT NOT NULL REFERENCES designers(id),
    source VARCHAR(50) NOT NULL,
    platform_id TEXT,
    platform_name TEXT NOT NULL,
    platform_slug TEXT,
    UNIQUE (source, platform_name),
    UNIQUE (source, platform_id)
    );

CREATE INDEX idx_designer_aliases_lookup ON designer_aliases(source, platform_name);

CREATE INDEX idx_designer_aliases_designer ON designer_aliases(designer_id)

CREATE TABLE IF NOT EXISTS sold_listings
(
    id BIGSERIAL PRIMARY KEY,
    external_id BIGINT NOT NULL,
    source VARCHAR(50) NOT NULL,
    title TEXT,
    department TEXT,
    designer_ids INTEGER[],
    designer_names TEXT[],
    category TEXT,
    category_path TEXT,
    category_size TEXT,
    category_path_size TEXT,

    color TEXT,
    condition TEXT,
    size TEXT,
    styles TEXT,
    country_of_origin VARCHAR(2),

    price INTEGER,
    original_price INTEGER,
    sold_pric INTEGER,
    sold_shipping_price INTEGER,
    discount_pct NUMERIC(8, 2),
    price_history INTEGER[],
    currency CHAR(3) DEFAULT 'USD',

    sold BOOLEAN,
    dropped BOOLEAN,

    created_at TIMESTAMPTZ,
    price_updated_at TIMESTAMPTZ,
    sold_at TIMESTAMPTZ,
    scraped_at TIMESTAMPTZ DEFAULT NOW(),

    cover_photo_url TEXT,
    local_image_path TEXT,
    image_download_status VARCHAR(20),
    image_download_attempted_at TIMESTAMPTZ,
    photo_count INTEGER,
    measurement_count INTEGER,

    location TEXT,
    seller_id  INTEGER,
    seller_total_transactions INTEGER,
    seller_trusted BOOLEAN,
    seller_rating_average DOUBLE PRECISION,
    seller_rating_count INTEGER,

    followers_count INTEGER,
    heat_score DOUBLE PRECISION,

    raw_payload JSONB,

    UNIQUE (source, external_id)
    );

CREATE INDEX IF NOT EXISTS idx_listings_source ON sold_listings (source);
CREATE INDEX IF NOT EXISTS idx_listings_sold_at ON sold_listings (sold_at) WHERE sold = TRUE;
CREATE INDEX IF NOT EXISTS idx_listings_designer_ids ON sold_listings USING GIN (designer_ids);
CREATE INDEX IF NOT EXISTS idx_listings_brand_category ON sold_listings (category);
CREATE INDEX IF NOT EXISTS idx_listings_price ON sold_listings (price);
CREATE INDEX IF NOT EXISTS idx_listings_image_pending ON sold_listings (image_download_status)
    WHERE image_download_status IN ('pending', 'failed');
CREATE INDEX IF NOT EXISTS idx_price_history ON sold_listings USING GIN (price_history);