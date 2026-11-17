# Rangva — Backend API

Spring Boot REST API for **Rangva**, a Pakistani hybrid fashion + beauty store. Handles catalogue, variants, reviews, wishlist, cart, Pakistani addresses and cash-on-delivery / local payment orders.

## Tech stack

| Tool | Version / purpose |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.5.0 (Web, Data JPA, Security, Validation) |
| PostgreSQL | 17 |
| JWT | jjwt 0.12.6, auth cookie `springBootEcom` |
| Lombok | Boilerplate reduction |
| springdoc-openapi | Swagger UI at `/swagger-ui.html` |
| Maven | `mvnw` wrapper included |

## Getting started

### 1. Database

```sql
CREATE DATABASE ecommerce;
```

Hibernate creates/updates the schema automatically (`ddl-auto=update`).

### 2. Configuration

Defaults live in `src/main/resources/application.properties`, and can be overridden with environment variables:

| Variable | Default | Meaning |
| --- | --- | --- |
| `PG_DB_URL` | `jdbc:postgresql://localhost:5432/ecommerce` | JDBC URL |
| `PG_DB_USERNAME` | `postgres` | DB user |
| `PG_DB_PASSWORD` | `12345` | DB password |
| `IMAGE_BASE_URL` | `http://localhost:8080/images` | Prefix for stored image filenames |

```bash
# Windows
.\mvnw.cmd spring-boot:run
# macOS / Linux
./mvnw spring-boot:run
```

API: `http://localhost:8080` · Swagger: `http://localhost:8080/swagger-ui.html`

### 3. Seed data

`config/DataSeeder.java` runs on startup **only when the products table is empty**. It creates:

- 8 categories (fashion + beauty)
- ~34 products with Pakistani pricing (PKR), brands (Rangva Studio, Sadaa, Naya, Zaitoon, Kiraan, Meher) and departments (`FASHION` / `BEAUTY`)
- variants (`SIZE`, `SHADE`, `VOLUME`, `ONE`) with per-option stock
- 12 sample reviews

Default accounts: `admin` / `adminPass`, plus `user1` / `password1` and `seller1` / `password2`.

## Domain model

| Entity | Notes |
| --- | --- |
| `Product` | `brand`, `department`, category, price, discount, `specialPrice`, stock, image |
| `ProductVariant` | `label`, `variantType`, `stock`, `extraPrice`, `sortOrder` |
| `Review` | 1 per user per product (`unique(product_id, user_id)`), rating 1–5, verified flag |
| `WishlistItem` | per user + product |
| `CartItem` | stores the chosen `variantLabel`; one line per product per cart |
| `OrderItem` | snapshots price, discount and `variantLabel` |
| `Address` | Pakistani: `phone`, `street`, `area`, `buildingName`, `city`, `state` (province), `pincode`, country defaults to Pakistan |

Prices are stored as plain PKR numbers. Delivery rules are applied server-side and mirrored on the frontend:

| Subtotal | Delivery fee |
| --- | --- |
| ≥ Rs. 3,000 | Free |
| < Rs. 3,000 | Rs. 199 |

## API overview

### Public

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/public/products` | Filter + paginate (`keyword`, `category`, `department`, `brand`, `minPrice`, `maxPrice`, `size`, `pageNumber`, `pageSize`, `sortBy`, `sortOrder`) |
| GET | `/api/public/products/{productId}` | Product detail with variants and rating summary |
| GET | `/api/public/products/{productId}/reviews` | Product reviews |
| GET | `/api/public/categories` | Categories |
| GET | `/api/public/categories/{categoryId}/products` | Products by category |
| GET | `/api/public/products/keyword/{keyword}` | Keyword search |

### Auth

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/auth/signup` | Register |
| POST | `/api/auth/signin` | Login (sets JWT cookie) |
| POST | `/api/auth/signout` | Logout |
| GET | `/api/auth/user` | Current user |
| GET | `/api/auth/username` | Current username |
| GET | `/api/auth/sellers` | Sellers list |

### Cart

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/cart/create` | Create cart for user |
| POST | `/api/carts/products/{productId}/quantity/{qty}?variant=` | Add item (variant optional) |
| GET | `/api/carts/users/cart` | Current user cart + total |
| PUT | `/api/cart/products/{productId}/quantity/{operation}` | Increase / decrease |
| DELETE | `/api/carts/{cartId}/product/{productId}` | Remove line |

### Wishlist & reviews

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/wishlist` | List saved products |
| POST | `/api/wishlist/{productId}` | Toggle / add |
| DELETE | `/api/wishlist/{productId}` | Remove |
| POST | `/api/products/{productId}/reviews` | Submit review |

### Orders

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/order/users/payments/{paymentMethod}` | Place order. `CASH_ON_DELIVERY`, `JAZZCASH`, `EASYPAISA`, `BANK_TRANSFER`, `CARD` |
| GET | `/api/admin/orders` | Admin order list |
| GET | `/api/seller/orders` | Seller order list |
| PUT | `/api/admin/orders/{orderId}/status` | Update status |

COD orders are created as `PLACED`; prepaid methods as `CONFIRMED`. Variant stock is decremented on order placement.

### Addresses

`POST /api/addresses`, `GET /api/users/addresses`, `GET /api/addresses/{id}`, `PUT /api/addresses/{id}`, `DELETE /api/addresses/{id}`.

## Project structure

```
src/main/java/com/ecommerce/project/
  config/        App constants, DataSeeder
  controller/    REST controllers
  model/         JPA entities
  payload/       DTOs and response wrappers
  repository/    Spring Data repositories
  security/      JWT, auth filters, security config
  service/       Business logic (+Impl)
  util/          AuthUtil and helpers

images/          Product photos served at /images/**
```

## Notes

- Product images are stored as bare filenames (e.g. `u_1490481651871-ab68de25d43d.jpg`); URLs are built from `image.base.url` at read time.
- Categories require at least 3 characters, so short names like `Men` are valid.
- No third-party international payment gateway is used — checkout is fully local.
