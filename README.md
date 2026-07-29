# QMarket

Онлайн-магазин на **Kotlin Multiplatform + Spring Boot 4.1** (модульный монолит).

## Текущее состояние (WIP, не релиз)

| Область | Статус |
|--------|--------|
| Auth (register / login / refresh, JWT) | ✅ |
| Catalog (categories, products, search) | ✅ |
| Cart (get / add / update / remove / clear) | ✅ |
| Seed: admin + демо-каталог | ✅ |
| Unit / controller / integration tests | ✅ |
| ktlint, JaCoCo | ✅ |
| Docker Compose (PostgreSQL) | ✅ |
| Swagger UI + **Authorize (JWT)** | ✅ |
| Orders, Payments | ❌ |
| Shared KMP DTOs ↔ API | ❌ |
| Клиенты (Android/iOS/Web/Desktop) | ⚠️ skeleton стартера — не удалять пока |
| Flyway как SoT схемы | ⚠️ скрипт есть, пока `ddl-auto=update` |

## Quick start

```bash
docker compose up -d
./gradlew :server:bootRun
```

| | |
|--|--|
| Swagger | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

### Admin

- `admin@qmarket.local` / `admin123`

### Swagger: как проверить защищённые API

1. `POST /api/v1/auth/login` → в ответе `accessToken`
2. Кнопка **Authorize** (справа сверху)
3. Вставить **только токен** (без слова `Bearer`)
4. Authorize → дальше Try it out на `/api/v1/cart` и т.д.

### Cart API (нужен JWT)

```
GET    /api/v1/cart
POST   /api/v1/cart/items          { "productId": "...", "quantity": 1 }
PUT    /api/v1/cart/items/{productId}  { "quantity": 2 }
DELETE /api/v1/cart/items/{productId}
DELETE /api/v1/cart
```

## Как проверяют API в профессиональной разработке

Ручной Postman — только для разового исследования. Основа:

| Уровень | Что | У нас |
|--------|-----|--------|
| Unit | сервисы, JWT, без HTTP | `*ServiceTest`, `JwtServiceTest` |
| Controller slice | HTTP + mocked service | `AuthControllerTest`, `ProductControllerTest` |
| Integration | полный стек + БД (Testcontainers) | `AuthIntegrationTest`, `CatalogIntegrationTest`, `CartIntegrationTest` |
| CI | те же тесты на каждый PR | позже |
| Exploratory | Swagger Authorize / иногда Postman | Swagger UI |

Запуск регрессии API (нужен Docker для integration):

```bash
./gradlew :server:common:test :server:identity:test :server:catalog:test :server:cart:test
./gradlew :server:test
./gradlew :server:ktlintCheck
```

Не нужно вручную прогонять все сценарии в Postman перед каждым шагом — integration-тесты это закрывают. Swagger — для быстрой проверки нового эндпоинта.

## Структура

```
server/
  common/     JWT, Security, exceptions
  identity/   Auth
  catalog/    Categories, products
  cart/       Shopping cart
app/          KMP clients (skeleton стартера — оставляем)
```

## Файлы стартера (Android/iOS/Web/Desktop)

Пока **не удаляем**: это база клиентских модулей. Удалим/заменим только когда начнём реальную UI-интеграцию с API, если что-то окажется лишним.
