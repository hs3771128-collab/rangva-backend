# Rangva — free deployment guide

Yeh guide store ko **bilkul free** stack par live karti hai:

| Part | Service | Free URL |
| --- | --- | --- |
| Frontend (React) | Netlify | `https://<site-name>.netlify.app` |
| Backend (Spring Boot) | Render (free web service, Docker) | `https://<service-name>.onrender.com` |
| Database (PostgreSQL) | Neon (free tier) | internal host |
| Images | backend container (`images/`) | `https://<render-url>/images/...` |

Koi credit card nahi lagta. Render ki free service 15 minute idle ke baad sleep hoti hai, is liye pehla request ~30–60 second le sakta hai (neeche keep-alive step dekhein).

---

## 0. Pehle yeh files push karein

Deployment files repo me already hain:

- backend: `Dockerfile`, `.dockerignore`, `render.yaml`
- frontend: `netlify.toml`

```bash
git add -A
git commit -m "chore: free deployment config"
git push
```

---

## 1. Neon (database) — 5 minute

1. <https://neon.tech> par GitHub se sign up karein.
2. **New Project** → name `rangva`, region **AWS ap-southeast-1 (Singapore)** (Pakistan ke sab se qareeb), Postgres version default.
3. Dashboard se **Connection string** copy karein. Do shakal milti hain:
   - `postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require`
   - JDBC ke liye: `jdbc:postgresql://HOST/DBNAME?sslmode=require`
4. Host me `-pooler` na ho (direct connection) — Hibernate ke liye yeh zyada reliable hai.

### 1a. Purana data migrate karein (recommended)

Aap ke store ka catalogue/orders live local Postgres me hai. Us ka dump pehle hi bana diya gaya hai:

```
E:\bacha project\_deploy\rangva-live-dump.sql
```

Neon par restore (PowerShell, local `psql` se):

```powershell
$env:PGPASSWORD='<NEON_PASSWORD>'
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' `
  -h <NEON_HOST> -U <NEON_USER> -d <NEON_DB> `
  -f 'E:\bacha project\_deploy\rangva-live-dump.sql'
```

`<NEON_USER>` usually `neondb_owner` hota hai aur `<NEON_DB>` usually `neondb`.

> **Fresh start chahiye?** Restore skip karein aur Render par pehli baar `APP_SEED_ENABLED=true` set karein (43 demo products aa jayenge), phir usay wapas `false` kar dein.

### 1b. Demo accounts ke roles hata dein

Purane dump me `user1`, `seller1`, `admin` accounts maujood hain. `admin` ka password Render step me rotate ho jayega. `user1` / `seller1` ke well-known passwords (README me hain) hataane ke liye:

```sql
DELETE FROM user_role
WHERE user_id IN (SELECT user_id FROM users WHERE username IN ('user1', 'seller1'));
```

Isay Neon ke **SQL Editor** me chala dein. (Users delete nahi kiye kyunke unke reviews FK se juda hain; roles na hone se wo kisi protected endpoint tak nahi pohanch sakte.)

> Dump me ek real pending order (`#1`, `mushadisadat@gmail.com`, `PENDING_PAYMENT`) bhi hai. Payment gateway abhi configure nahi, is liye woh pending rahega — customer ko WhatsApp/email se COD me convert kar lein ya order cancel kar dein.

---

## 2. Render (backend) — 10 minute

1. <https://render.com> par GitHub se sign up karein.
2. **New +** → **Blueprint** → repository `hs3771128-collab/rangva-backend` select karein → **Apply**.
   Render `render.yaml` khud parh lega (Docker, free plan, Singapore, health check).
3. Blueprint apply karte waqt yeh values poochi jayengi (`sync: false`):

