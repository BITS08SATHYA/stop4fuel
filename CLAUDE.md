# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

StopForFuel is a fuel station management application with a Spring Boot backend and Next.js frontend.

## Development Commands

### Backend (Java 21 / Spring Boot 3.2 / Gradle)
```bash
cd backend
docker compose up -d                  # Start PostgreSQL (port 5432)
./gradlew bootRun                     # Start backend (port 8080)
./gradlew test                        # Run tests
./gradlew test --tests "com.stopforfuel.SomeTest"  # Run single test
```

### Frontend (Next.js 15 / React 19 / TypeScript)
```bash
cd frontend
npm install
npm run dev      # Start dev server (port 3000)
npm run build    # Production build
npm run lint     # ESLint
```

### Both Services
```bash
./start_services.sh   # Starts backend only (runs gradlew bootRun)
```

## Architecture

### Backend (`backend/`)
- **Framework**: Spring Boot 3.2.2, Java 21, Gradle, PostgreSQL 16, Lombok
- **Package structure**: `com.stopforfuel.backend.{entity,repository,service,controller}`
  - Legacy employee module lives under `com.stopforfuel.employee` (separate from main package)
- **Pattern**: Standard layered architecture — Controller → Service → Repository (Spring Data JPA)
- **API prefix**: All REST endpoints use `/api/<resource>` (e.g., `/api/customers`, `/api/tanks`)
- **Multi-tenancy**: `BaseEntity` includes `scid` (Site Company ID) for tenant isolation and `shiftId` for shift-cycle linking. `SimpleBaseEntity` is a lighter base without tenant/shift fields.
- **CORS**: Configured in `com.stopforfuel.config.WebConfig` allowing `http://localhost:3000`
- **DDL**: Hibernate `ddl-auto: update` — schema is auto-managed, no manual migrations
- **Data seeding**: `DataInitializer` provides initial data on startup

### Frontend (`frontend/`)
- **Framework**: Next.js 15 (App Router), React 19, TypeScript, Tailwind CSS v4
- **Styling**: Tailwind with CSS variables for theming (light/dark via `next-themes`), custom `glass-card` UI component
- **Charts**: Recharts for dashboard visualizations
- **Layout**: `app/layout.tsx` wraps all pages with `AppSidebar` (sidebar navigation) + `ThemeProvider`
- **Route groups**:
  - `/` — Main Dashboard (revenue, fuel volume, credit aging, tank status)
  - `/customers/**` — Customer management (groups, vehicles, mappings, profile)
  - `/operations/**` — Station operations (tanks, pumps, nozzles, products, suppliers, grades, inventory, invoices, shifts, advances)
  - `/operations/dashboard` — Operational Dashboard (tank/pump/nozzle status, meter readings)
  - `/operations/invoices/dashboard` — Invoice Analytics Dashboard
  - `/payments/**` — Payment tracking, credit overview, ledger, statements
  - `/payments/dashboard` — Payment Analytics Dashboard
  - `/employees`, `/company` — Staff and company management (salary, leave, attendance)
  - `/analytics/**` — ML analytics pages (placeholder — needs ML microservice)
- **Components**: Shared components in `components/`, UI primitives in `components/ui/`
- **Utilities**: `lib/utils.ts` (includes `cn()` for className merging via clsx + tailwind-merge)

## Key Domain Entities

The app models a fuel station with: Company, Customers, Groups, Vehicles, VehicleTypes, Products, Suppliers, GradeTypes, Tanks, Pumps, Nozzles, Shifts, ShiftClosingReport, ShiftTransactions (Cash/Card/UPI/CCMS/Cheque/Bank/Expense/NightCash), Inventory tracking (TankInventory, NozzleInventory, ProductInventory, GodownStock, CashierStock, StockTransfer, PurchaseOrder), InvoiceBills, Statements, Payments, CashAdvance, EmployeeAdvance, SalaryPayment, and Employee (with leave, attendance, documents).

## Database

PostgreSQL 16 via Docker. Connection defaults in `application.yml` point to a Docker network IP (`172.19.0.2`). If connection fails, verify the container IP or switch to `localhost`.
