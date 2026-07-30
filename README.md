# QMarket

Онлайн-магазин на **Kotlin Multiplatform + Spring Boot 4.1** (модульный монолит).

План и версии: [docs/ROADMAP.md](docs/ROADMAP.md)

## Текущее состояние (WIP, не релиз)

| Область | Статус |
|--------|--------|
| Auth (JWT) | ✅ |
| Catalog | ✅ |
| Cart | ✅ |
| Orders (checkout from cart) | ✅ (проверить) |
| Seed admin + demo catalog | ✅ |
| Unit / integration tests | ✅ (incl. order IT) |
| ktlint, JaCoCo, Swagger Authorize | ✅ |
| Payments | ❌ |
| Shared KMP DTOs / Clients UI | ⚠️ skeleton |
| Flyway SoT | ⚠️ ddl-auto=update |

## Quick start

```bash
docker compose up -d
./gradlew :server:bootRun
```

- Swagger: http://localhost:8080/swagger-ui.html (Authorize = JWT accessToken)
- Admin: `admin@qmarket.local` / `admin123`

### Orders API

```
POST   /api/v1/orders                 # создать из корзины { shippingAddress, customerNote? }
GET    /api/v1/orders?page=0&size=20  # мои заказы (без sort — см. fix Swagger)
GET    /api/v1/orders/{id}
POST   /api/v1/orders/{id}/cancel
GET    /api/v1/orders/admin/all       # ADMIN/MANAGER
GET    /api/v1/orders/admin/{id}
PUT    /api/v1/orders/admin/{id}/status  { "status": "CONFIRMED" }
```

Статусы: `PENDING | CONFIRMED | PAID | SHIPPED | DELIVERED | CANCELLED`

### Тесты

```bash
./gradlew :server:common:test :server:identity:test :server:catalog:test :server:cart:test :server:order:test
./gradlew :server:test
./gradlew :server:ktlintCheck
```
