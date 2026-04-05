# Android App — StopForFuel MVP Plan

## Context

All core web features, auth, and CI/CD are complete. Backend REST APIs are fully built. This is a **first draft** Android app focused on two station roles: **cashiers** (raise invoices) and **fuel attendants** (log pump readings + raise invoices at their pump). Single app, role-based navigation.

**Folder**: `android/` inside the monorepo (created during implementation, not now)
**Play Store name**: "StopForFuel" (verified available on Play Store + App Store)
**Domain**: stopforfuel.com (already owned)

## Architecture

- **Kotlin + Jetpack Compose**, single-Activity, MVVM
- **Single module** — extractable to multi-module as app grows
- **Dependencies**: Retrofit + OkHttp, Hilt, Navigation Compose, Material3, EncryptedSharedPreferences
- **No Room/offline** for MVP

### Package Structure
```
com.stopforfuel.app/
  data/
    remote/          — ApiService, AuthInterceptor, DTOs
    local/           — TokenStore (EncryptedSharedPreferences)
    repository/      — AuthRepo, InvoiceRepo, LookupRepo, ShiftRepo, PumpSessionRepo
  ui/
    theme/           — Material3 theme, WindowSizeClass
    navigation/      — NavGraph, Routes
    login/           — LoginScreen + ViewModel
    home/            — HomeScreen + ViewModel (shift/session hub)
    invoice/         — InvoiceScreen + ViewModel + components/
    pumpsession/     — PumpSessionScreen + ViewModel (attendant readings)
    history/         — ShiftInvoicesScreen + ViewModel
    common/          — LoadingOverlay, ErrorSnackbar
```

## Screens (5 total)

### 1. Login
- **Auto-detect phone number** from SIM (via `TelephonyManager` + permission) or show saved numbers list
- Large 4-digit PIN pad (no keyboard needed)
- `POST /api/auth/login` → store JWT + user info
- Auto-redirect on token expiry (8hr TTL)

### 2. Home (Hub — role-based)

