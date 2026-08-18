# Cost Monitor

Система мониторинга цен строительных материалов (источник: kazan.lemanapro.ru через парсер) и прогнозирования стоимости объектов строительства на основе расхода из 1С.

**Стек:** Java 21 · Spring Boot 3.3 · Gradle · PostgreSQL · Flyway · Testcontainers · Playwright · Docker

## Что уже есть

- Мультитенантность (`company_id`)
- Справочник материалов + маппинг на LemanaPro (`MaterialMapping`)
- **Парсер цен LemanaPro** (Playwright, обход Qrator)
- **Ежедневный job в 10:00 по Москве** (`Europe/Moscow`)
- История цен (`material_prices`)
- Admin API для ручного запуска и probe SKU
- Unit-тесты парсера HTML + TenantContext
- **Docker Compose** для локального запуска

## Docker Compose (рекомендуется)

### Только PostgreSQL (приложение на хосте)

```bash
docker compose up -d db

# затем локально
./gradlew :app:bootRun
# или gradle :app:bootRun
```

БД: `localhost:5432`, user/pass/db = `cost_monitor`.

### Полный стек (app + db)

Сборка образа дольше (Playwright + Chromium внутри):

```bash
docker compose --profile app up --build
```

Приложение: http://localhost:8080  
Ping: http://localhost:8080/api/v1/ping

Остановка:

```bash
docker compose --profile app down
# данные Postgres сохраняются в volume cost_monitor_pgdata
```

Сброс данных БД:

```bash
docker compose down -v
```

## Расписание парсера

По умолчанию:

```yaml
app:
  scheduler:
    price-update-enabled: true
    price-update-cron: "0 0 10 * * *"   # каждый день в 10:00
    price-update-zone: Europe/Moscow
```

Job обновляет цены только для **CONFIRMED** маппингов всех активных компаний.

## Парсер LemanaPro

Сайт закрыт Qrator — обычный HTTP/Jsoup получает 403. Используется **Playwright (Chromium)**.

### Установка браузера на хосте (если app не в Docker)

```bash
npx --yes playwright@1.47.0 install chromium
```

### Как работает fetch по SKU

1. Открывает `https://kazan.lemanapro.ru/search/?q={sku}`
2. Ищет ссылку на карточку товара с артикулом
3. Парсит цену (`PriceHtmlParser`)
4. Пауза `app.lemana.request-delay-ms` (по умолчанию 2 с)

### Admin API (временно без auth)

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/admin/prices/refresh-all` | Как ночной job |
| POST | `/api/v1/admin/prices/refresh/{companyId}` | Одна компания |
| GET | `/api/v1/admin/prices/probe/{sku}` | Цена без записи в БД |

```bash
curl -X POST http://localhost:8080/api/v1/admin/prices/refresh-all
curl http://localhost:8080/api/v1/admin/prices/probe/81976749
```

## Локальный запуск без Docker-приложения

### Требования

- JDK 21+
- Docker (хотя бы для Postgres / Testcontainers)
- Chromium для Playwright (если парсер на хосте)

### БД

```bash
docker compose up -d db
```

### Приложение

```bash
gradle wrapper --gradle-version 8.10.2   # если нет wrapper
./gradlew :app:bootRun
```

### Тесты

```bash
./gradlew :app:test
```

## Структура (ключевое)

```
infrastructure/price/
  PriceProvider.java
  LemanaProParserProvider.java    # Playwright
  PriceHtmlParser.java
application/price/
  PriceUpdateService.java
  PriceUpdateScheduler.java       # cron 10:00 MSK
api/
  PriceAdminController.java
docker-compose.yml
Dockerfile
```

## Дальнейшие шаги

1. Fuzzy-matching названий 1С ↔ LemanaPro + UI подтверждения
2. ConstructionObject + расход из 1С
3. Cost Calculation Engine
4. Frontend (React)
5. Spring Security + JWT

## Источник цен

Только **парсер** https://kazan.lemanapro.ru/  
(B2B API отложен).
