<p align="center">
  <img src="docs/screenshots/logo.png" alt="StopForFuel Logo" width="120" />
</p>

<h1 align="center">StopForFuel</h1>

<p align="center">
  <strong>A comprehensive fuel station management system for Indian petrol bunks</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-green?logo=springboot" alt="Spring Boot 3.2" />
  <img src="https://img.shields.io/badge/Next.js-15-black?logo=next.js" alt="Next.js 15" />
  <img src="https://img.shields.io/badge/React-19-blue?logo=react" alt="React 19" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql" alt="PostgreSQL 16" />
  <img src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript" alt="TypeScript" />
  <img src="https://img.shields.io/badge/Tailwind%20CSS-4-38BDF8?logo=tailwindcss" alt="Tailwind CSS v4" />
  <img src="https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker" alt="Docker" />
</p>

---

## Overview

**StopForFuel** is a full-stack, production-ready fuel station management platform designed specifically for Indian petrol bunk operations. It covers the complete business lifecycle — from daily shift operations and inventory tracking to customer credit management, invoicing, payment reconciliation, and financial reporting.

Built with a modern tech stack (Spring Boot + Next.js), it provides a responsive glassmorphism UI with dark/light theme support, real-time dashboards, and a comprehensive REST API with 150+ endpoints across 29 controllers.

---

## Screenshots

<!-- Add your screenshots to docs/screenshots/ and they will render here -->

### Dashboard
<p align="center">
  <img src="docs/screenshots/dashboard.png" alt="Main Dashboard" width="90%" />
</p>

### Operational Dashboard
<p align="center">
  <img src="docs/screenshots/operational-dashboard.png" alt="Operational Dashboard" width="90%" />
</p>

### Payment Dashboard
<p align="center">
  <img src="docs/screenshots/payment-dashboard.png" alt="Payment Dashboard" width="90%" />
</p>

### Customer Management
<p align="center">
  <img src="docs/screenshots/customers.png" alt="Customer Management" width="90%" />
</p>

### Invoice Billing
<p align="center">
  <img src="docs/screenshots/invoices.png" alt="Invoice Billing" width="90%" />
</p>

### Credit Management
<p align="center">
  <img src="docs/screenshots/credit-overview.png" alt="Credit Overview" width="90%" />
</p>

### Customer Ledger
<p align="center">
  <img src="docs/screenshots/ledger.png" alt="Customer Ledger" width="90%" />
</p>

### Shift Register
<p align="center">
  <img src="docs/screenshots/shift-register.png" alt="Shift Register" width="90%" />
</p>

### Dark Mode
<p align="center">
  <img src="docs/screenshots/dark-mode.png" alt="Dark Mode" width="90%" />
</p>

> **Note:** Place your screenshots in `docs/screenshots/` with the filenames above, or update the paths to match your files.

---

## Features

### Core Operations
- **Shift Management** — Open/close shifts, track attendants, reconcile cash registers
- **Invoice Billing** — Cash and credit invoicing with product line items, discount calculations, and digital signatures
- **Inventory Tracking** — Daily tank dip readings, nozzle meter readings, and product stock tracking with automatic calculations (total stock, sale stock, sales)
- **Cash Advance Management** — Issue advances (home/night/regular), track returns (full/partial), auto-debit from shift register

### Station Equipment
- **Tank Management** — Configure tanks with capacity and linked products, active/inactive status
- **Pump Management** — Define pumps with activation controls
- **Nozzle Management** — Map nozzles to tanks and pumps, track stamping expiry dates, nozzle companies
- **Station Layout** — Visual overview of the station's equipment hierarchy

### Customer & Fleet Management
- **Customer Profiles** — Comprehensive customer records with phone numbers, addresses, GST, credit limits (amount & liters)
- **Group Management** — Organize customers into fleet groups for bulk operations
- **Vehicle Registry** — Track vehicles with types, preferred products, monthly liter limits, consumed liters
- **Customer-Vehicle Mapping** — Assign/unassign vehicles to customers and customers to groups
- **Auto-Block System** — Automatically blocks customers when credit limits are exceeded (amount, liters, or 90-day aging)

### Financial Management
- **Payment Processing** — Record payments against statements or individual bills with multiple payment modes (Cash, UPI, Card, Cheque, Bank Transfer, CCMS)
- **Statement Generation** — Generate statements from unlinked credit bills with optional filters (date range, vehicle, product, specific bills)
- **Credit Management** — Real-time credit overview with aging breakdown (0-30, 31-60, 61-90, 90+ days)
- **Customer Ledger** — Full debit/credit ledger with opening balance, running balance, and closing balance
- **Payment Reconciliation** — Track payments per statement and per bill with summary views

