# QMarket

Онлайн-магазин на **Kotlin Multiplatform + Spring Boot 4.1** (модульный монолит).

План и версии: [docs/ROADMAP.md](docs/ROADMAP.md)

## Текущее состояние (WIP, не релиз v0.1)

| Область | Статус |
|--------|--------|
| Auth (JWT) | ✅ |
| Catalog | ✅ |
| Cart | ✅ |
| Orders (checkout from cart) | ✅ |
| Seed admin + demo catalog | ✅ |
| Unit / controller / integration tests | ✅ |
| ktlint, JaCoCo, Swagger Authorize | ✅ |
| Flyway SoT (`spring-boot-starter-flyway`) | ✅ V1+V2, app: `validate`, tests: Flyway |
| Payments (mock) | ✅ POST /api/v1/orders/{id}/pay |
| Shared KMP DTOs / Clients UI | ⚠️ skeleton |

## Quick start

```bash
# При смене схемы (один раз):
# docker compose down -v && docker compose up -d

docker compose up -d
./gradlew :server:bootRun
```

- Swagger: http://localhost:8080/swagger-ui.html (Authorize = JWT accessToken)
- Admin: `admin@qmarket.local` / `admin123`

### Orders API

```
POST   /api/v1/orders                 # из корзины { shippingAddress, customerNote? }
GET    /api/v1/orders?page=0&size=20  # без sort (Swagger sort=string ломал JPA)
GET    /api/v1/orders/{id}
POST   /api/v1/orders/{id}/cancel
POST   /api/v1/orders/{id}/pay     # mock payment → PAID
GET    /api/v1/orders/admin/all
GET    /api/v1/orders/admin/{id}
PUT    /api/v1/orders/admin/{id}/status  { "status": "CONFIRMED" }
```

Статусы: `PENDING | CONFIRMED | PAID | SHIPPED | DELIVERED | CANCELLED`

### Тесты и качество (всегда по всему проекту)

```bash
./gradlew test ktlintCheck --parallel
```

Точечно только для отладки: `./gradlew :server:order:test`
