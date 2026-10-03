# Modern Kallaa Petti — Plan: Logo, Signature and Industry Modes (v1)

Version target: **v1.1.0**. Requested on 3 October 2026.

## 1. Requirements

| # | Requirement | Priority |
|---|---|---|
| R1 | Upload a company logo; print it on bills, statements, reports and thermal receipts | Must |
| R2 | Authorised signatory: upload a signature image (or draw it), with name and designation, printed on bills | Must |
| R3 | Bills suited to service businesses: education, research, hair salon and similar | Must |

## 2. Logo (R1)

- Picked with the Android Photo Picker (no storage permission), scaled to at most
  600 px, saved as PNG inside the app (`files/branding/logo.png`).
- Shown in: A4 invoice header (top-left, 56 pt box), party statements and report
  PDFs, Bluetooth thermal receipts (printed as an ESC/POS raster image, black and
  white), dashboard header and Business profile.
- Included in backup files and restored with them.

## 3. Authorised signature (R2)

- Two ways: **upload a photo** of a signature on white paper (the paper background
  is removed automatically so the ink sits cleanly on the bill), or **draw** it on
  the screen with a finger.
- Signatory **name** and **designation** (e.g. Proprietor, Principal, Director).
- Printed above the "Authorised signatory" line on every A4 bill. Stored at
  `files/branding/signature.png`; included in backups.

## 4. Industry modes (R3)

A **business type** chosen at setup (changeable in Business profile) adjusts
words, bill fields, defaults and a starter list of services. Everything remains
editable; the type only sets sensible defaults.

| Type | Customer called | Catalogue called | Bill title (no GST) | Extra bill fields | Stock |
|---|---|---|---|---|---|
| Shop / Retail | Customer | Items | Bill of Supply | — | On |
| School / Coaching / Training | Student | Fee heads | Fee Receipt | Roll / Admission no., Class / Course, Batch / Section, Fee period | Off |
| Research / Consultancy / Lab | Client | Services | Invoice | PO / Work order no., Project, Service period, Milestone | Off |
| Salon / Beauty / Spa | Customer | Services | Bill | Stylist, Appointment time | Off |
| Clinic / Healthcare | Patient | Services | Bill / Receipt | Doctor, Age / Gender, OP no. | Off |
| Repair / Service centre | Customer | Services & parts | Job Invoice | Job card no., Device / Vehicle, Serial / Reg. no., Technician | On (parts) |
| Freelancer / Professional | Client | Services | Invoice | Project, Reference / PO no. | Off |

When GST is on, sale bills are always titled **Tax Invoice**, as GST rules require.

### 4.1 Starter services (optional, loaded on request)

SAC headings (4-digit) are used, which the GST rules allow for businesses with
turnover up to ₹5 crore. GST rates are defaults only and must be confirmed with a
tax adviser; education and healthcare services are often exempt, while
commercial coaching, salons, repairs and consultancy are generally taxed at 18 %.

| Type | Examples (SAC, default GST) |
|---|---|
| Education | Tuition fee, Admission fee, Exam fee, Lab fee, Library fee, Transport fee (SAC 9992, 0 %) |
| Research | Research project fee (9981, 18 %), Consultancy per day, Data analysis, Report preparation, GIS mapping & analysis, Training workshop (9983, 18 %) |
| Salon | Haircut, Kids haircut, Shave, Beard trim, Hair colour, Hair spa, Facial, Head massage, Threading, Waxing, Manicure, Pedicure (9997, 18 %) |
| Clinic | Consultation, Follow-up visit, Dressing, Injection, ECG (9993, 0 %) |
| Repair | Inspection charge, Labour charge, Service charge (9987, 18 %) |
| Freelancer | Professional fees, Hourly consulting, Design work (9983, 18 %) |

### 4.2 Custom bill fields

- Up to four extra fields per business; labels come from the business type and
  can be renamed or cleared in **Invoice settings** (useful for any trade, e.g.
  "Vehicle no." for a transporter).
- Filled in the bill editor under **Bill details**; printed in the bill's
  Details box and on thermal receipts.
- An optional **Due date** for invoices (research, professional and repair).

## 5. Data changes

- Database version 1 → 2 with a Room auto-migration (new columns with defaults);
  existing bills and settings stay intact. A migration test guards this.
- `business`: `businessType`, `logoFile`, `signatureFile`, `signatoryName`,
  `signatoryDesignation`, `customFields`, `printLogoOnReceipt`.
- `invoice`: `customFields` (label/value pairs).

## 6. Acceptance checks

1. Logo and signature appear on the rendered A4 bill image in tests.
2. Thermal receipt bytes contain a raster image command when a logo is set.
3. A database created by v1.0.0 opens in v1.1.0 with all data.
4. A salon bill and an education fee receipt can be created end to end, with the
   right words and fields on screen and in the PDF.
5. Backup → restore brings back logo and signature.