### Products & Suppliers
- **Product Catalog** — Fuel, lubricants, and accessories with categories, HSN codes, pricing, and grade types
- **Supplier Management** — Track fuel suppliers with contact details
- **Grade Types** — Define oil types and grade classifications (e.g., Premium Diesel, Regular Petrol)
- **Incentive System** — Configure per-customer, per-product discount rates based on minimum quantities

### Dashboards & Analytics
- **Main Dashboard** — High-level business metrics and visualizations (Recharts)
- **Operational Dashboard** — Daily operations summary with inventory status
- **Payment Dashboard** — Payment collection tracking and outstanding analysis

### System Features
- **Multi-Tenancy** — Site Company ID (`scid`) on all entities for tenant isolation
- **Shift-Cycle Linking** — All transactions linked to active shift for audit trail
- **Dark/Light Theme** — System-aware theme with manual toggle
- **Glassmorphism UI** — Modern glass-card design language with smooth transitions
- **Responsive Design** — Works across desktop and tablet form factors

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Backend** | Java (OpenJDK) | 21 |
| | Spring Boot | 3.2.2 |
| | Spring Data JPA | 3.2.x |
| | Hibernate | 6.x |
| | Lombok | Latest |
| | Gradle | 8.5 |
| **Frontend** | Next.js (App Router) | 15 |
| | React | 19 |
| | TypeScript | 5 |
| | Tailwind CSS | 4 |
| | Recharts | 3.5 |
| | Lucide Icons | 0.555 |
| | next-themes | 0.4 |
| **Database** | PostgreSQL | 16 |
| **Infrastructure** | Docker | Multi-stage builds |
| | Docker Compose | 3-service stack |
| | AWS EC2 + ECR | Production deployment |
| **Testing** | JUnit 5 | Latest |
| | Mockito | Latest |
| | Spring MockMvc | Latest |

---

## Project Structure

```
stopforfuel/
├── backend/                          # Spring Boot API server
│   ├── src/main/java/com/stopforfuel/
│   │   ├── backend/
│   │   │   ├── controller/           # 29 REST controllers (150+ endpoints)
│   │   │   ├── service/              # 23 service classes (business logic)
│   │   │   ├── repository/           # Spring Data JPA repositories
│   │   │   ├── entity/               # 32 JPA entities + 9 transaction subtypes
│   │   │   └── DataInitializer.java  # Seed data on startup
│   │   ├── config/                   # CORS, exception handling
│   │   └── employee/                 # Legacy employee module
│   ├── src/test/java/                # 35 test files, 319 test methods
│   │   └── com/stopforfuel/backend/
│   │       ├── controller/           # 12 controller tests (MockMvc)
│   │       └── service/              # 23 service tests (Mockito)
│   ├── Dockerfile                    # Multi-stage build (JDK 21 → JRE 21)
│   └── build.gradle
│
├── frontend/                         # Next.js UI
│   ├── app/                          # 29 page routes (App Router)
│   │   ├── page.tsx                  # Main dashboard
│   │   ├── customers/                # Customer, groups, vehicles, mappings, profile
│   │   ├── operations/               # Tanks, pumps, nozzles, inventory, shifts, invoices
│   │   ├── payments/                 # Payments, statements, credit, ledger
│   │   ├── employees/                # Employee management
│   │   ├── company/                  # Company settings
│   │   └── marketing/                # Marketing page
│   ├── components/                   # Shared React components
│   │   ├── ui/                       # UI primitives (glass-card, buttons, etc.)
│   │   ├── app-sidebar.tsx           # Main navigation sidebar
│   │   └── theme-provider.tsx        # Dark/light theme
│   ├── lib/                          # Utilities (cn() for className merging)
│   ├── Dockerfile                    # Multi-stage build (Node 20 Alpine)
│   └── package.json
│
├── docker-compose.yml                # Full stack: PostgreSQL + Backend + Frontend
├── start_services.sh                 # Dev startup script
├── CLAUDE.md                         # AI assistant project context
└── README.md                         # This file
```

---

## Getting Started

### Prerequisites

- **Java 21** (OpenJDK recommended)
- **Node.js 20.9+** with npm
- **Docker** and **Docker Compose** (for database, or full stack)
- **Git**

### Option 1: Docker Compose (Recommended)

Spin up the entire stack with a single command:

