# Omno Pricing Service

High-throughput pricing API with multi-level caching (Caffeine + Redis), stale-while-revalidate refresh, and pub/sub invalidation across replicas.

## Architecture (at a glance)
```
[Client]
   |
   v
[HTTP Controllers] --(DTOs/validation)--> [PriceService]
   |                                           |
   |                              [MultiLevel Cache Manager]
   |                               |        ^
   |                               v        |
   |                      L1: Caffeine (soft TTL)
   |                      L2: Redis (TTL, key prefix, pub/sub + circuit breaker)
   |                               |        |
   |                               +--------+
   |                                   |
   v                                   v
[Postgres: products, price_adjustments]
```

## Domain & Data Model
- `Product`: `id (PK)`, `sku (unique)`, `name`, `base_price`.
- `PriceAdjustment`: `id (PK)`, `product_id (FK)`, `type (PROMO|TAX|FEE)`, `value`, `mode (ABSOLUTE|PERCENT)`, `updated_at`.
- Final price formula: start with `base_price`, apply absolute adjustments, then percent adjustments (scale 2, HALF_EVEN).

## How to run
- Local JVM: `./mvnw spring-boot:run` (requires Postgres + Redis reachable via `SPRING_DATASOURCE_URL` / `SPRING_DATA_REDIS_HOST`).
- Docker compose/swarm: `docker compose up --build` (uses `compose.yaml`, includes Postgres, Redis, two app replicas, otel-collector).
- Tests: `./mvnw test` (uses Testcontainers; no local DB/Redis needed).

## Config surface (env-friendly)
- DB pool: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT_MS`, `DB_POOL_VALIDATION_TIMEOUT_MS`, `DB_POOL_MAX_LIFETIME_MS`.
- Redis: `SPRING_DATA_REDIS_HOST/PORT/DATABASE/TIMEOUT/CLIENT_NAME`, `CACHE_L2_PREFIX` (namespacing).
- Cache toggles & tuning: `CACHE_ENABLED`, `CACHE_L1_ENABLED`, `CACHE_L2_ENABLED`, `CACHE_L1_MAX_SIZE`, `CACHE_L1_TTL`, `CACHE_L2_TTL`, `CACHE_REFRESH_SOFT_TTL_RATIO`, `CACHE_INVALIDATE_ENABLED`.
- Observability: OTLP endpoints in `application.yml` (collector defaults in compose), actuator metrics exposed at `/actuator/metrics`.

## HTTP scripts
- See `http/api.http` for ready-to-run requests (REST Client / IntelliJ / HTTPie friendly): health, seeded reads, admin adjustments, DB-only path, 404 case.

## Benchmarks (from step #5)
- DB-only baseline (30s, 50 VUs): ~3.6k rps; avg 12.37ms, p95 27.72ms, max 182.54ms.
- Cached baseline (45s, 100 VUs): ~19.1k rps; avg 5.14ms, p95 12.06ms, p99 22.96ms, max 234.84ms (narrowly missed p95<10ms target).
- Freshness SLA (write→read delay across replicas): avg ~27–35ms, p95 ~55–63ms, max ~129–180ms; timeoutRate 0.
- How to rerun: use `bench/baseline-db.js` for DB-only; `bench/cache-test.js` for cached path; set `BASE_URL` (and `HOT_ID` for cached).

## Limitations / trade-offs
- No jitter/backoff on expirations; synchronized TTLs could spike traffic on rolling restarts.
- Redis circuit breaker prevents overload, but we still rely on Redis availability for L2 benefits; noisy neighbors may increase tail latencies.
- Cache refresh window is ratio-based; tune `CACHE_REFRESH_SOFT_TTL_RATIO` alongside `CACHE_L1_TTL` to avoid excessive refresh churn.
- No Grafana dashboards committed (step #8 optional); OTLP/Prometheus endpoints are exposed for downstream dashboards.

## Endpoints (summary)
- `GET /price/{id}` — cached path (multi-level).
- `GET /price-db/{id}` — DB-only path (no cache).
- `POST /admin/price/{id}/adjustments` — replace adjustments list for product.
- `POST /admin/seed` — seed demo data (`count`, `adjustRate`, `clear` flags)
- `POST /admin/clear` — truncate tables.
- `GET /health` — liveness.