**Cashier sees:**
- Active shift status (shift #, attendant, start time) from `GET /api/shifts/active`
- **[New Invoice]** — primary action (disabled if no active shift)
- **[Shift Bills]** — view invoices in current shift

**Attendant sees:**
- Active pump session status (pump name, open readings, start time)
- **[Start Session]** / **[End Session]** — open/close pump session
- **[New Invoice]** — raise invoice (pre-filtered to their assigned pump's nozzles)
- **[My Sales]** — view their session's invoices + total

Both roles: user name, station info from JWT in header. Pre-fetch products + nozzles into cache.

### 3. Create Invoice (2-step, multi-product)

Same for both cashier and attendant. **Attendant's nozzle list is pre-filtered to their assigned pump.**

**Step 1 — "WHAT" (Products + Quantities):**
```
┌──────────────────────────────────────────────┐
│ Shift #42 · Pump 3                [Customer?]│
│ [Walk-in]                                    │
├──────────────────────────────────────────────┤
│ ADDED ITEMS                          [clear] │
│ ┌──────────────────────────────────────────┐ │
│ │ Petrol MS · N3-Pump2 · 40L   ₹4,104  ✕ │ │
│ │ Engine Oil · — · 1            ₹350    ✕ │ │
│ └──────────────────────────────────────────┘ │
│                                              │
│ + ADD PRODUCT                                │
│ ┌──────────────────────────────────────────┐ │
│ │ PRODUCT (large buttons, color-coded)     │ │
│ │ [Petrol MS] [Diesel HSD] [XP95] [Oil]   │ │
│ │                                          │ │
│ │ NOZZLE (auto-filtered by product+pump)   │ │
│ │ [N1-Pump3] [N2-Pump3]                   │ │
│ │                                          │ │
│ │ QUANTITY       QUICK: [₹500] [₹1K] [₹2K]│ │
│ │ [  40  ]  [Liters | ₹Rupees]            │ │
│ │  7  8  9                                 │ │
│ │  4  5  6                                 │ │
│ │  1  2  3                                 │ │
│ │  C  0  .           [+ ADD TO BILL]       │ │
│ └──────────────────────────────────────────┘ │
├──────────────────────────────────────────────┤
│ TOTAL: ₹4,454  (2 items)        [NEXT →]    │
└──────────────────────────────────────────────┘
```

- Products from `GET /api/products/active` (all categories)
- Nozzles auto-filter: by product's tank AND by attendant's pump (if attendant role)
- Quick-amount buttons (₹500, ₹1000, ₹2000) for fuel
- Liters/Rupees toggle — Rupees mode calculates qty = amount / unitPrice
- "Customer?" opens search bottom sheet → customer → vehicles as chips
- Walk-in is default

**Step 2 — "PAY" (Payment + Confirm):**
```
┌──────────────────────────────────────────────┐
│ PAYMENT MODE                                 │
│ [CASH] [UPI] [CARD] [CHEQUE] [BANK]         │
├──────────────────────────────────────────────┤
│ DRIVER (optional, expandable)                │
│ [Name]  [Phone]                              │
├──────────────────────────────────────────────┤
│ INVOICE SUMMARY           Shift #42          │
│ ─────────────────────────────────────        │
│ Petrol MS  40L × ₹102.61      ₹4,104.40     │
│   Nozzle: N3-Pump2                           │
│ Engine Oil  1 × ₹350           ₹350.00      │
│ ─────────────────────────────────────        │
│ Customer: Walk-in                            │
│ GROSS:     ₹4,454.40                        │
│ Discount:  ₹0.00                            │
│ NET TOTAL: ₹4,454.40                        │
├──────────────────────────────────────────────┤
│ [← BACK]              [CONFIRM BILL ✓]      │
└──────────────────────────────────────────────┘
```

- CASH pre-selected
- CONFIRM → `POST /api/invoices` → success toast with billNo → reset form
- **Tablet**: both steps side-by-side (two-pane layout)

### 4. Pump Session (Attendant only — NEW)

**Start Session:**
```
┌──────────────────────────────────────────────┐
│ START PUMP SESSION                           │
├──────────────────────────────────────────────┤
│ Select Your Pump:                            │
│ [Pump 1]  [Pump 2]  [Pump 3]  [Pump 4]      │
├──────────────────────────────────────────────┤
│ OPENING METER READINGS (Pump 3)              │
│ ┌──────────────────────────────────────────┐ │
│ │ Nozzle 1 (Petrol MS)    [  45230.5  ]   │ │
│ │ Nozzle 2 (Diesel HSD)   [  32100.0  ]   │ │
│ └──────────────────────────────────────────┘ │
├──────────────────────────────────────────────┤
│                       [START SESSION ▶]      │
└──────────────────────────────────────────────┘
```

**End Session:**
```
┌──────────────────────────────────────────────┐
│ END PUMP SESSION · Pump 3                    │
│ Started: 6:00 AM · Duration: 8h 12m         │
├──────────────────────────────────────────────┤
│ CLOSING METER READINGS                       │
│ ┌──────────────────────────────────────────┐ │
│ │ Nozzle 1 (Petrol MS)    [  45890.2  ]   │ │
│ │ Nozzle 2 (Diesel HSD)   [  32450.0  ]   │ │
│ └──────────────────────────────────────────┘ │
├──────────────────────────────────────────────┤
│ SESSION SUMMARY                              │
│ ─────────────────────────────────────        │
│ Nozzle 1: 659.7 L  × ₹102.61 = ₹67,691     │
│ Nozzle 2: 350.0 L  × ₹89.50  = ₹31,325     │
│ ─────────────────────────────────────        │
│ TOTAL LITERS:  1,009.7 L                     │
│ TOTAL SALES:   ₹99,016                      │
├──────────────────────────────────────────────┤
│                       [END SESSION ■]        │
└──────────────────────────────────────────────┘
```

- Attendant picks their pump → enters opening readings per nozzle
- During session: can raise invoices (filtered to their pump's nozzles)
- End session: enter closing readings → app calculates liters sold + sales amount per nozzle
- Data saved to backend via new `PumpSession` API

### 5. Shift Invoices / My Sales
- **Cashier**: `GET /api/invoices/shift/{shiftId}` — all shift invoices
- **Attendant**: filtered to invoices from their pump session period
- List: billNo, customer, amount, payment mode, time
- Running totals header (count, total ₹, per-payment-mode breakdown)

## New Backend Work Required

### New Entity: `PumpSession`
```
PumpSession
  id, scid, shiftId
  pump (FK → Pump)
  attendant (FK → User)
  startTime, endTime
  status: OPEN / CLOSED
  readings: List<PumpSessionReading>

PumpSessionReading
  id, pumpSessionId
  nozzle (FK → Nozzle)
  openReading (BigDecimal)
  closeReading (BigDecimal)
  litersSold (BigDecimal)     — computed: close - open
  salesAmount (BigDecimal)    — computed: litersSold × unitPrice
```

### New API Endpoints
| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/pump-sessions` | POST | Start a pump session (open readings) |
| `/api/pump-sessions/active` | GET | Get attendant's active session |
| `/api/pump-sessions/{id}/close` | POST | Close session (closing readings, calc totals) |
| `/api/pump-sessions/{id}` | GET | Get session detail with summary |
| `/api/pump-sessions/shift/{shiftId}` | GET | All sessions in a shift (for manager view later) |

### Backend Files to Create
- `backend/src/main/java/com/stopforfuel/backend/entity/PumpSession.java`
- `backend/src/main/java/com/stopforfuel/backend/entity/PumpSessionReading.java`
- `backend/src/main/java/com/stopforfuel/backend/repository/PumpSessionRepository.java`
- `backend/src/main/java/com/stopforfuel/backend/service/PumpSessionService.java`
- `backend/src/main/java/com/stopforfuel/backend/controller/PumpSessionController.java`
- `backend/src/main/java/com/stopforfuel/backend/dto/PumpSessionDTO.java`

## Shift ID Strategy

- **Fetched on Home** via `GET /api/shifts/active` → held in `ShiftRepository` (Hilt singleton)
- **Gates invoice creation** — disabled if no active shift
- **Displayed on every screen** header
- **Backend is authoritative** — also validates independently on `POST /api/invoices`
- **Periodic refresh** on app resume

## Existing Backend APIs Used (no changes)

| Endpoint | Purpose |
|----------|---------|
| `POST /api/auth/login` | Phone + passcode login |
| `GET /api/shifts/active` | Get open shift |
| `GET /api/products/active` | Product list for buttons |
| `GET /api/nozzles/active` | Nozzle list (has tank.productId) |
| `GET /api/pumps/active` | Pump list (for session pump selection) |
| `GET /api/customers?search=X` | Customer search |
| `GET /api/customers/{id}/vehicles` | Vehicle picker |
| `POST /api/invoices` | Create invoice (multi-product) |
| `GET /api/invoices/shift/{shiftId}` | Shift invoice list |

## Build Order

| Phase | What | Details |
|-------|------|---------|
| **1. Backend — PumpSession** | New entity, repository, service, controller, DTO | Create PumpSession + PumpSessionReading entities, CRUD APIs, open/close logic |
| **2. Android Skeleton + Auth** | Project setup in `android/`, Hilt, Retrofit, TokenStore | Login screen with SIM phone auto-detect + PIN pad |
| **3. Home + Shift** | Home screen, shift fetch, role-based layout | Cashier vs attendant home views, nav graph |
| **4. Invoice Creation** | Multi-product invoice flow | Product grid, nozzle grid (pump-filtered for attendants), numpad, payment, confirm |
| **5. Pump Session** | Attendant pump session flow | Start session (pump select + open readings), end session (close readings + summary) |
| **6. History** | Shift invoices / My Sales | Invoice list, running totals, per-payment-mode breakdown |
| **7. Polish** | Tablet layout, error handling, theming | WindowSizeClass, loading states, brand colors |

## Key Design Decisions

- **Single app, role-based**: Cashier and attendant see different home screens based on `user.role`
- **Walk-in default**: Most cash sales are walk-in
- **Multi-product per invoice**: Fuel + lubricants in one bill
- **2-step invoice** (not 5): "what products" → "how paying"
- **Attendant nozzles filtered by pump**: When attendant has an active pump session, only their pump's nozzles show
- **PumpSession is personal tracking**: Doesn't affect main shift closing — purely attendant accountability
- **Auto-detect phone on login**: Fewer taps to log in
- **No offline/Room**: MVP, station has connectivity
- **No printing**: Defer to v2
- **Backend-first for PumpSession**: Server stores readings for future reporting

## Future Growth

| Version | Add |
|---------|-----|
| v2 | Thermal receipt printing, credit invoice support |
| v3 | Owner/manager screens (dashboards, reports, approvals) |
| v4 | Customer screens (balance, statements, payment history) |
| v5 | Room DB for offline invoice queue |
| v6 | Shift open/close from mobile, tank dip entry |

## Verification

1. **Backend**: `cd backend && ./gradlew bootRun` — test new PumpSession APIs via curl/Postman
2. **Android**: Open `android/` in Android Studio → build → run on emulator (API base: `http://10.0.2.2:8080`)
3. **Login**: Seeded employee phone + passcode "1234"
4. **Shift**: Ensure OPEN shift exists (via web app)
5. **Invoice**: Add petrol + oil → CASH → Confirm → verify in web app invoice list
6. **Pump Session**: Start session → log open readings → raise invoice → end session → verify totals
7. **Role test**: Login as cashier vs attendant → verify different home screens
