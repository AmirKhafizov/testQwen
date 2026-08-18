# Cost Monitor

Система мониторинга цен строительных материалов (источник: kazan.lemanapro.ru через парсер) и прогнозирования стоимости объектов строительства на основе расхода из 1С.

**Стек:** Java 21 · Spring Boot 3.3 · Gradle · PostgreSQL · Flyway · Testcontainers · Playwright · Docker · SpringDoc OpenAPI

## Что уже есть

- Мультитенантность (`company_id`)
- Справочник материалов + маппинг на LemanaPro (`MaterialMapping`)
- **Парсер цен LemanaPro** (Playwright, обход Qrator)
- **Ежедневный job в 10:00 по Москве** (`Europe/Moscow`)
- История цен (`material_prices`)
- Admin API для ручного запуска и probe SKU
- **Swagger UI** для ручного тестирования API
- Unit-тесты парсера HTML + TenantContext
- **Docker Compose** для локального запуска

## Swagger UI

После старта приложения:

| Что | URL |
|-----|-----|
| **Swagger UI** | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

В UI доступны:
- **Health** — `GET /api/v1/ping`
- **Prices (Admin)** — probe SKU, refresh одной/всех компаний

Кнопка **Try it out** → Execute — удобно гонять запросы без curl.

## Docker Compose (рекомендуется)

### Только PostgreSQL (приложение на хосте)

```bash
docker compose up -d db

# затем локально
./gradlew :app:bootRun
```

БД: `localhost:5432`, user/pass/db = `cost_monitor`.

### Полный стек (app + db)

```bash
docker compose --profile app up --build
```

- API: http://localhost:8080  
- Swagger: http://localhost:8080/swagger-ui.html  
- Ping: http://localhost:8080/api/v1/ping

```bash
docker compose --profile app down
docker compose down -v   # сброс данных Postgres
```

## Расписание парсера

```yaml
app:
  scheduler:
    price-update-enabled: true
    price-update-cron: "0 0 10 * * *"   # каждый день в 10:00
    price-update-zone: Europe/Moscow
```

Job обновляет только **CONFIRMED** маппинги активных компаний.

## Парсер LemanaPro

Сайт закрыт Qrator — используется **Playwright (Chromium)**.

```bash
# на хосте, если app не в Docker
npx --yes playwright@1.47.0 install chromium
```

### Admin API

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/admin/prices/refresh-all` | Как ночной job |
| POST | `/api/v1/admin/prices/refresh/{companyId}` | Одна компания |
| GET | `/api/v1/admin/prices/probe/{sku}` | Цена без записи в БД |

Удобнее вызывать через Swagger UI.

## Локальный запуск

```bash
docker compose up -d db
gradle wrapper --gradle-version 8.10.2   # если нет wrapper
./gradlew :app:bootRun
./gradlew :app:test
```

## Дальнейшие шаги

1. Fuzzy-matching 1С ↔ LemanaPro + UI подтверждения
2. ConstructionObject + расход из 1С
3. Cost Calculation Engine
4. Frontend (React)
5. Spring Security + JWT

## Источник цен

Только **парсер** https://kazan.lemanapro.ru/ (B2B API отложен).
