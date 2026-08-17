# Cost Monitor

Система мониторинга цен строительных материалов (источник: kazan.lemanapro.ru через парсер) и прогнозирования стоимости объектов строительства на основе расхода из 1С.

**Стек:** Java 21 · Spring Boot 3.3 · Gradle · PostgreSQL · Flyway · Testcontainers · Playwright

## Что уже есть

- Мультитенантность (`company_id`)
- Справочник материалов + маппинг на LemanaPro (`MaterialMapping`)
- **Парсер цен LemanaPro** (Playwright, обход Qrator)
- **Ежедневный job в 10:00 по Москве** (`Europe/Moscow`)
- История цен (`material_prices`)
- Admin API для ручного запуска и probe SKU
- Unit-тесты парсера HTML + TenantContext

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

### Установка браузера (один раз на машине)

```bash
# после сборки зависимостей
./gradlew :app:dependencies

# установить Chromium для Playwright
java -cp "$(./gradlew -q :app:printClasspath 2>/dev/null || echo '')" \
  com.microsoft.playwright.CLI install chromium

# или проще, если есть npm:
npx --yes playwright install chromium
```

Альтернатива через Maven-совместимый вызов:

```bash
./gradlew :app:bootRun   # при первом fetch Playwright сам подскажет команду install
```

Рекомендуемый способ из документации Playwright Java:

```bash
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

(при использовании Gradle можно скачать CLI jar из зависимости `com.microsoft.playwright:playwright`).

### Как работает fetch по SKU

1. Открывает `https://kazan.lemanapro.ru/search/?q={sku}`
2. Ищет ссылку на карточку товара, содержащую артикул
3. Открывает карточку и парсит цену (`PriceHtmlParser`: meta itemprop, CSS-классы, embedded JSON)
4. Соблюдает `app.lemana.request-delay-ms` (по умолчанию 2 с)

### Admin API (временно без auth)

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/v1/admin/prices/refresh-all` | Как ночной job |
| POST | `/api/v1/admin/prices/refresh/{companyId}` | Одна компания |
| GET | `/api/v1/admin/prices/probe/{sku}` | Проверка цены без записи в БД |

Пример:

```bash
curl -X POST http://localhost:8080/api/v1/admin/prices/refresh-all
curl http://localhost:8080/api/v1/admin/prices/probe/81976749
```

## Быстрый старт

### Требования

- JDK 21+
- Docker (PostgreSQL / Testcontainers)
- Chromium для Playwright

### БД

```bash
docker run -d --name cost-monitor-db \
  -e POSTGRES_DB=cost_monitor \
  -e POSTGRES_USER=cost_monitor \
  -e POSTGRES_PASSWORD=cost_monitor \
  -p 5432:5432 postgres:16
```

### Запуск

```bash
gradle wrapper --gradle-version 8.10.2   # если нет wrapper
./gradlew :app:bootRun
```

- Ping: http://localhost:8080/api/v1/ping

### Тесты

```bash
./gradlew :app:test
```

Unit-тесты `PriceHtmlParserTest` не требуют сети/браузера.

## Структура (ключевое)

```
infrastructure/price/
  PriceProvider.java              # интерфейс
  LemanaProParserProvider.java    # Playwright
  PriceHtmlParser.java            # извлечение цены из HTML
application/price/
  PriceUpdateService.java         # обновление по маппингам
  PriceUpdateScheduler.java       # cron 10:00 MSK
api/
  PriceAdminController.java       # ручной запуск / probe
```

## Дальнейшие шаги

1. Fuzzy-matching названий 1С ↔ LemanaPro + UI подтверждения
2. ConstructionObject + расход из 1С
3. Cost Calculation Engine (пересчёт при смене цены)
4. Frontend (React)
5. Spring Security + JWT
6. Прокси / ротация при усилении защиты Qrator

## Источник цен

Только **парсер** https://kazan.lemanapro.ru/  
(B2B API отложен).