```bash
git clone https://github.com/BITS08SATHYA/stop4fuel.git
cd stop4fuel

# Start all services (PostgreSQL + Backend + Frontend)
docker compose up -d

# View logs
docker compose logs -f
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api |
| PostgreSQL | localhost:5432 |

To stop:
```bash
docker compose down
```

### Option 2: Local Development

**1. Start the database:**
```bash
docker compose up -d postgres
```

**2. Start the backend:**
```bash
cd backend
./gradlew bootRun
```
Backend will start on http://localhost:8080

**3. Start the frontend:**
```bash
cd frontend
npm install
npm run dev
```
Frontend will start on http://localhost:3000

---

## API Reference

The backend exposes a RESTful API at `/api/*` with 150+ endpoints across 29 controllers. Here are the key resource groups:

### Station Equipment
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/tanks` | List all tanks |
| GET | `/api/tanks/active` | List active tanks |
| POST | `/api/tanks` | Create a tank |
| PUT | `/api/tanks/{id}` | Update a tank |
| PATCH | `/api/tanks/{id}/toggle-status` | Toggle tank active status |
| GET | `/api/pumps` | List all pumps |
| GET | `/api/nozzles` | List all nozzles |
| GET | `/api/nozzles/tank/{tankId}` | Nozzles by tank |

### Customer Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/customers?search=&groupId=` | Paginated customer list with filters |
| GET | `/api/customers/{id}` | Get customer by ID |
| POST | `/api/customers` | Create customer |
| PUT | `/api/customers/{id}` | Update customer |
| PATCH | `/api/customers/{id}/toggle-status` | Toggle active/inactive |
| PATCH | `/api/customers/{id}/block` | Block customer |
| PATCH | `/api/customers/{id}/unblock` | Unblock customer |
| GET | `/api/customers/stats` | Customer statistics |
| GET | `/api/customers/{id}/vehicles` | Vehicles for customer |

### Groups & Mappings
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/groups` | List all groups |
| POST | `/api/groups` | Create group |
| DELETE | `/api/groups/{id}` | Delete group (unlinks customers) |
| PATCH | `/api/mappings/assign-customers-to-group` | Assign customers to group |
| PATCH | `/api/mappings/assign-vehicles-to-customer` | Assign vehicles to customer |
| GET | `/api/mappings/unassigned-customers` | List unassigned customers |
| GET | `/api/mappings/unassigned-vehicles` | List unassigned vehicles |

### Invoicing
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/invoices` | List all invoices |
| POST | `/api/invoices` | Create invoice (cash or credit) |
| GET | `/api/invoices/customer/{id}?billType=&paymentStatus=&fromDate=&toDate=` | Customer invoices with filters |
| GET | `/api/invoices/shift/{shiftId}` | Invoices for a shift |

### Payments
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/payments/statement/{statementId}` | Record payment against statement |
| POST | `/api/payments/bill/{invoiceBillId}` | Record payment against individual bill |
| GET | `/api/payments/customer/{customerId}` | Paginated payments for customer |
| GET | `/api/payments/summary/statement/{id}` | Statement payment summary |
| GET | `/api/payments/summary/bill/{id}` | Bill payment summary |

### Statements & Credit
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/statements/generate` | Generate statement from credit bills |
| GET | `/api/statements?customerId=&status=` | List statements with filters |
| DELETE | `/api/statements/{id}/bills/{billId}` | Remove bill from statement |
| GET | `/api/credit/overview` | Credit overview with aging breakdown |
| GET | `/api/credit/customer/{id}` | Customer credit detail |

### Ledger
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/ledger/opening-balance?customerId=&asOfDate=` | Opening balance at date |
| GET | `/api/ledger/customer/{id}?fromDate=&toDate=` | Full ledger with running balance |
| GET | `/api/ledger/outstanding/{customerId}` | Unpaid credit bills |

### Inventory
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/inventory/tanks` | Save tank dip reading |
| POST | `/api/inventory/nozzles` | Save nozzle meter reading |
| POST | `/api/inventory/products` | Save product stock |
| GET | `/api/inventory/tanks?date=` | Tank readings by date |
| GET | `/api/inventory/nozzles?date=` | Nozzle readings by date |

### Shift Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/shifts/open` | Open a new shift |
| POST | `/api/shifts/{id}/close` | Close active shift |
| GET | `/api/shifts/active` | Get active shift |
| GET | `/api/shifts/{id}/summary` | Shift transaction summary |
| POST | `/api/advances` | Create cash advance |
| POST | `/api/advances/{id}/return` | Record advance return |

### Products & Suppliers
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/products` | List all products |
| GET | `/api/products/active` | Active products only |
| GET | `/api/suppliers` | List all suppliers |
| GET | `/api/grades` | List all grade types |

---

## Database Schema

The application uses **41 JPA entities** organized into these domain areas:

```
                    ┌──────────────┐
                    │   Company    │
                    └──────────────┘
                           │ scid (tenant)
          ┌────────────────┼────────────────┐
          │                │                │
   ┌──────┴──────┐  ┌─────┴─────┐  ┌───────┴──────┐
   │   Customer  │  │   Shift   │  │   Product    │
   │  (groups,   │  │ (open/    │  │ (fuel, lube, │
   │   vehicles) │  │  close)   │  │  accessory)  │
   └──────┬──────┘  └─────┬─────┘  └───────┬──────┘
          │               │                │
   ┌──────┴──────┐  ┌─────┴─────┐  ┌───────┴──────┐
   │ InvoiceBill │  │  Shift    │  │    Tank      │
   │ (cash/      │←─│Transaction│  │  (capacity,  │
   │  credit)    │  │ (cash,upi,│  │   product)   │
   └──────┬──────┘  │  card,    │  └───────┬──────┘
          │         │  expense) │          │
   ┌──────┴──────┐  └──────────┘   ┌──────┴──────┐
   │  Statement  │                 │   Nozzle    │
   │  (billing   │                 │  (tank +    │
   │   period)   │                 │   pump)     │
   └──────┬──────┘                 └─────────────┘
          │
   ┌──────┴──────┐
   │   Payment   │
   │ (statement  │
   │  or bill)   │
   └─────────────┘
```

**Key design patterns:**
- **BaseEntity** — All tenant-aware entities extend this with `scid` (Site Company ID), `shiftId`, and audit timestamps
- **SimpleBaseEntity** — Lightweight base without tenant/shift fields (used by Vehicle)
- **Single Table Inheritance** — `ShiftTransaction` uses discriminator column for Cash, UPI, Card, Cheque, Bank, CCMS, Expense subtypes
- **Auto-managed schema** — Hibernate `ddl-auto: update` (no manual migrations)

---

## Testing

The project has comprehensive unit tests with **319 test methods across 35 test files**.

```bash
# Run all tests
cd backend
./gradlew test

# Run a specific test class
./gradlew test --tests "com.stopforfuel.backend.service.CustomerServiceTest"

# View HTML report
open build/reports/tests/test/index.html
```

### Test Coverage

| Layer | Test Files | Test Methods |
|-------|-----------|-------------|
| **Service Tests** | 23 | ~240 |
| **Controller Tests** | 12 | ~80 |
| **Total** | **35** | **319** |

**Services tested:** Customer, Product, Payment, Shift, InvoiceBill, Tank, Pump, Nozzle, Company, Group, Vehicle, Mapping, GradeType, Supplier, ShiftTransaction, Incentive, TankInventory, NozzleInventory, ProductInventory, Ledger, Statement, CreditManagement, CashAdvance

**Controllers tested:** Customer, Product, Shift, Tank, Company, Group, Vehicle, Mapping, InvoiceBill, Payment, Ledger, CashAdvance

---

## Deployment

### Docker Compose (Development/Staging)

```bash
docker compose up -d --build
```

### AWS EC2 + ECR (Production)

The project includes production deployment scripts for AWS:

```bash
# Build and push images to ECR
./deploy/build-and-push.sh

# Deploy on EC2
./deploy/deploy-ec2.sh
```

**Production architecture:**
- **EC2 instance** running Docker containers
- **ECR repositories** for backend and frontend images
- **Multi-stage Docker builds** for optimized image sizes
- **Environment-based configuration** via Docker environment variables

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://172.19.0.2:5432/stopforfuel` | PostgreSQL connection URL |
| `DATABASE_USER` | `postgres` | Database username |
| `DATABASE_PASSWORD` | `password` | Database password |
| `SERVER_PORT` | `8080` | Backend server port |
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080/api` | API URL for frontend |

---

## Development

### Backend Commands

```bash
cd backend
./gradlew bootRun              # Start dev server (hot reload)
./gradlew test                 # Run all tests
./gradlew build                # Production build
./gradlew bootJar              # Build executable JAR
```

### Frontend Commands

```bash
cd frontend
npm install                    # Install dependencies
npm run dev                    # Start dev server (hot reload)
npm run build                  # Production build
npm run lint                   # Run ESLint
```

### Database

```bash
# Start PostgreSQL via Docker
docker compose up -d postgres

# Connect via psql
docker exec -it stopforfuel-postgres psql -U postgres -d stopforfuel
```

> **Note:** The schema is auto-managed by Hibernate. No manual migrations needed — just start the backend and tables are created/updated automatically.

---

## Configuration

### CORS

CORS is configured in `com.stopforfuel.config.WebConfig` to allow the frontend origin. For production, update the allowed origins:

```java
// WebConfig.java
allowedOrigins("http://localhost:3000", "https://your-domain.com")
```

### Theming

The frontend uses CSS variables with `next-themes` for dark/light mode. Theme tokens are defined in `globals.css` and the system preference is respected by default.

---

## Roadmap

- [ ] AWS Cognito authentication with RBAC (Admin, Manager, Attendant, Customer roles)
- [ ] JasperReports for PDF invoices and statements
- [ ] Epson thermal printer integration for bill printing
- [ ] SMS/WhatsApp notifications for credit alerts
- [ ] Mobile-responsive PWA for attendant use
- [ ] GST return data export
- [ ] Multi-station support with centralized management

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is proprietary software. All rights reserved.

---

<p align="center">
  Built with care for Indian petrol bunk operations
</p>
