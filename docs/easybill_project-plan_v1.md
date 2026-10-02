# EasyBill — Project Plan (v1)

An offline-first billing and inventory app for Android, aimed at small Indian
businesses (retail shops, wholesalers, service providers). Functional reference:
Vyapar. Design goals: fast bill creation, a clean and rich interface, and zero
running cost.

---

## 1. Goals and Constraints

| # | Goal / Constraint | What it means for the build |
|---|---|---|
| G1 | **Zero budget** | Only free and open-source tools and libraries. No paid APIs, no servers, no SaaS. |
| G2 | **Fast billing** | A cash sale must be completable in under 10 seconds and 5 taps or fewer. |
| G3 | **Clean, rich UI** | Material 3 design system with custom branding, dark mode, motion, and charts. |
| G4 | **Works offline** | All data on the device. No internet needed for any core feature. |
| G5 | **GST-ready** | Tax invoices that meet Rule 46 of the CGST Rules, 2017 (Indian GST). |
| G6 | **Data safety** | Automatic local backups plus user-controlled export/restore. |

**Why offline-first fits a zero budget:** a server (database hosting, sync, auth)
is the main recurring cost in apps like Vyapar. Keeping everything on-device
removes that cost entirely. Cloud sync can be added later with a free tier if
needed (see Phase 4).

---

## 2. Zero-Cost Toolchain

| Need | Tool | Cost |
|---|---|---|
| IDE | Android Studio (stable) | Free |
| Language | Kotlin | Free |
| Source control and CI | GitHub + GitHub Actions (free minutes for public repos; limited free minutes for private) | Free |
| Design mock-ups | Figma (free plan) or directly in Compose Previews | Free |
| Icons | Material Symbols (Apache 2.0) | Free |
| Fonts | Inter or Manrope for UI, Noto Sans for Indian scripts (SIL Open Font License) | Free |
| Illustrations / animation | Lottie files from free community sets, unDraw illustrations | Free |
| Test devices | Android Emulator + your own phone | Free |
| Crash reports (optional) | Firebase Crashlytics (free) — or none, to stay fully offline | Free |
| Distribution | GitHub Releases / direct APK / F-Droid | Free |
| Distribution (Play Store) | Google Play Console | **US$25 one-time** — the only unavoidable cost if you want the Play Store |

---

## 3. Recommended Technology Stack

**Choice: native Android with Kotlin + Jetpack Compose.**

Reasons over Flutter / React Native for this project:
- Fastest cold start and scroll performance on low-end Android phones, which
  most small shop owners use.
- Direct access to Bluetooth printing, the PDF API, and the camera without
  plugin bridges.
- Material 3 is native to Compose, so a polished UI needs less custom work.
- Android-only target, so cross-platform reach is not needed.

| Layer | Library | Purpose |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Screens, theming, dynamic colour, dark mode |
| Navigation | Navigation Compose (type-safe routes) | Single-activity navigation |
| State | ViewModel + Kotlin Coroutines / Flow | Unidirectional data flow |
| DI | Hilt | Dependency injection |
| Database | Room (SQLite) with FTS4 | Local storage, fast full-text item/party search |
| Preferences | DataStore | Settings, invoice preferences |
| Background work | WorkManager | Scheduled auto-backup |
| Charts | Vico (Apache 2.0) | Dashboard sales/expense charts |
| Images | Coil | Logo, item photos |
| Animation | Lottie Compose (Apache 2.0) | Empty states, success animations |
| Barcode scanning | Google ML Kit Barcode Scanning (free, on-device) + CameraX | Scan item barcodes |
| QR generation | ZXing core (Apache 2.0) | UPI payment QR on invoices |
| PDF | Android `PdfDocument` (built-in) | Invoice PDF generation |
| Thermal printing | DantSu ESCPOS-ThermalPrinter-Android (MIT) | 58 mm / 80 mm Bluetooth printers |
| Excel export | FastExcel or plain CSV | Report exports |
| Testing | JUnit, Turbine, Compose UI Test, Room in-memory DB | Unit, UI, and DB tests |
| Performance | Baseline Profiles + Macrobenchmark | Fast cold start and scroll |

All versions are pinned in a Gradle version catalog (`gradle/libs.versions.toml`)
at the latest stable release when the project is created.

---

## 4. Feature Scope by Phase

### Phase 1 — MVP (core billing)
1. **Onboarding** — business name, logo, address, GSTIN (optional), state,
   phone, UPI ID, invoice prefix. Completed in one screen.
2. **Items** — name, sale price, purchase price, unit, HSN/SAC, GST rate,
   barcode, opening stock, low-stock alert level, category.
