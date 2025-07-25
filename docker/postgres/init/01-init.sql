CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
CREATE EXTENSION IF NOT EXISTS postgis_tiger_geocoder;

CREATE SCHEMA IF NOT EXISTS transport;
CREATE SCHEMA IF NOT EXISTS routing;
CREATE SCHEMA IF NOT EXISTS admin;

GRANT ALL PRIVILEGES ON DATABASE bus_route_db TO bus_route_user;
GRANT ALL ON SCHEMA public TO bus_route_user;
GRANT ALL ON SCHEMA transport TO bus_route_user;
GRANT ALL ON SCHEMA routing TO bus_route_user;
GRANT ALL ON SCHEMA admin TO bus_route_user;

INSERT INTO spatial_ref_sys (srid, auth_name, auth_srid, proj4text, srtext)
VALUES (32640, 'EPSG', 32640,
        '+proj=utm +zone=40 +datum=WGS84 +units=m +no_defs',
        'PROJCS["WGS 84 / UTM zone 40N",GEOGCS["WGS 84",DATUM["WGS_1984",SPHEROID["WGS 84",6378137,298.257223563,AUTHORITY["EPSG","7030"]],AUTHORITY["EPSG","6326"]],PRIMEM["Greenwich",0,AUTHORITY["EPSG","8901"]],UNIT["degree",0.0174532925199433,AUTHORITY["EPSG","9122"]],AUTHORITY["EPSG","4326"]],PROJECTION["Transverse_Mercator"],PARAMETER["latitude_of_origin",0],PARAMETER["central_meridian",57],PARAMETER["scale_factor",0.9996],PARAMETER["false_easting",500000],PARAMETER["false_northing",0],UNIT["metre",1,AUTHORITY["EPSG","9001"]],AUTHORITY["EPSG","32640"]]')
    ON CONFLICT (srid) DO NOTHING;