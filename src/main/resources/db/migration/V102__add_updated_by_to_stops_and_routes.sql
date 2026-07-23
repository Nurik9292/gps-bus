ALTER TABLE bus_stops ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);
ALTER TABLE bus_routes ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100);

COMMENT ON COLUMN bus_stops.updated_by IS 'Username администратора, сделавшего последнее изменение (NULL для строк до V102)';
COMMENT ON COLUMN bus_routes.updated_by IS 'Username администратора, сделавшего последнее изменение (NULL для строк до V102)';
