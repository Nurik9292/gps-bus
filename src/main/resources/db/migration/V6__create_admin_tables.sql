-- db/migration/V6__Create_Admin_Tables.sql

-- Таблица администраторов
CREATE TABLE IF NOT EXISTS admins (
                                      id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(20) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    is_super_admin BOOLEAN DEFAULT false,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

-- Таблица баннеров
CREATE TABLE IF NOT EXISTS banners (
                                       id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    image_url TEXT NOT NULL,
    target_url TEXT,
    is_active BOOLEAN DEFAULT true,
    display_order INTEGER DEFAULT 0,
    start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

-- Таблица городов
CREATE TABLE IF NOT EXISTS cities (
                                      id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    name_tm VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
    );

-- Индексы для производительности
CREATE INDEX IF NOT EXISTS idx_admins_username ON admins(username);
CREATE INDEX IF NOT EXISTS idx_admins_active ON admins(is_active);
CREATE INDEX IF NOT EXISTS idx_banners_active ON banners(is_active);
CREATE INDEX IF NOT EXISTS idx_banners_display_order ON banners(display_order);
CREATE INDEX IF NOT EXISTS idx_cities_active ON cities(is_active);
CREATE INDEX IF NOT EXISTS idx_cities_name ON cities(name);

-- Начальные данные - создание супер-администратора
INSERT INTO admins (id, username, password_hash, full_name, is_super_admin, is_active)
VALUES (
           'admin-00001',
           'admin',
           '$2a$10$eImiTXuWVxfM37uY4JANjOhSzm/yQLdpb5bLGFpwT3Zq8TGtmPGqy', -- password: admin123
           'Super Administrator',
           true,
           true
       ) ON CONFLICT (username) DO NOTHING;

-- Тестовые города
INSERT INTO cities (id, name, name_tm, display_order) VALUES
                                                          ('city-001', 'Ашхабад', 'Aşgabat', 1),
                                                          ('city-002', 'Туркменабад', 'Türkmenabat', 2),
                                                          ('city-003', 'Дашогуз', 'Daşoguz', 3),
                                                          ('city-004', 'Туркменбаши', 'Türkmenbaşy', 4),
                                                          ('city-005', 'Мары', 'Mary', 5)
    ON CONFLICT (id) DO NOTHING;

-- Тестовые баннеры
INSERT INTO banners (id, title, image_url, target_url, display_order) VALUES
                                                                          ('banner-001', 'Welcome to Bus Route System', '/images/welcome-banner.jpg', '/routes', 1),
                                                                          ('banner-002', 'Real-time Bus Tracking', '/images/tracking-banner.jpg', '/tracking', 2)
    ON CONFLICT (id) DO NOTHING;
