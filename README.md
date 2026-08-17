# Cost Monitor

Система мониторинга цен строительных материалов (источник: kazan.lemanapro.ru через парсер) и прогнозирования стоимости объектов строительства на основе расхода из 1С.

**Стек:** Java 21 · Spring Boot 3.3 · Gradle · PostgreSQL · Flyway · Testcontainers

## Возможности (план)

- Мультитенантность (несколько строительных компаний)
- Справочник материалов компании + маппинг на товары LemanaPro
- Парсер цен с kazan.lemanapro.ru
- История цен и автоматический пересчёт прогнозной стоимости объектов
- Интеграция с 1С (расход материалов)
- Unit + Integration тесты

## Структура проекта

```
cost-monitor/
├── app/                          # основной модуль
│   ├── src/main/java/.../costmonitor/
│   │   ├── api/                  # REST controllers
│   │   ├── application/          # application services
│   │   ├── config/
│   │   ├── domain/               # entities + repositories
│   │   │   ├── company/
│   │   │   ├── material/
│   │   │   ├── external/
│   │   │   ├── mapping/
│   │   │   └── price/
│   │   ├── infrastructure/       # parsers, external integrations
│   │   │   └── price/
│   │   └── tenant/
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/
└── build.gradle.kts
```

## Быстрый старт

### Требования

- JDK 21+
- Docker (для PostgreSQL / Testcontainers)
- Gradle 8.10+ (или используйте wrapper после генерации)

### Локальная БД

```bash
docker run -d --name cost-monitor-db \
  -e POSTGRES_DB=cost_monitor \
  -e POSTGRES_USER=cost_monitor \
  -e POSTGRES_PASSWORD=cost_monitor \
  -p 5432:5432 postgres:16
```

### Запуск

```bash
# из корня репозитория
./gradlew :app:bootRun
```

Приложение: http://localhost:8080  
Ping: http://localhost:8080/api/v1/ping

### Тесты

```bash
./gradlew :app:test
```

Тесты используют Testcontainers (нужен Docker).

## Gradle Wrapper

Если wrapper ещё не сгенерирован:

```bash
gradle wrapper --gradle-version 8.10.2
```

## Дальнейшие шаги

1. Реализовать полноценный парсер LemanaPro (Playwright / обход защиты)
2. Сервис fuzzy-matching материалов
3. Сущности ConstructionObject + расход из 1С
4. Cost Calculation Engine
5. Frontend (React)
6. Spring Security + JWT

## Источник цен

На данный момент ориентируемся **только на парсер** сайта https://kazan.lemanapro.ru/  
(B2B API Лемана ПРО отложен).
