# QMarket — план и версии

Источник: согласованный план разработки. Релиз версии — только после закрытия scope, зелёных тестов и подтверждения.

## Версии

| Версия | Фокус | Статус |
|--------|--------|--------|
| **v0.1 Foundation** | Инфра, auth, catalog, cart, orders (backend), tests, tooling | **WIP** |
| **v0.2 Catalog & Auth polish** | OAuth optional, профиль, фильтры catalog | pending |
| **v0.3 Cart & Checkout** | (cart+orders уже в v0.1 backend) UI checkout на клиентах | pending |
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
- [x] Unit + controller slice + integration tests (order: unit; IT — в планах)
- [x] ktlint, JaCoCo, Docker Compose, Swagger JWT Authorize
- [x] Order integration tests
- [ ] Flyway as schema source of truth (сейчас `ddl-auto=update`; V2 cart/orders SQL ready)
- [ ] Payments
- [ ] Shared KMP DTOs / client UI beyond skeleton

## v1.0 MVP (из плана)

Покупатель: регистрация, каталог, корзина, заказ, оплата (мок + 1 провайдер), подтверждение.  
Админ: товары, заказы, пользователи.  
Платформы: Android + Web (+ Desktop); iOS параллельно.

## Архитектура

Монолит → модульный монолит (сейчас) → микросервисы по нагрузке (v2.0+).

## Правило релиза

Версия не считается выпущенной, пока не закрыт согласованный scope, не зелёные тесты, не обновлена документация и нет явного подтверждения.
