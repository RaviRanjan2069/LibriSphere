# LibriSphere - Multi-Vendor Bookstore E-Commerce Platform

LibriSphere is a multi-vendor bookstore web application. The frontend is a single-page app written in plain **HTML, CSS and JavaScript** (no framework, no build step) that talks to a **Spring Boot REST API** backed by **MongoDB**.

## 🌟 Key Features

1. **Buyers**
   - Browse, search and filter the catalogue by category; sort by price, title or newest.
   - Book pages with stock status, quantity picker and seller details.
   - Cart (works before signing in), checkout, and an order confirmation page.
   - My Orders with All / Active / Delivered / Cancelled tabs; cancel a whole order or a single item until it ships, with stock returned and the refund shown.

2. **Sellers**
   - Dashboard with revenue, units sold, orders and low-stock alerts.
   - Orders tabs (To ship / Shipped / Delivered / Cancelled / All); update status or cancel unshipped items.
   - Add, edit and delete listings with a live cover preview.

3. **Administrators**
   - Platform totals, recent orders with refunds, and approval of new seller accounts.
   - Create more administrator accounts, and answer Help & support requests (the customer is emailed the reply).

4. **Accounts & security**
   - Sign-up verifies the **email address** with a 6-digit code (resend countdown) and asks for the password twice; an optional profile picture can be added with a live preview.
   - Sign in with **email or username + password**, or with an **emailed one-time code**.
   - **Forgot password** (`/forgot-password`): emails a single-use link (valid 30 minutes) to the account's verified address; it opens `/reset-password` to choose a new password. Only a hash of the link's token is stored.
   - *Profile* page (`/account`): change or remove the photo; edit full name, gender and (sellers) shop name; confirm the email, or change it with a code sent to the new address; change the password; and, for buyers, delete the account (password required, refused while an order is still on its way). Avatars appear in the navbar and on seller/admin dashboards.
   - Passwords are stored as BCrypt hashes (older plain-text passwords are upgraded automatically on startup). Codes are stored only as HMACs, expire after 5 minutes, work once, and are rate limited.

5. **Design**
   - Light theme by default, dark mode that follows the OS with a toggle in the navbar.
   - Responsive from phone to desktop; accessible labels, focus states and confirmation dialogs.

---

## 🛠️ Technology Stack
- **Frontend**: HTML, CSS and vanilla JavaScript (ES modules), served directly from `src/main/resources/static/`
- **Backend**: Spring Boot 3.3 REST API (`/api/...`), Java 21
- **Database**: MongoDB (local server on `localhost:27017`) via Spring Data MongoDB

## 📁 Project Structure
```
src/main/resources/static/        <- frontend: plain HTML/CSS/JS, served as-is by Spring Boot
    index.html                    the single page; loads css/style.css and js/app.js
    css/style.css                 design system, light/dark themes
    js/app.js                     entry point: routes and the role guard
    js/router.js                  History API routing and link handling
    js/pages/                     one module per page (home, book-detail, cart, checkout, orders, ...)
    js/layout.js                  header, footer, sign-in layout, theme toggle
    js/components.js  js/icons.js covers, avatars, alerts, stat cards; inline SVG icons
    js/session.js  js/ui.js       signed-in user and cart count; toasts and confirm dialog
    js/otp.js                     email + one-time code fields (sign-up, code sign-in, account)
    js/api.js  js/dom.js          fetch wrapper for /api; html`` templates that escape all values
src/main/java/com/bookstore/ecommerce/
    api/                          REST controllers and JSON response types
    config/WebConfig.java         serves index.html for the frontend's routes
    service/  repository/  model/ business logic and MongoDB documents
```

## 🔌 REST API
All responses are JSON; errors look like `{"error": "message"}`. The signed-in user is kept in the HTTP session (cookie).

