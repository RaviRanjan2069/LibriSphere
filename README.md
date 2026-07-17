# LibriSphere - Multi-Vendor Bookstore E-Commerce Platform

LibriSphere is a modern, premium multi-vendor bookstore e-commerce web application built using **Spring Boot**, **JPA/Hibernate**, **Thymeleaf**, and **HTML5/CSS3/JavaScript**.

## 🌟 Key Features

1. **Multi-Vendor System**:
   - Multiple vendors can register and list their book products.
   - Separate order tracking: individual items within a single customer order are routed to their respective vendors.
   - Vendors can update fulfillment status ("PENDING", "SHIPPED", "DELIVERED") independently for their products.
   - Vendor analytics dashboard showing total earnings, units sold, and active orders.

2. **Buyer Storefront**:
   - Modern storefront layout with responsive flex/grid spacing and category filters.
   - Search functionality across titles and authors.
   - Detailed book viewer showing vendor details, descriptions, and stock counts.
   - Interactive shopping cart with quantity adjustments and asynchronous AJAX add-to-cart operations.
   - Simulated secure checkout and order confirmation.
   - Order history tracking page with item status details.

3. **Platform Administration**:
   - Admin overview dashboard displaying global gross sales, total orders, catalog counts, and registered user counts.
   - Verification system for pending vendor applications.

4. **Premium Visual Styling**:
   - Custom property-based dark mode theme featuring a deep glassmorphism visual system.
   - Vibrant indigo/purple glowing highlights and transitions.
   - Responsive page sizing adapting perfectly to mobile, tablet, and desktop viewports.

---

## 🛠️ Technology Stack
- **Backend Framework**: Spring Boot 3.3.0
- **Language**: Java 21 (Supports Java 17+)
- **Database**: H2 In-Memory Database (Zero config required)
- **Object-Relational Mapping (ORM)**: Spring Data JPA (Hibernate)
- **Template Engine**: Thymeleaf (serving HTML/CSS/JS)
- **Styling**: Vanilla CSS3 (Modern custom design system)
- **Interactions**: Vanilla JavaScript (ES6+, Fetch API, custom Toast notifications)

---

## 🚀 Running the Project

### Option 1: Run via IDE (Recommended)
1. Open your favorite Java IDE (e.g., **IntelliJ IDEA**, **Eclipse**, or **VS Code**).
2. Import/Open this directory (`c:\Users\ravir\Desktop\intern`) as a **Maven Project**.
3. Let the IDE import the dependencies defined in `pom.xml`.
4. Locate the main application class:
   `src/main/java/com/bookstore/ecommerce/EcommerceApplication.java`
5. Right-click and select **Run 'EcommerceApplication'**.
6. Open your browser and navigate to: `http://localhost:8080`

### Option 2: Run via Command Line (Requires Maven installed)
1. Open your terminal/shell inside this directory.
2. Clean and run the project using:
   ```bash
   mvn spring-boot:run
   ```
3. Open your browser and navigate to: `http://localhost:8080`

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

---

## 🛢️ Database Console
Since the application uses an in-memory H2 database, you can view the live database tables in your browser while the application is running:
- **H2 Console URL**: `http://localhost:8080/h2-console`
- **JDBC URL**: `jdbc:h2:mem:bookstoredb`
- **Username**: `sa`
- **Password**: *(leave empty)*
