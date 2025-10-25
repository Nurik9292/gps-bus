-- V18: Создание таблицы для хранения событий баннеров (Event Sourcing)
--
-- Event Sourcing - паттерн, где все изменения сохраняются как последовательность событий.
-- Преимущества:
-- - Полный аудит всех изменений (кто, когда, что изменил)
-- - Возможность восстановить состояние на любой момент времени
-- - История всех операций для аналитики
-- - Отладка: понимание последовательности событий
--
-- Таблица banner_events - это append-only лог событий.
-- События НИКОГДА не изменяются и не удаляются, только добавляются.

CREATE TABLE IF NOT EXISTS banner_events (
    -- Уникальный ID события
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ID баннера, к которому относится событие
    banner_id UUID NOT NULL,

    -- Тип события (BannerCreatedEvent, BannerUpdatedEvent, и т.д.)
    event_type VARCHAR(100) NOT NULL,

    -- Версия формата события (для совместимости при эволюции схемы)
    event_version INTEGER NOT NULL DEFAULT 1,

    -- Полезная нагрузка события в формате JSON
    -- Содержит все данные события (title, type, imageUrl и т.д.)
    payload JSONB NOT NULL,

    -- Метаданные события
    -- Может содержать информацию о пользователе, IP-адресе, причине и т.д.
    metadata JSONB,

    -- Временная метка возникновения события
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Временная метка записи события в базу данных
    -- Может отличаться от occurred_at в случае задержек или повторных попыток
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Индекс для быстрого поиска всех событий баннера
-- Используется при восстановлении состояния баннера
CREATE INDEX idx_banner_events_banner_id ON banner_events(banner_id, occurred_at);

-- Индекс для поиска событий по типу
-- Полезен для аналитики (например, "сколько раз создавались баннеры")
CREATE INDEX idx_banner_events_event_type ON banner_events(event_type, occurred_at);

-- Индекс для поиска событий по времени
-- Используется для получения событий за определенный период
CREATE INDEX idx_banner_events_occurred_at ON banner_events(occurred_at DESC);

-- GIN индекс для быстрого поиска по JSON payload
-- Позволяет искать события по содержимому (например, все события с type='MAIN')
CREATE INDEX idx_banner_events_payload ON banner_events USING GIN (payload);

-- Комментарии к таблице и колонкам для документации
COMMENT ON TABLE banner_events IS 'Event Store для хранения всех событий баннеров. Append-only таблица для Event Sourcing паттерна.';

COMMENT ON COLUMN banner_events.event_id IS 'Уникальный идентификатор события';
COMMENT ON COLUMN banner_events.banner_id IS 'ID баннера, к которому относится событие';
COMMENT ON COLUMN banner_events.event_type IS 'Тип события: BannerCreatedEvent, BannerUpdatedEvent, BannerActivatedEvent, BannerDeactivatedEvent, BannerDeletedEvent';
COMMENT ON COLUMN banner_events.event_version IS 'Версия схемы события для обратной совместимости';
COMMENT ON COLUMN banner_events.payload IS 'JSON с данными события (title, type, imageUrl, targetUrl, и т.д.)';
COMMENT ON COLUMN banner_events.metadata IS 'JSON с метаданными (user_id, ip_address, reason, и т.д.)';
COMMENT ON COLUMN banner_events.occurred_at IS 'Когда событие произошло (временная метка из приложения)';
COMMENT ON COLUMN banner_events.recorded_at IS 'Когда событие было записано в базу данных';

-- Пример данных в таблице:
--
-- event_id: '123e4567-e89b-12d3-a456-426614174000'
-- banner_id: '550e8400-e29b-41d4-a716-446655440000'
-- event_type: 'BannerCreatedEvent'
-- event_version: 1
-- payload: {
--   "title": "Акция недели",
--   "type": "MAIN",
--   "imageUrl": "banner1.jpg",
--   "targetUrl": "promo.com",
--   "startDate": "2025-01-01T00:00:00Z",
--   "endDate": "2025-12-31T23:59:59Z",
--   "displayOrder": 10,
--   "content": "..."
-- }
-- metadata: {
--   "userId": "admin-123",
--   "ipAddress": "192.168.1.1",
--   "correlationId": "req-456"
-- }
-- occurred_at: '2025-01-15 10:30:00+00'
-- recorded_at: '2025-01-15 10:30:01+00'
