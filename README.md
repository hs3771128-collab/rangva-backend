# Rangva — Backend API

Spring Boot REST API for **Rangva**, a Pakistani hybrid fashion + beauty store. Handles catalogue, variants, reviews, wishlist, cart, Pakistani addresses and cash-on-delivery / local payment orders.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/hs3771128-collab/rangva-backend)

> Free live deployment (Neon + Render + Netlify) ki step-by-step guide: **[DEPLOY.md](DEPLOY.md)**. Dockerfile aur `render.yaml` repo me tayyar hain.

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
| `PORT` | `8080` | HTTP port (Render sets this automatically) |
| `PG_DB_URL` | `jdbc:postgresql://localhost:5432/ecommerce` | JDBC URL |
| `PG_DB_USERNAME` | `postgres` | DB user |
| `PG_DB_PASSWORD` | `12345` | DB password |
| `IMAGE_BASE_URL` | `http://localhost:8080/images` | Prefix for stored image filenames |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Comma-separated browser origins allowed to call the API |
| `AUTH_COOKIE_SAME_SITE` | `Lax` | Set `None` when the storefront and API are on different domains |
| `AUTH_COOKIE_SECURE` | `false` | Set `true` with `SameSite=None` on HTTPS |
| `APP_SEED_DEMO_USERS` | `true` | Local demo accounts (`user1`, `seller1`, `admin`); set `false` in production |
| `APP_SEED_ADMIN_PASSWORD` | *(empty)* | One-time admin password create/rotate; remove after the first boot |
| `JPA_SHOW_SQL` / `JPA_FORMAT_SQL` | `true` | Disable on a public deployment |
| `PAYMENTS_BACKEND_BASE_URL` | `http://localhost:8080` | **Public** backend URL used for gateway callbacks |
| `PAYMENTS_FRONTEND_RETURN_URL` | `http://localhost:5173/order-confirm` | Where the gateway sends the customer back |

```bash
# Windows
.\mvnw.cmd spring-boot:run
# macOS / Linux
./mvnw spring-boot:run
```

API: `http://localhost:8080` · Swagger: `http://localhost:8080/swagger-ui.html`

### 3. Data & seeding

The PostgreSQL database is the **single source of truth** — the app never inserts or resets catalogue data on startup. Products, categories, variants, reviews and orders are managed dynamically through the admin panel (`/admin`).

A one-time demo catalogue lives in `config/DataSeeder.java` but is **disabled by default**:

```properties
app.seed.enabled=false
```

To populate a brand new (empty) database once:

```bash
# Windows
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--app.seed.enabled=true"
# macOS / Linux
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.seed.enabled=true"
```

Then set it back to `false` (or just restart normally). Even when enabled, the seeder exits immediately if any product already exists, so it can never duplicate or overwrite your data.

The demo catalogue (when enabled) creates:

- 8 categories (fashion + beauty)
- ~34 products with Pakistani pricing (PKR), brands (Rangva Studio, Sadaa, Naya, Zaitoon, Kiraan, Meher) and departments (`FASHION` / `BEAUTY`)
- variants (`SIZE`, `SHADE`, `VOLUME`, `ONE`) with per-option stock
- 12 sample reviews

Default accounts: `admin` / `adminPass`, plus `user1` / `password1` and `seller1` / `password2`.

### 4. Payment gateways (JazzCash + Easypaisa + Safepay cards)

Online payments use **hosted checkout** pages: the store never receives card or wallet credentials. Credentials are read from environment variables, so nothing secret is committed to git. A gateway is only offered at checkout when it is enabled *and* its credentials are set.

```properties
# JazzCash merchant form
JAZZCASH_ENABLED=true
JAZZCASH_SANDBOX=false          # true while testing
JAZZCASH_MERCHANT_ID=...
JAZZCASH_PASSWORD=...
JAZZCASH_INTEGRITY_SALT=...

# Easypaisa Easypay
EASYPAISA_ENABLED=true
EASYPAISA_SANDBOX=false         # true while testing
EASYPAISA_STORE_ID=...
EASYPAISA_HASH_KEY=...
EASYPAISA_PAYMENT_METHOD=MA     # MA (mobile account), OTC, CC
EASYPAISA_EMAIL=...

# Safepay cards (Visa / Mastercard)
SAFEPAY_ENABLED=true
SAFEPAY_SANDBOX=false           # true while testing
SAFEPAY_API_KEY=...
SAFEPAY_SECRET=...
SAFEPAY_WEBHOOK_SECRET=...      # signs the X-SFPY-SIGNATURE header

# Gateway callbacks must reach this backend over the public internet
PAYMENTS_BACKEND_BASE_URL=https://api.your-domain.pk
PAYMENTS_FRONTEND_RETURN_URL=https://your-domain.pk/order-confirm
```

