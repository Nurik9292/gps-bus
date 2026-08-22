# gps-bus

Real-time transit tracking backend for a city bus network.
**4 cities · 97 routes · 1,292 vehicles · ~15,600 GPS updates a minute · 1–2 s from provider poll to the passenger map.**

## What it does

Two GPS provider APIs are polled every 5 seconds. Raw GPS lies — positions arrive noisy, delayed and out of order — so every fix goes through a pipeline before anyone sees it:

```
provider feeds → normalize + dedup → outlier filter → map matching → direction → ETA → WebSocket
```

- **Normalize / dedup** — two upstream feeds merged into one stream
- **Outlier filter** — speed-based guards drop off-road and corrupted fixes
- **Map matching** — each position snapped onto the route polyline (PostGIS)
- **Direction** — route tangent + trajectory consistency, with flip guards
- **ETA** — per-stop arrival times from rolling segment speeds, historical fallback
- **Delivery** — route-scoped WebSocket topics, Redis pub/sub fan-out across instances, delta updates

When a provider feed drops, vehicles are marked stale and fade on the map instead of freezing in place.

## Stack

Java 17 · Spring Boot 3.5 (reactive) · WebSocket (STOMP) · Redis · PostgreSQL + PostGIS · Docker

## Structure

```
src/        application code: ingestion, pipeline stages, delivery, admin API
docker/     container setup
Makefile    common tasks
```

## Status

This is the codebase of a production system. A self-contained demo mode — mock GPS simulation, seeded routes, one-command Docker start — is in progress.

---

Built and maintained by [Nury Davletov](https://github.com/Nurik9292) — Full Stack Developer, SaaS platforms, marketplaces and real-time systems.
