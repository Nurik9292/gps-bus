CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    is_active BOOLEAN DEFAULT true,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_notifications_active ON notifications(is_active);
CREATE INDEX idx_notifications_display_order ON notifications(display_order);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_active_display_order ON notifications(is_active, display_order);

CREATE TABLE IF NOT EXISTS notification_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_events_notification_id ON notification_events(notification_id, occurred_at);
CREATE INDEX idx_notification_events_event_type ON notification_events(event_type, occurred_at);
CREATE INDEX idx_notification_events_occurred_at ON notification_events(occurred_at DESC);
CREATE INDEX idx_notification_events_payload ON notification_events USING GIN (payload);

COMMENT ON TABLE notifications IS 'System notifications for users';
COMMENT ON COLUMN notifications.title IS 'Notification title';
COMMENT ON COLUMN notifications.content IS 'Notification content/body';
COMMENT ON COLUMN notifications.is_active IS 'Whether the notification is currently active';
COMMENT ON COLUMN notifications.display_order IS 'Order for displaying notifications';

COMMENT ON TABLE notification_events IS 'Event Store for notification domain events';
