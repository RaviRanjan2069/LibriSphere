# LibriSphere - Multi-Vendor Bookstore E-Commerce Platform

## Overview
LibriSphere is a multi-vendor bookstore e-commerce application. Independent sellers list their books, readers browse and buy from many sellers with a single cart, and administrators approve sellers and monitor the platform.

The backend is a **Spring Boot REST API** backed by **MongoDB**; the frontend is a single-page app written in plain **HTML, CSS and JavaScript** (no framework, no template engine, no build step).

## Features
- Multi-vendor marketplace: sellers manage their own listings, stock and orders
- Buyer catalogue with search, categories and sorting; cart, checkout and order tracking
- Cancel a whole order or a single item before it ships, with stock returned and refunds shown
- Seller and admin dashboards with sales figures, order management and seller approval
- Secure accounts: email verification codes, sign-in with password or emailed code, "Keep me signed in"
- Forgot password by emailed reset link; Profile page for photo, details, email and password
- Separate Admin sign-in tab; administrators can create more administrator accounts
- Help & support requests, answered by admins, with replies emailed to the customer
- Emails sent through Brevo (or SMTP), with a development mode that needs no email account
- Light and dark themes, responsive from phone to desktop

## Tech Stack
- Java 21
- Spring Boot 3.3 (REST API)
- Spring Data MongoDB
- MongoDB
- HTML
- CSS
- JavaScript (ES modules)
- Brevo transactional email API
- Maven
- JUnit 5 and MockMvc tests

## Run the Project
Requires **Java 21** and a running **MongoDB** server on `localhost:27017` (on Windows, the "MongoDB" service).

```bash
mvn spring-boot:run
```

or with the included Maven wrapper:

```bash
mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run          # macOS / Linux
```

Open your browser:

http://localhost:9090

On first start the database is filled with demo sellers, books and orders. Without email settings the app runs in **development mode**: codes are printed in the console and filled in automatically (see the Email section below to send real emails).

To work on the frontend, edit the files in `src/main/resources/static/` and reload the browser. There is nothing to install or build.

## 🌐 Deployment (Vercel + Render + MongoDB Atlas)
Live site: **https://librisphere-bookstore.vercel.app**

Vercel can't run a Java server, so the app is split across three free services:

```
Browser ──▶ librisphere-bookstore.vercel.app
             ├─ HTML / CSS / JS            served by Vercel (src/main/resources/static)
             └─ /api/*, /media/*  ──proxy──▶ Render: Spring Boot in Docker (render.yaml, Dockerfile)
                                                └─▶ MongoDB Atlas
```
The browser only ever talks to the Vercel address, so sign-in cookies work as on a single site.