3. **Parties** — customers and suppliers: name, phone, GSTIN, state, address,
   opening balance.
4. **Sale invoice** — add items by search or barcode scan, quantity, price,
   discount (per line and on total), GST auto-split (CGST + SGST or IGST),
   round-off, payment received (cash / UPI / card / credit), notes.
5. **Cash sale (counter) mode** — default "Cash Customer", item grid of
   favourites, tap to add, single "Save & Print" button.
6. **Invoice output** — PDF (A4 and A5), share to WhatsApp / any app,
   Bluetooth thermal print, UPI QR code printed on the bill.
7. **Payment In** — record payments against outstanding invoices.
8. **Dashboard** — today's sales, amount receivable, amount payable, low-stock
   count, 7-day sales chart, recent transactions.
9. **Backup / Restore** — one-tap export to a file (user can pick Google Drive
   through the system file picker), daily automatic local backup.
10. **Settings** — theme (light / dark / system), invoice prefix and numbering,
    default tax mode, print size.

### Phase 2 — Full trading cycle
- Purchase bills, Payment Out, purchase returns
- Estimates / quotations, convert estimate → invoice in one tap
- Sale returns (credit notes), delivery challans
- Expenses with categories
- Stock adjustment, stock movement history per item
- Party ledger (statement) with PDF share
- Payment reminders via WhatsApp share (pre-filled message, no paid API)

### Phase 3 — Reports and GST
- Sales, purchase, and day book reports with date filters
- Profit and loss (simple), stock summary, item-wise profit
- GST reports: GSTR-1 summary (B2B, B2C, HSN summary), GSTR-3B summary
- Export to PDF / Excel / CSV
- Multiple invoice themes (4–6 designs), custom terms and signature image

### Phase 4 — Extras (only if needed)
- Multiple businesses (firms) in one app
- App lock (PIN / fingerprint using BiometricPrompt)
- Hindi and regional language UI
- Optional cloud sync using a free tier (e.g. Firebase Spark plan or
  Supabase free tier), with clear limits
- Home-screen widget and launcher shortcuts ("New Sale")

### Out of scope (costs money or needs registration)
- **E-invoicing (IRN generation)** — requires a GST Suvidha Provider (GSP);
  mandatory only for businesses with aggregate annual turnover above ₹5 crore.
- **E-way bill generation via API** — requires GSP access.
- **SMS sending** — paid gateways; WhatsApp/share intents are used instead.
- **In-app payment collection** — payment gateways charge fees; a static UPI QR
  is used instead.

---

## 5. Fast-Billing Design Rules

These rules apply to every screen in the billing flow.

1. **One tap to start a bill** — floating "New Sale" button on every main tab,
   plus a launcher shortcut (long-press app icon).
2. **No mandatory customer** — defaults to "Cash Customer"; party selection is
   optional.
3. **Search-as-you-type** — Room FTS4 index on item name, code, and barcode;
   results within one frame of typing.
4. **Continuous barcode scanning** — scanner stays open; each scan adds or
   increments the item.
5. **Smart defaults** — remember last price per party, last payment mode, and
   last used tax setting.
6. **Numeric keypad first** — quantity and price fields open the number pad
   directly; quantity stepper (+/−) on each line.
7. **Combined actions** — "Save & Print", "Save & Share", "Save & New" as
   single buttons.
8. **Undo, not confirm** — deletions show an Undo snackbar instead of a
   confirmation dialog.
9. **No network waits** — every operation is local.

### Performance budgets (measured on a mid-range device)

| Metric | Target |
|---|---|
| Cold start to dashboard | < 1.0 s |
| Item search with 10,000 items | < 50 ms |
| Save invoice | < 100 ms |
| Generate invoice PDF | < 1 s |
| List scrolling | 60 fps, no dropped frames |
| APK size | < 15 MB |

---

## 6. UI / Design System

- **Material 3** with a custom brand palette (primary: deep indigo or teal,
  accent for amounts due in amber, success in green, errors in red).
- **Dynamic colour** (Android 12+) as an optional setting; brand colours by
  default for consistent invoices and screenshots.
- **Light and dark themes**, both fully designed.
- **Typography:** Inter / Manrope for UI; tabular (monospaced) digits for all
  amounts so columns align; ₹ symbol and Indian digit grouping (1,23,456.00).
- **Spacing:** 4 dp / 8 dp grid; rounded cards (16 dp corners); soft elevation.
- **Components to build once and reuse:** KPI card, amount text, party chip,
  item row, quantity stepper, bottom-sheet pickers, empty state with Lottie,
  date-range filter bar, search bar.
