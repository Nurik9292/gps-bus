# OSRM local OSM overlays

Small hand-authored `.osm` files patching pedestrian infrastructure that is
**missing from upstream OpenStreetMap** but exists in reality (verified against
Yandex / local knowledge). Each overlay is merged into the downloaded
`turkmenistan-base.osm.pbf` before OSRM preprocessing, so foot routing uses it.

`make osrm-setup` applies every `*.osm` here automatically (requires
`osmium-tool`: `apt-get install osmium-tool`). After it finishes:
`docker compose restart osrm`.

## Conventions
- New object IDs use the `9_000_000_000_xxx` range (far above real OSM IDs) to
  avoid collisions during `osmium merge`.
- A crossing way is `highway=footway` + `footway=crossing`; foot.lua routes it.
- Endpoints are placed at (or spanning toward) the bus-stop coordinates so OSRM
  snaps onto the overlay even without sharing nodes with existing ways.
- Document the real-world source (which crossing, why missing) in a `note` tag.

## Overlays
- `andalyb-north-crossing.osm` — pedestrian crossing on the north side of the
  Andalyb-Nyyazow traffic circle (Nurmuhammet Andalyp köçesi), between the
  Çopan ata transfer stops. Missing from OSM as of 2026-06; without it OSRM
  detoured ~150 m south to the only mapped crossings (433 m vs 249 m).

## Durability / prod
Overlays are local. On the prod OSRM host, run the same `make osrm-setup`
(with `osmium-tool` installed and this folder present) so prod routing matches.
The proper long-term fix is to map these crossings in upstream OSM; once they
appear there, the matching overlay file can be deleted.