| Variable | Value |
| --- | --- |
| `PG_DB_URL` | `jdbc:postgresql://<NEON_HOST>/<NEON_DB>?sslmode=require` |
| `PG_DB_USERNAME` | Neon user (e.g. `neondb_owner`) |
| `PG_DB_PASSWORD` | Neon password |
| `APP_CORS_ALLOWED_ORIGINS` | filhal `https://rangva.netlify.app` — Netlify ke baad asal URL daalein |
| `APP_SEED_ADMIN_PASSWORD` | koi **strong** password (e.g. `Rangva!Admin#2026`) |

Baqi sab `render.yaml` se khud set ho jata hai: JWT secret (random), `AUTH_COOKIE_SAME_SITE=None`, `AUTH_COOKIE_SECURE=true`, demo users off, SQL logging off.

4. Deploy hone dein. Logs me yeh dikhna chahiye:

```
Tomcat started on port 10000 (http)
Started SbEcomApplication
```

5. Service ka URL copy karein, e.g. `https://rangva-api.onrender.com`.
   - Agar Render ne naam ke aage suffix lagaya (e.g. `rangva-api-7x2k`), to **Environment** me `IMAGE_BASE_URL` bhi update karein: `https://<actual-host>/images`.
6. Health check browser me khol kar test karein:

```
https://<render-host>/api/public/payments/methods
```

`{"jazzcash":false,"easypaisa":false,"safepay":false,"testMode":false}` aana chahiye.

### 2a. Admin password rotate hone ke baad

Pehli successful boot par log me yeh aayega:

```
Admin password rotated from APP_SEED_ADMIN_PASSWORD. Remove that environment variable now.
```

**Environment** se `APP_SEED_ADMIN_PASSWORD` delete kar dein aur **Save** karein. (Password DB me hash ho chuka hai; env var ab zaroori nahi.)

---

## 3. Netlify (frontend) — 5 minute

1. <https://netlify.com> par GitHub se sign up karein.
2. **Add new site** → **Import an existing project** → GitHub → `hs3771128-collab/rangva-frontend`.
3. Build settings (auto detect ho jate hain, `netlify.toml` bhi maujood hai):
   - Build command: `npm run build`
   - Publish directory: `dist`
4. **Site configuration → Environment variables** me:

| Key | Value |
| --- | --- |
| `VITE_BACK_END_URL` | `https://<render-host>` (bina trailing slash) |

> `netlify.toml` me default `https://rangva-api.onrender.com` likha hai. Agar aap ka Render host alag hai to environment variable us value ko override kar dega.

5. **Deploy site** → **Site configuration → Change site name** se `rangva` try karein (agar free ho). URL milega: `https://rangva.netlify.app`.

---

## 4. Dono ko aapas me connect karein — 2 minute

Render → **Environment**:

| Variable | Value |
| --- | --- |
| `APP_CORS_ALLOWED_ORIGINS` | `https://<netlify-site>.netlify.app` (exact, trailing slash ke baghair) |

**Save changes** → Render service restart hogi (~1 min).

Ab Netlify URL khol kar test karein:

1. Products load ho rahe hain?
2. Signup/login ho raha hai?
3. Cart → checkout → address → payment → review → place order?
4. Footer → **Track my order** se tracking chal rahi hai?

---

## 5. Render ko jagaye rakhein (optional, recommended)

Free service sleep hoti hai. <https://uptimerobot.com> (free) me HTTP monitor banayein:

- URL: `https://<render-host>/api/public/payments/methods`
- Interval: 5 minutes

Is se zyada tar waqt service awake rehti hai aur customers ko cold-start nahi milta.

---

## 6. Payment gateways (baad me, credentials milne par)

Render → Environment me add karein, phir service restart:

```env
PAYMENTS_BACKEND_BASE_URL=https://<render-host>
PAYMENTS_FRONTEND_BASE_URL=https://<netlify-site>.netlify.app
PAYMENTS_FRONTEND_RETURN_URL=https://<netlify-site>.netlify.app/order-confirm

JAZZCASH_ENABLED=true
JAZZCASH_MERCHANT_ID=...
JAZZCASH_PASSWORD=...
JAZZCASH_INTEGRITY_SALT=...
JAZZCASH_SANDBOX=false

EASYPAISA_ENABLED=true
EASYPAISA_STORE_ID=...
EASYPAISA_HASH_KEY=...
EASYPAISA_EMAIL=...
EASYPAISA_SANDBOX=false

SAFEPAY_ENABLED=true
SAFEPAY_API_KEY=...
SAFEPAY_SECRET=...
SAFEPAY_WEBHOOK_SECRET=...
SAFEPAY_SANDBOX=false
```