1. **MongoDB Atlas** (https://cloud.mongodb.com): create a free M0 cluster, a database user, and allow access from anywhere (`0.0.0.0/0`, Render has no fixed IP). Copy the connection string and add the database name: `mongodb+srv://USER:PASS@cluster0.xxxxx.mongodb.net/librisphere`.
2. **Render** (https://render.com): *New → Blueprint*, pick this GitHub repository; it reads `render.yaml`. Fill in:
   - `MONGODB_URI`: the Atlas connection string
   - `ADMIN_PASSWORD`: password for the `admin` account (the public demo password is never used in production)
   - `BREVO_API_KEY`: your Brevo key

   `OTP_SECRET` is generated automatically, and `APP_URL` (the public site address used in reset-link emails) is set in `render.yaml`. The service gets an address like `https://librisphere-api-ar00.onrender.com`; if yours differs, update the two `destination` URLs in `vercel.json`.
3. **Vercel** (https://vercel.com/new): import this GitHub repository. No build settings are needed; `vercel.json` serves `src/main/resources/static` and forwards `/api` and `/media` to Render.

Every push to `master` redeploys both. Production settings are in `src/main/resources/application-prod.properties`.

**Free-plan limits:** the Render server sleeps after 15 minutes without visits; the first visit after that takes about a minute while it wakes up. Profile photos are stored on Render's disk, which the free plan resets on every deploy or restart.

---

## 🔑 Demo Accounts
| Role | User id | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `admin123` | Sign in from the **Admin** tab (locally only; in production the password is `ADMIN_PASSWORD`) |
| **Seller A** | `vendor_a` | `vendor123` | Storefront: *Apex Books* (approved) |
| **Seller B** | `vendor_b` | `vendor123` | Storefront: *Beacon Bookshop* (approved) |
| **Seller C** | `vendor_c` | `vendor123` | Storefront: *Cosmos Reads* (waiting for admin approval) |
| **Buyer** | `buyer` | `buyer123` | Existing customer with order history |

> ⚠️ These passwords are public. Before putting the site online, sign in as each demo account and change its password from the Profile page, or create your own administrator from the Admin dashboard.

## 📋 Feature Details
**Buyers**
- Browse, search and filter the catalogue by category; sort by price, title or newest.
- Book pages with stock status, quantity picker and seller details.
- Cart (works before signing in), checkout, and an order confirmation page.
- My Orders with All / Active / Delivered / Cancelled tabs.

**Sellers**
- Dashboard with revenue, units sold, orders and low-stock alerts.
- Orders tabs (To ship / Shipped / Delivered / Cancelled / All); update status or cancel unshipped items.
- Add, edit and delete listings with a live cover preview.

**Administrators**
- Platform totals, recent orders with refunds, and approval of new seller accounts.
- Create more administrator accounts.
- Answer Help & support requests; the customer is emailed the reply.

**Accounts & security**
- Sign-up verifies the email address with a 6-digit code and asks for the password twice; an optional profile photo can be added.
- Sign in with user id or email + password, or with an emailed one-time code; **Keep me signed in** keeps the session for 30 days.
- **Forgot password**: a single-use link (valid 30 minutes) is emailed to the account's verified address. Only a hash of the link's token is stored.
- **Profile** page: change or remove the photo; edit full name, gender and (sellers) shop name; confirm the email, or change it with a code sent to the new address; change the password; buyers can delete their account.
- Passwords are stored as BCrypt hashes. Codes are stored only as HMACs, expire after 5 minutes, work once, and are rate limited. Sign-in, codes and reset links never reveal whether an address is registered.

**Help & support** (`/support`)
- Buyers and sellers raise requests with a topic, priority and description, and follow them with a reference such as `SUP-00001`.
- New requests are emailed to the support inbox; admins reply from the dashboard, and customers can reopen or delete their requests.

## 📁 Project Structure
```
src/main/resources/static/        <- frontend: plain HTML/CSS/JS, served as-is by Spring Boot
    index.html                    the single page; loads css/style.css and js/app.js
    css/style.css                 design system, light/dark themes
    js/app.js                     entry point: routes and the role guard
    js/router.js                  History API routing and link handling
    js/pages/                     one module per page (home, book-detail, cart, checkout, orders, support, ...)
    js/layout.js                  header, footer, sign-in layout, theme toggle
    js/components.js  js/icons.js covers, avatars, alerts, stat cards; inline SVG icons
    js/session.js  js/ui.js       signed-in user and cart count; toasts and confirm dialog
    js/otp.js                     email + one-time code fields (sign-up, code sign-in, profile)
    js/api.js  js/dom.js          fetch wrapper for /api; html`` templates that escape all values
src/main/java/com/bookstore/ecommerce/
    api/                          REST controllers and JSON response types
    mail/                         Brevo, SMTP and development-mode mailers
    config/WebConfig.java         serves index.html for the frontend's routes
    service/  repository/  model/ business logic and MongoDB documents
src/test/                         integration tests (real MongoDB) and unit tests
```

## 🧭 Pages
| Page | URL |
| :--- | :--- |
| Store, book, cart, checkout | `/`, `/book/:id`, `/cart`, `/checkout` |
| Buyer orders | `/orders`, `/orders/:id/confirmation` |
| Seller | `/seller`, `/seller/books/new`, `/seller/books/:id/edit` |
| Admin | `/admin`, `/login?as=admin` (Admin sign-in tab) |
| Account | `/login` (`?method=code` for email-code sign-in), `/register`, `/forgot-password`, `/reset-password`, `/account` |
| Help & support | `/support` |

## 🔌 REST API
All responses are JSON; errors look like `{"error": "message", "field": "..."}`. The signed-in user is kept in the HTTP session (cookie). State-changing requests must send the `X-Requested-With` header (CSRF protection).

| Area | Endpoints |
| :--- | :--- |
| Auth | `GET /api/auth/me`, `POST /api/auth/login` (user id or email; `remember`, `role`), `POST /api/auth/logout`, `POST /api/auth/register` (multipart, needs a verified email) |
| One-time codes | `GET /api/auth/otp/config`, `POST /api/auth/otp/send` (`purpose`: `REGISTER`, `LOGIN`, `VERIFY_EMAIL`, `CHANGE_EMAIL`), `POST /api/auth/otp/verify`, `POST /api/auth/login/otp` |
| Password reset | `POST /api/auth/password/forgot` (emails a reset link), `POST /api/auth/password/reset/check`, `POST /api/auth/password/reset` (link token + new password) |
| Your account | `GET/PUT/DELETE /api/users/me`, `POST/DELETE /api/users/me/profile-picture`, `POST /api/users/me/email/verify`, `POST /api/users/me/email`, `POST /api/users/me/password`; photos are served from `/media/avatars/…` |
| Catalogue | `GET /api/books?category=&search=&sort=`, `GET /api/books/{id}` |
| Cart | `GET /api/cart`, `POST /api/cart/items`, `PUT /api/cart/items/{bookId}`, `DELETE /api/cart/items/{bookId}` |
| Orders (buyer) | `GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders` (checkout), `POST /api/orders/{id}/cancel`, `POST /api/orders/items/{itemId}/cancel` |
| Seller | `GET /api/vendor/dashboard`, `GET/POST/PUT/DELETE /api/vendor/books[/{id}]`, `PUT /api/vendor/order-items/{id}/status` |
| Admin | `GET /api/admin/dashboard`, `POST /api/admin/vendors/{id}/approve`, `POST /api/admin/admins`, `POST /api/admin/support/{id}/resolve` |
| Help & support | `GET/POST /api/support`, `POST /api/support/{id}/reopen`, `DELETE /api/support/{id}` |

---

## ✉️ Email
Verification codes, password-reset links and support emails are sent through **Brevo** (recommended) or SMTP. Credentials come from **environment variables** or the git-ignored `config/application.properties`; never put them in `src/main/resources/application.properties` or the frontend code.

**Brevo setup (free plan, 300 emails/day):**
1. Sign up at https://www.brevo.com.
2. Add and verify your sender address: *Senders, domains & dedicated IPs → Senders*.
3. Create an API key: *SMTP & API → API keys → Generate a new API key* (starts with `xkeysib-`).
4. Create `config/application.properties` in the project folder (it is git-ignored) with:
   ```properties
   app.mail.brevo.api-key=xkeysib-your-key
   app.mail.brevo.sender=you@example.com
   ```
   or set `BREVO_API_KEY` and `BREVO_SENDER`, then restart the app. The log shows `Verification emails: Brevo API, sent from …`.

If sending fails, the server log shows Brevo's reason (for example an invalid key or an unverified sender). Emails sent from a free `@gmail.com` address can land in spam; a sender on your own domain delivers best.

| Variable | Purpose |
| :--- | :--- |
| `BREVO_API_KEY` | Brevo API key; when set, emails go through Brevo |
| `BREVO_SENDER` | Sender address verified in Brevo |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP alternative: a Gmail address and its 16-character **App Password** |
| `MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT` | Optional SMTP settings (defaults: `MAIL_USERNAME`, `smtp.gmail.com`, `587`) |
| `OTP_SECRET` | Long random string used to HMAC stored codes. Set it in production (otherwise a random key is used per run). |
| `APP_URL` | Address of the site as users reach it; reset links in emails point here (default `http://localhost:9090`) |
| `SUPPORT_EMAIL` | Support inbox shown on the Help & support page and emailed about new requests |
| `UPLOADS_DIR` | Where profile photos are stored (default `data/uploads`) |

**Development mode** (no Brevo key and no SMTP login): nothing is emailed. After *Send code*, the form fills the code in automatically from a local dev inbox that only answers requests from this computer, and codes and reset links are printed in the Spring Boot console. Codes are still random, expire, and are checked exactly as in production. The app refuses to start without email settings when the `prod` Spring profile is active.

**Limits** (`app.otp.*` in `application.properties`): codes are 6 digits and valid 5 minutes; 60 s before a resend; 5 codes per address per hour; 5 wrong guesses per code; 20 sends and 60 checks per IP per hour.

## 🛢️ Database
The application stores its data in **MongoDB**, in a database named `librisphere`.

- **Requirement**: a MongoDB server on `localhost:27017`. Change the connection with `spring.data.mongodb.uri` in `src/main/resources/application.properties`.
- **Collections**: `users`, `books`, `orders`, `order_items`, `support_requests`; `database_sequences` (counters for short numeric ids such as `/book/12`, `#LBS-3` and `SUP-00001`); `otp_challenges` and `password_reset_tokens` (hashed, deleted automatically by TTL indexes).
- **Users**: `password` is a BCrypt hash; `emailVerified` records a confirmed address; `avatarKey` points at the profile photo file under `UPLOADS_DIR`.
- **Browse it** with [MongoDB Compass](https://www.mongodb.com/products/tools/compass) at `mongodb://localhost:27017`.
- **Reset to the demo data**: stop the app, drop the `librisphere` database, and start the app again.
- A standalone MongoDB server has no multi-document transactions, so stock is reserved with atomic conditional updates; two shoppers can never buy the same last copy.

## 🧪 Tests
```bash
mvnw.cmd test        # Windows
./mvnw test          # macOS / Linux
```
The integration tests need MongoDB and use a throwaway `librisphere_unittest` database that is dropped afterwards. Emails go to an in-memory outbox, so no real email is sent.
