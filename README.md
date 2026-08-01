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
| Profile (GET/PATCH /users/me) | ✅ |
| Shipping addresses | ✅ |
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

### Catalog filters

```
GET /api/v1/products?q=&categoryId=&featuredOnly=&minPrice=&maxPrice=&sortBy=price|name|createdAt&sortDir=asc|desc&page=&size=
```

### Addresses API

```
GET    /api/v1/users/me/addresses
POST   /api/v1/users/me/addresses
GET    /api/v1/users/me/addresses/{id}
PUT    /api/v1/users/me/addresses/{id}
DELETE /api/v1/users/me/addresses/{id}
```

Первый адрес или `default: true` становится адресом по умолчанию (один на пользователя).

### Profile API

```
GET    /api/v1/users/me
PATCH  /api/v1/users/me   { "firstName"?, "lastName"?, "phone"? }
POST   /api/v1/users/me/password  { "currentPassword", "newPassword" }  # 204
```

### Orders API

```
POST   /api/v1/orders                 # { shippingAddress? | addressId?, customerNote? }
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