- **Motion:** shared-element transitions from list to detail, animated totals
  when items are added, success tick animation after save.
- **Screens (MVP):** Dashboard · Sales list · New Sale · Counter mode · Items ·
  Item editor · Parties · Party detail/ledger · Payment In · Invoice preview ·
  Settings · Backup.
- **Accessibility:** minimum 48 dp touch targets, contrast ratio ≥ 4.5:1,
  TalkBack labels, font scaling up to 200 %.

The design system lives in its own module (`core/designsystem`) so every
feature uses the same tokens and components.

---

## 7. Architecture

Pattern: **MVVM with unidirectional data flow**, single activity, layered as
UI → Domain → Data.

```
app/                      Application class, MainActivity, navigation graph
core/
  model/                  Plain Kotlin data classes (Invoice, Item, Party, Money …)
  designsystem/           Theme, colours, typography, reusable composables
  database/               Room entities, DAOs, migrations, FTS tables
  data/                   Repositories (single source of truth)
  domain/                 Use cases + tax engine (pure Kotlin, fully unit-tested)
  common/                 Formatting (₹, amount in words), dates, dispatchers
  print/                  PDF renderer, thermal printer (ESC/POS), templates
feature/
  dashboard/  billing/  items/  parties/  payments/
  reports/    settings/ backup/ onboarding/
```

Key technical decisions:
- **Money stored as `Long` paise**, never `Double`, to avoid rounding errors.
- **Quantity stored as `Long` thousandths** (3 decimal places, for kg / litre).
- **Tax rates stored as basis points** (18 % = 1800).
- **Tax engine is pure Kotlin** with no Android dependency, so it can be tested
  exhaustively with unit tests.
- **Room schema exported** and every change has a written migration — user
  data must never be lost on update.
- **Invoice numbering** per series and per financial year (April–March), unique,
  maximum 16 characters (Rule 46(b), CGST Rules).

---

## 8. Data Model (Room tables)

| Table | Key fields |
|---|---|
| `business` | name, logo, address, state_code, gstin, phone, email, upi_id, bank details, signature |
| `party` | name, type (customer/supplier), phone, gstin, state_code, billing/shipping address, opening_balance |
| `item` | name, code, barcode, unit, hsn_sac, sale_price, purchase_price, tax_rate_bp, tax_inclusive, category_id, low_stock_qty, is_favourite |
| `item_fts` | FTS4 shadow table over item name, code, barcode |
| `category` | name, colour |
| `invoice` | type (SALE, PURCHASE, ESTIMATE, SALE_RETURN, PURCHASE_RETURN, CHALLAN), series, number, date, party_id, place_of_supply, subtotal, discount, cgst, sgst, igst, cess, round_off, total, paid, balance, status |
| `invoice_line` | invoice_id, item_id, description snapshot, hsn snapshot, qty, unit, rate, discount, tax_rate_bp, taxable_value, tax amounts, line_total |
| `payment` | direction (IN/OUT), party_id, date, amount, mode (cash/UPI/card/bank/cheque), reference |
| `payment_allocation` | payment_id, invoice_id, amount |
| `expense` | category_id, date, amount, mode, note |
| `stock_movement` | item_id, date, qty_change, reason (sale/purchase/return/adjustment), ref_id |
| `invoice_series` | prefix, financial_year, next_number |

Line items keep **snapshots** (name, HSN, rate, tax) so old invoices never
change when an item is edited later.

---

## 9. Indian GST Rules to Implement

1. **Tax split** — if the business state equals the place of supply:
   CGST + SGST (each half the rate); otherwise IGST (full rate).
2. **Rates** — 0, 5, 12, 18, 28 % slabs plus cess, held in an editable table
   rather than hard-coded, because rates are revised by the GST Council.
3. **GSTIN validation** — 15-character format check (state code + PAN + entity
   + `Z` + check digit) including the check-digit algorithm.
4. **Invoice content (Rule 46)** — supplier name/address/GSTIN, serial number,
   date, recipient GSTIN (B2B), HSN/SAC, description, quantity, unit, value,
   taxable value, rate and amount of each tax, place of supply, signature.
5. **Bill of Supply** — for non-GST or composition-scheme businesses, no tax
   columns.
6. **Tax-inclusive and tax-exclusive pricing** per item.
7. **Amount in words** using the Indian system (lakh, crore).
8. **HSN summary** on the invoice and in GSTR-1 export.

All GST logic must be verified against the current CGST Rules and CBIC
notifications before release; rates and thresholds change.

---

## 10. Printing and Sharing

