
ALTER TABLE banners ADD COLUMN reply_time INTEGER;

COMMENT ON COLUMN banners.reply_time IS 'Time in seconds to wait for user reply/response. Only applicable for POPUP type banners. NULL for other banner types.';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_banners_popup_reply_time
ON banners(type, reply_time, is_active)
WHERE type = 'popup' AND reply_time IS NOT NULL;
