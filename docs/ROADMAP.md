# QMarket — план и версии

Источник: согласованный план разработки. Релиз версии — только после закрытия scope, зелёных тестов и подтверждения.

## Версии

| Версия | Фокус | Статус |
|--------|--------|--------|
| **v0.1 Foundation** | Backend + shared client shopper flow (auth→checkout→orders) | **WIP** (почти закрыт scope) |
| **v0.2 Catalog & Auth polish** | OAuth optional, admin catalog UX, rate limit login | pending |
| **v0.3** | (merged into v0.1 client) reserved / skip | done via shared UI |
| **v1.0 MVP** | Payments (1 провайдер), notifications, admin UI, search | pending |
| **v1.1 Growth** | Recommendations, reviews, promos, delivery integrations | pending |
| **v1.2 Scale** | Observability, cache, performance | pending |
| **v2.0** | Microservices evolution by need | pending |

## v0.1 Foundation — чеклист backend (факт)

- [x] Modular monolith Spring Boot 4.1 (`common`, `identity`, `catalog`, `cart`, `order`)
- [x] JWT auth register/login/refresh
- [x] Catalog CRUD + search + seed
- [x] Cart API
- [x] Orders: create from cart, list, cancel, admin status
- [x] Unit + controller slice + integration tests (incl. order IT)
- [x] ktlint, JaCoCo, Docker Compose, Swagger JWT Authorize
- [x] Flyway SoT via `spring-boot-starter-flyway` (V1+V2, validate)
- [x] Payments (mock only; real PSP later)
- [x] Profile API (GET/PATCH /users/me)
- [x] Shipping addresses CRUD
- [ ] Shared KMP DTOs / client UI beyond skeleton

## v1.0 MVP (из плана)

Покупатель: регистрация, каталог, корзина, заказ, оплата (мок + 1 провайдер), подтверждение.  
Админ: товары, заказы, пользователи.  
Платформы: Android + Web (+ Desktop); iOS параллельно.

## Архитектура

Монолит → модульный монолит (сейчас) → микросервисы по нагрузке (v2.0+).

## Правило релиза

Версия не считается выпущенной, пока не закрыт согласованный scope, не зелёные тесты, не обновлена документация и нет явного подтверждения.