- **PDF:** A4, A5, and thermal-width (58 mm / 80 mm) layouts, all drawn from the
  same invoice model with `PdfDocument` + Canvas.
- **Thermal printing:** Bluetooth ESC/POS printers; save the paired printer
  once, print with one tap afterwards.
- **Share:** `FileProvider` + share intent to WhatsApp, email, or any app.
- **UPI QR:** generated locally from
  `upi://pay?pa=<UPI_ID>&pn=<Business>&am=<Amount>&cu=INR&tn=<Invoice No>`
  — customers scan and pay with any UPI app; no gateway fee.

---

## 11. Backup and Data Safety

Because there is no server, data protection is a priority.

1. **Auto backup (daily)** with WorkManager — zipped database copy in app
   storage, last 7 kept.
2. **Manual export / restore** — a `.easybill` backup file saved through the
   Storage Access Framework, so the user can choose Google Drive, a pen drive,
   or local storage at no cost to the developer.
3. **Android Auto Backup** — enabled for the database (Google stores up to
   25 MB per app in the user's own Drive).
4. **Optional encryption** of backup files with a user password.
5. **Restore test** included in the automated test suite.

---

## 12. Quality and Testing

| Level | What is tested |
|---|---|
| Unit | Tax engine, totals, rounding, amount-in-words, GSTIN check digit, invoice numbering |
| Database | DAOs, FTS search, migrations (Room `MigrationTestHelper`) |
| UI | Billing flow: add items → save → print/share (Compose UI tests) |
| Performance | Macrobenchmark for cold start and item search; Baseline Profile generation |
| CI | GitHub Actions: build, lint (ktlint/detekt), unit tests on every push |

---

## 13. Release and Distribution

1. **Signing key** — generated locally, backed up in two places (loss means
   no future updates on the Play Store).
2. **Free route** — signed APK on GitHub Releases; optionally F-Droid
   (requires fully open-source code, no Firebase).
3. **Play Store route** — US$25 one-time; needs a privacy policy (can be hosted
   free on GitHub Pages) and a Data Safety form ("no data collected" if fully
   offline).
4. **Name check** — confirm "EasyBill" is not already used on the Play Store or
   trademarked before publishing; pick an alternative if it is.

---

## 14. Milestones (solo developer, approx. 15–20 hours/week)

| Phase | Work | Duration |
|---|---|---|
| 0 | Project setup, modules, CI, design system, theme, navigation shell | 1–2 weeks |
| 1a | Database, items, parties, onboarding | 2 weeks |
| 1b | Sale invoice, counter mode, tax engine, Payment In | 3 weeks |
| 1c | PDF, thermal print, share, UPI QR, dashboard, backup | 2–3 weeks |
| — | **MVP beta to 5–10 real shop owners** | 2 weeks of feedback |
| 2 | Purchases, estimates, returns, expenses, ledger | 4 weeks |
| 3 | Reports, GST reports, invoice themes | 3–4 weeks |
| 4 | Extras as demanded by users | Ongoing |

**MVP in roughly 10–12 weeks; Phases 1–3 in roughly 5–6 months.**

---

## 15. Risks and Mitigation

| Risk | Mitigation |
|---|---|
| Data loss (phone lost/reset) | Auto backup + prominent "Last backup" status on dashboard + reminder if no export in 7 days |
| GST calculation errors | Pure-Kotlin tax engine with exhaustive unit tests; cross-check sample invoices with a chartered accountant |
| Thermal printer compatibility | Test with 2–3 common 58 mm models; ESC/POS is widely supported |
| Scope creep (Vyapar has hundreds of features) | Strict phase gates; ship MVP first, add only what beta users ask for |
| Slow performance on old phones | Performance budgets enforced with Macrobenchmark in CI |
| Database migration bugs on update | Exported schemas + migration tests for every version |

---

## 16. Decisions Needed Before Coding

1. **Primary users:** GST-registered businesses, non-GST small shops, or both
   (recommended: both, with GST as a toggle)?
2. **Distribution:** Play Store (US$25 one-time) or free APK / GitHub only?
3. **Language:** English only for MVP, or Hindi / a regional language from the
   start?
4. **Thermal printing in MVP:** yes (recommended for retail) or defer to
   Phase 2?
5. **Brand:** keep the name "EasyBill"? Preferred primary colour?
6. **Minimum Android version:** recommended Android 8.0 (API 26), which covers
   nearly all active devices in India.

---

## Next Step

After the decisions above are confirmed, Phase 0 begins: create the Android
project with the module layout in Section 7, set up the version catalog, CI,
and the design system, and deliver a running app shell with the dashboard and
navigation in place.