| Area | Endpoints |
| :--- | :--- |
| Auth | `GET /api/auth/me`, `POST /api/auth/login` (username or email), `POST /api/auth/logout`, `POST /api/auth/register` (multipart, needs a verified email) |
| One-time codes | `GET /api/auth/otp/config`, `POST /api/auth/otp/send` (`purpose`: `REGISTER`, `LOGIN`, `VERIFY_EMAIL`, `CHANGE_EMAIL`), `POST /api/auth/otp/verify`, `POST /api/auth/login/otp`, `POST /api/auth/password/forgot` (emails a reset link), `POST /api/auth/password/reset/check`, `POST /api/auth/password/reset` (link token + new password) |
| Your account | `GET/PUT/DELETE /api/users/me` (details; delete a buyer account with `{password}`), `POST/DELETE /api/users/me/profile-picture`, `POST /api/users/me/email/verify`, `POST /api/users/me/email` (new address + `CHANGE_EMAIL` code), `POST /api/users/me/password`; pictures are served from `/media/avatars/…` |
| Catalogue | `GET /api/books?category=&search=&sort=`, `GET /api/books/{id}` |
| Cart | `GET /api/cart`, `POST /api/cart/items`, `PUT /api/cart/items/{bookId}`, `DELETE /api/cart/items/{bookId}` |
| Orders (buyer) | `GET /api/orders`, `GET /api/orders/{id}`, `POST /api/orders` (checkout), `POST /api/orders/{id}/cancel`, `POST /api/orders/items/{itemId}/cancel` |
| Seller | `GET /api/vendor/dashboard`, `GET/POST/PUT/DELETE /api/vendor/books[/{id}]`, `PUT /api/vendor/order-items/{id}/status` |
| Admin | `GET /api/admin/dashboard`, `POST /api/admin/vendors/{id}/approve`, `POST /api/admin/admins`, `POST /api/admin/support/{id}/resolve` |
| Help & support | `GET/POST /api/support`, `POST /api/support/{id}/reopen`, `DELETE /api/support/{id}` |

---

## 🚀 Running the Project
Requires Java 21 and a running MongoDB server (the Windows "MongoDB" service).

**Run the app**:
```bash
mvnw.cmd spring-boot:run        # Windows
./mvnw spring-boot:run          # macOS / Linux
```
Open **http://localhost:9090**.

**Work on the frontend**: edit the files in `src/main/resources/static/` and reload the browser. There is nothing to install or build; `spring-boot:run` serves those files straight from the source folder.

| Page | URL |
| :--- | :--- |
| Store, book, cart, checkout | `/`, `/book/:id`, `/cart`, `/checkout` |
| Buyer orders | `/orders`, `/orders/:id/confirmation` |
| Seller | `/seller`, `/seller/books/new`, `/seller/books/:id/edit` |
| Admin | `/admin`, `/login?as=admin` (Admin sign-in tab) |
| Help & support | `/support` |
| Account | `/login` (`?method=code` for email-code sign-in), `/register`, `/forgot-password`, `/reset-password`, `/account` |

---

## 🔑 Demo Sandbox Credentials
The database is pre-seeded with realistic mockup data (vendors, catalog, orders) so you can test all operations immediately:

| Role | Username | Password | Notes |
| :--- | :--- | :--- | :--- |
| **Administrator** | `admin` | `admin123` | Platform approval, global metrics |
| **Vendor A** | `vendor_a` | `vendor123` | Storefront: *Apex Books* (Approved) |
| **Vendor B** | `vendor_b` | `vendor123` | Storefront: *Beacon Bookshop* (Approved) |
| **Vendor C** | `vendor_c` | `vendor123` | Storefront: *Cosmos Reads* (Pending admin approval) |
| **Buyer** | `buyer` | `buyer123` | Pre-existing customer with order history |

**Run the backend tests** (needs MongoDB; uses a throwaway `librisphere_unittest` database):
```bash
mvnw.cmd test
```

---

## ✉️ Email verification codes

Verification and password-reset codes are emailed through **Brevo** (recommended) or SMTP. Credentials come from **environment variables** or the git-ignored `config/application.properties`; never put them in `src/main/resources/application.properties` or the frontend code.

