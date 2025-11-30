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
- Docker compose (single-host): `docker compose up --build`.
- Docker Swarm (stack name `omno`, tested with 2 app replicas):
  - `docker stack deploy -c compose.yaml omno`
  - Services: Postgres (5432), Redis (6379), app (8080), otel-collector (4317/4318/8889), Prometheus (9090), Grafana (3000).
  - To refresh Grafana provisioning: `docker service update --force omno_grafana`. To reset dashboards completely, remove the Grafana volume (`docker volume rm omno_grafana_data`) before redeploy.
- Tests: `./mvnw test` (uses Testcontainers; needs Docker). For unit-only in restricted env: `./mvnw -DskipITs test` (or run specific `-Dtest=...`).

## Observability (Prometheus + Grafana)
- Metrics: Prometheus scrapes the app at `/actuator/prometheus` and the otel-collector at `:8889`. Grafana is pre-provisioned:
  - Login: `http://localhost:3000` (admin/admin)
  - Datasource: Prometheus (`http://prometheus:9090`)
  - Dashboard: "Omno Observability" (provisioned from `grafana/dashboards/omno-observability.json`)
  - Panels include HTTP RPS/error%, p95/p99 latency, DB query p95, cache hit ratios, cache loader latency, refresh outcomes, inflight loads, evictions/clears.
- Traces: still exported OTLP to the collector (`otel-collector:4318`). No logs pipeline.

## Config surface (env-friendly)
- DB pool: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_POOL_CONNECTION_TIMEOUT_MS`, `DB_POOL_VALIDATION_TIMEOUT_MS`, `DB_POOL_MAX_LIFETIME_MS`.
- Redis: `SPRING_DATA_REDIS_HOST/PORT/DATABASE/TIMEOUT/CLIENT_NAME`, `CACHE_L2_PREFIX` (namespacing).
- Cache toggles & tuning: `CACHE_ENABLED`, `CACHE_L1_ENABLED`, `CACHE_L2_ENABLED`, `CACHE_L1_MAX_SIZE`, `CACHE_L1_TTL`, `CACHE_L2_TTL`, `CACHE_REFRESH_SOFT_TTL_RATIO`, `CACHE_INVALIDATE_ENABLED`.
- Observability: Prometheus scrape at `/actuator/prometheus`; OTLP tracing endpoint in `application*.yml` (collector defaults in compose).

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
- Integration tests require Docker (Testcontainers). Run unit-only when Docker is unavailable.

## Endpoints (summary)
- `GET /price/{id}` — cached path (multi-level).
- `GET /price-db/{id}` — DB-only path (no cache).
- `POST /admin/price/{id}/adjustments` — replace adjustments list for product.
- `POST /admin/seed` — seed demo data (`count`, `adjustRate`, `clear` flags)
- `POST /admin/clear` — truncate tables.
- `GET /health` — liveness.

## Test coverage
- Unit tests cover cache layers, pub/sub invalidation, circuit breaker wrapper, controllers (MockMvc), exception handler, mapper, seeding logic, and entities.
- Integration tests (Docker-required) cover cached path correctness, cache invalidation, and admin seed/clear.
- To generate a coverage report, run your IDE’s coverage runner or add JaCoCo (not included by default). For a quick view, run `./mvnw test` and inspect your IDE metrics; branch coverage improves with the cache/pubsub/controller tests.