Safepay dashboard me webhook URL: `https://<render-host>/api/payments/safepay/webhook`
(events: payment succeeded + payment failed).

`PAYMENTS_TEST_MODE` ko production me **kabhi true na** karein — app prod profile ke sath start hi nahi hogi.

---

## 7. Free tier ki limits (jaan lena zaroori)

| Cheez | Free limit | Asar |
| --- | --- | --- |
| Render web service | 512 MB RAM, 15 min idle ke baad sleep | pehla request slow; UptimeRobot se fix |
| Render disk | ephemeral | **admin panel se upload ki gayi nayi images redeploy par delete ho jati hain**. Repo wali 79 images safe hain. Bar-bar upload karna ho to Cloudinary (free) integrate karna hoga. |
| Neon | 0.5 GB storage | catalogue ke liye kaafi |
| Netlify | 100 GB bandwidth/month | kaafi |

---

## 8. Troubleshooting

| Masla | Wajah / fix |
| --- | --- |
| Site par products nahi aate, console me CORS error | `APP_CORS_ALLOWED_ORIGINS` me Netlify ka exact URL (https, no trailing slash) |
| Login ke baad dobara logout ho jata hai | `VITE_BACK_END_URL` galat host; Netlify env update kar ke **redeploy** karein |
| Images broken | `IMAGE_BASE_URL` Render host se match nahi kar raha |
| `502` / `Application failed to respond` | Render cold start — 60 sec wait, ya UptimeRobot |
| Netlify par `/track-order` refresh par 404 | `netlify.toml` ka `/* → /index.html 200` redirect missing (file committed hai) |
| Render build fail: `Could not resolve dependencies` | dobara deploy karein; build cache/network ka temporary issue |
| Admin login nahi ho raha | `APP_SEED_ADMIN_PASSWORD` wali value try karein; logs me "Admin password rotated" check karein |

---

## 9. Custom domain (optional)

Netlify → **Domain management** me apna domain add karein, phir Render ki `APP_CORS_ALLOWED_ORIGINS` me woh domain bhi comma se add kar dein:

```
https://rangva.pk,https://www.rangva.pk,https://rangva.netlify.app
```

Free domain chahiye to `*.netlify.app` hi rakhein — woh permanent free hai.

---

## 10. Foran public link (bina koi signup — temporary)

Agar abhi, isi waqt, kisi ko link dena ho to Cloudflare quick tunnel use karein. Is me koi account nahi banta aur URL `https://<random>.trycloudflare.com` hota hai.

Setup (repo ke bahar, sirf machine par):

```
E:\bacha project\_deploy\live-tunnel.ps1      # backend + bridge + tunnel
E:\bacha project\_deploy\live\server.js       # storefront + /api ek hi origin par
```

Chalane ke liye:

```powershell
powershell -ExecutionPolicy Bypass -File "E:\bacha project\_deploy\live-tunnel.ps1"
```

Yeh script backend (8080) start karta hai, frontend ko **same-origin mode** me build karta hai (`VITE_BACK_END_URL=` khaali → `/api` relative), phir Node bridge (4173) aur tunnel chalata hai.

| Faida | Nuqsan |
| --- | --- |
| 1 minute me live, koi signup nahi | PC band/sleep hua to site band |
| Same origin → CORS/cookie ka masla nahi | Har restart par URL badal jata hai |
| Asli DB ka data | Payment gateway callbacks ke liye theek nahi |

Permanent solution ke liye section 1–4 follow karein (Neon + Render + Netlify).