**Brevo setup (free plan, 300 emails/day):**
1. Sign up at https://www.brevo.com with `ravirajput2069@gmail.com`.
2. Make sure that address is a verified sender: *Senders, domains & dedicated IPs → Senders* (verify it from the email Brevo sends you if it isn't already).
3. Create an API key: *SMTP & API → API keys → Generate a new API key* (starts with `xkeysib-`).
4. Paste it into `config/application.properties` (`app.mail.brevo.api-key=xkeysib-…`), or set `BREVO_API_KEY`, then restart the app. The log shows `Verification emails: Brevo API, sent from ravirajput2069@gmail.com`.

If sending fails, the server log shows Brevo's reason (for example an invalid key or an unverified sender). Emails sent from a free `@gmail.com` address can land in spam; a sender on your own domain delivers best.

| Variable | Purpose |
| :--- | :--- |
| `BREVO_API_KEY` | Brevo API key; when set, emails go through Brevo |
| `BREVO_SENDER` | Sender address verified in Brevo (default `ravirajput2069@gmail.com`) |
| `MAIL_USERNAME` | SMTP alternative: the Gmail address that sends the codes |
| `MAIL_PASSWORD` | A Gmail **App Password** (16 characters), not your normal password |
| `MAIL_FROM` | Optional sender address (defaults to `MAIL_USERNAME`) |
| `MAIL_HOST`, `MAIL_PORT` | Optional, for other providers (defaults `smtp.gmail.com`, `587`) |
| `OTP_SECRET` | Long random string used to HMAC stored codes. Set it in production (otherwise a random key is used per run). |
| `SUPPORT_EMAIL` | Support inbox shown on the Help & support page and emailed about new requests (default `mentora.smartlearning@gmail.com`) |
| `APP_URL` | Address of the site as users reach it; reset links in emails point here (default `http://localhost:9090`) |
| `UPLOADS_DIR` | Where profile pictures are stored (default `data/uploads`) |

**Development mode** (no `BREVO_API_KEY` and no `MAIL_USERNAME`/`MAIL_PASSWORD`): nothing is emailed. After *Send code*, the form fills the code in automatically from a local dev inbox (`GET /api/dev/mailbox/latest`, which answers only in development mode and only to requests from this computer). Each code is also printed in the Spring Boot console as
`[DEV EMAIL - not sent] to ra•••@gmail.com: Your LibriSphere verification code is 123456`, and the forms show a "Development mode" notice. Codes are still random, expire, and are checked by the server exactly as in production. The app refuses to start without mail credentials when the `prod` Spring profile is active.

**Gmail SMTP setup (alternative to Brevo):**
1. Turn on 2-Step Verification for the Google account: Google Account → Security → 2-Step Verification.
2. Create an App Password: Google Account → Security → 2-Step Verification → **App passwords** (or https://myaccount.google.com/apppasswords), name it "LibriSphere", copy the 16-character password.
3. Start the app with it (Windows PowerShell):
   ```powershell
   $env:MAIL_USERNAME="you@gmail.com"; $env:MAIL_PASSWORD="abcd efgh ijkl mnop"; $env:OTP_SECRET="<long random string>"
   .\mvnw.cmd spring-boot:run
   ```
   The log shows `Verification emails: SMTP via smtp.gmail.com` instead of the development-mode banner. A personal Gmail account can send a few hundred emails a day.

**Limits** (in `application.properties`, `app.otp.*`): codes are 6 digits and valid 5 minutes; 60 s before a resend; 5 codes per address per hour; 5 wrong guesses per code; 20 sends and 60 checks per IP per hour. Asking for a sign-in code never reveals whether an address is registered, and codes are only ever sent to addresses that have been verified (or that are being verified by a signed-in owner).

---

## 🛢️ Database
The application stores users, books and orders in **MongoDB**, in a database named `librisphere`.

- **Requirement**: a MongoDB server running on `localhost:27017` (the Windows "MongoDB" service). Change the connection in `src/main/resources/application.properties` via `spring.data.mongodb.uri`.
- **Collections**: `users`, `books`, `orders`, `order_items`, plus `database_sequences`, which holds the counters for the numeric ids used in URLs (`/book/12`) and order numbers (`#LBS-3`), and `otp_challenges`, which holds pending one-time codes (hashed; MongoDB deletes them automatically through a TTL index on `purgeAt`).
- **Users**: `password` is a BCrypt hash; `emailVerified` / `emailVerifiedAt` record a confirmed address, and `avatarKey` (e.g. `avatars/<random>.jpg`) points at the profile picture, whose file lives under `UPLOADS_DIR`, not in MongoDB.
- **Browse it** with [MongoDB Compass](https://www.mongodb.com/products/tools/compass) by connecting to `mongodb://localhost:27017`.
- **First start**: if the `users` collection is empty, demo accounts, books and a sample order are created automatically.
- **Reset to the demo data**: stop the app, drop the `librisphere` database (for example in Compass), and start the app again.
- **Note**: a standalone MongoDB server has no multi-document transactions, so stock is reserved with atomic conditional updates; two shoppers can never buy the same last copy.
