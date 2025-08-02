CREATE TABLE cities (
                        id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        name_tm VARCHAR(100),
                        is_active BOOLEAN DEFAULT true,
                        display_order INTEGER DEFAULT 0,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        version BIGINT DEFAULT 0
);

CREATE TABLE admins (
                        id VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(20) UNIQUE NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        full_name VARCHAR(100) NOT NULL,
                        is_active BOOLEAN DEFAULT true,
                        is_super_admin BOOLEAN DEFAULT false,
                        last_login_at TIMESTAMP WITH TIME ZONE,
                        avatar TEXT,
                        created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        version BIGINT DEFAULT 0
);

CREATE TABLE banners (
                         id VARCHAR(36) PRIMARY KEY,
                         title VARCHAR(200) NOT NULL,
                         image_url TEXT NOT NULL,
                         target_url TEXT,
                         is_active BOOLEAN DEFAULT true,
                         display_order INTEGER DEFAULT 0,
                         start_date TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         end_date TIMESTAMP WITH TIME ZONE,
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         version BIGINT DEFAULT 0
);

-- Индексы для производительности
CREATE INDEX idx_cities_active ON cities(is_active);
CREATE INDEX idx_cities_name ON cities(name);
CREATE INDEX idx_admins_username ON admins(username);
CREATE INDEX idx_admins_active ON admins(is_active);
CREATE INDEX idx_banners_active ON banners(is_active);
CREATE INDEX idx_banners_display_order ON banners(display_order);
CREATE INDEX CONCURRENTLY idx_admins_has_avatar ON admins (id, username) WHERE avatar IS NOT NULL;


COMMENT ON COLUMN admins.avatar IS 'Base64 encoded avatar image or file path';