How the flow works:

1. The customer places an order. It is saved as `PENDING_PAYMENT` with payment `PENDING` — **nothing is marked paid yet**.
2. `POST /api/payments/{jazzcash|easypaisa|safepay}/init` starts the payment. JazzCash/Easypaisa return a **signed form** the storefront auto-submits; Safepay returns a checkout URL (a tracker is stored on the payment).
3. The customer pays on the gateway's own page.
4. The result comes back through `/api/payments/{gateway}/callback` (JazzCash, Easypaisa) or through Safepay's webhook plus the `verify` call the confirmation page makes after the redirect.
5. Only after the signature, amount and order checks pass does the order move to `CONFIRMED` / payment `PAID`. A failed or tampered result leaves it unpaid (`PAYMENT_FAILED`) so the customer can retry from the confirmation page.

JazzCash callbacks are verified with `pp_SecureHash` (HMAC-SHA256 over the integrity salt and the sorted `pp_*` values). Easypaisa requests use `merchantHashedReq` (HMAC-MD5, Base64) and are matched on `storeId`, `orderRefNum` and amount. Safepay webhooks must carry `X-SFPY-SIGNATURE` = `Base64(HMAC-SHA256(raw body, SAFEPAY_WEBHOOK_SECRET))`; the tracker is then matched to the stored payment. Safepay payments are also re-checked server-to-server through `GET /order/v1/authorized` when the customer returns.

> While developing on localhost, point `PAYMENTS_BACKEND_BASE_URL` at a tunnel (for example `ngrok http 8080`) and register the callback/webhook URLs in the gateway portals. In Safepay, register `https://<backend>/api/payments/safepay/webhook` for the *payment succeeded* and *payment failed* events. Safepay's checkout shows Visa, Mastercard and PayPak; the scheme has to be enabled for your merchant account (ask Safepay support), otherwise only the international cards appear.

#### Test mode (no credentials needed)

Merchant accounts take time to approve, but the checkout flow can already be exercised end to end with **test mode**:

```properties
PAYMENTS_TEST_MODE=true          # development only, never in production
PAYMENTS_FRONTEND_BASE_URL=http://localhost:5173
```

With test mode on:

- every gateway is reported as available (`GET /api/public/payments/methods` returns `testMode: true`),
- `POST /api/payments/{gateway}/init` returns `http://localhost:5173/mock-gateway?...` instead of calling the real gateway,
- the storefront's test page posts to `POST /api/payments/mock/complete` with `{orderId, gateway, success}` and the order moves to `CONFIRMED`/`PAID` or `PAYMENT_FAILED` exactly like a real callback,
- **no real money moves**. `PAYMENTS_TEST_MODE` cannot be left on by accident: the app refuses to start when it is combined with a `prod`/`production` profile.

To accept real payments, set the gateway's `*_ENABLED` flag and its credentials, then remove `PAYMENTS_TEST_MODE`.

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
| POST | `/api/public/orders/track` | Public order tracking: `{orderId, contact}` where `contact` is the checkout email or phone. Wrong order/contact returns the same generic 400 so ids cannot be enumerated |

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
| POST | `/api/order/users/payments/{paymentMethod}` | Place order. `CASH_ON_DELIVERY`, `JAZZCASH`, `EASYPAISA`, `SAFEPAY`, `BANK_TRANSFER` |
| GET | `/api/order/users/{orderId}` | A customer's own order (used after a gateway redirect) |
| POST | `/api/payments/{gateway}/init` | Start/retry an online payment; returns the signed form or checkout URL |
| POST | `/api/payments/jazzcash/callback` | JazzCash result (signature-verified, public) |
| POST | `/api/payments/easypaisa/callback` | Easypaisa result (verified, public) |
| POST | `/api/payments/safepay/webhook` | Safepay webhook (`X-SFPY-SIGNATURE` verified, public) |
| POST | `/api/payments/safepay/verify` | Re-checks a Safepay tracker after the redirect (authenticated) |
| POST | `/api/payments/mock/complete` | Test mode only: approves/fails a simulated payment (authenticated) |
| GET | `/api/public/payments/methods` | Which gateways are configured |
| GET | `/api/admin/orders` | Admin order list |
| GET | `/api/seller/orders` | Seller order list |
| PUT | `/api/admin/orders/{orderId}/status` | Update status |

COD orders are created as `PLACED`; online and bank-transfer orders as `PENDING_PAYMENT` until the payment is verified (then `CONFIRMED`, or `PAYMENT_FAILED` when the gateway reports a failure). Variant stock is decremented on order placement.

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
