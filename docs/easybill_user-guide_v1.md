# Modern Kallaa Petti — User Guide (v1.4.0)

## 1. Install
1. Copy the APK to the phone (WhatsApp to yourself, USB or Google Drive) and tap it.
2. Allow installing from that source when Android asks, then tap **Install**.
3. If Play Protect warns "unknown developer", choose **More details › Install anyway**.

Android 8.0 or newer is required. The app needs no internet connection.

## 2. First setup (one screen)
- **Type of business**: Shop / Retail, School / Coaching / Training,
  Research / Consultancy / Lab, Salon / Beauty / Spa, Clinic / Healthcare,
  Repair / Service centre or Freelancer / Professional. The type sets the words
  in the app (Student, Client, Patient; Fees, Services), the bill title when GST
  is off (Fee Receipt, Bill, Job Invoice) and the extra bill fields. Tick
  **Add starter services** to load a sample price list (edit the prices later).
- **Business logo**: tap **Upload logo** and pick an image. A square logo on a
  plain background prints best.
- **Business name**, mobile number, address and **state** (decides CGST + SGST or IGST).
- Turn on **GST registered** and enter your GSTIN if you charge GST. Leave it off
  for simple bills without tax (non-GST or composition shops).
- **MSME / Udyam (optional)**: if your business has an Udyam registration,
  enter the number (e.g. UDYAM-TN-02-0012345) and choose **Micro**, **Small** or
  **Medium** as on the certificate. It prints under the GSTIN on every bill.
  For micro and small enterprises, sale bills also carry a note that business
  buyers must pay within the agreed period, not later than 45 days, under
  Section 15 of the MSMED Act, 2006. Turn the note off in **Settings › Invoice
  settings › MSME payment note**. Leave the field blank if you are not
  registered.
- Add your **UPI ID** to print a payment QR code on every bill.

- **Bill colour**: after you upload a logo, the app takes its main colour for
  your bills (top bar, title, table header and total). Colours from the logo
  are shown first under **Bill colour**, followed by twelve ready-made colours;
  the small bill above them shows how it will look. Very light colours are
  darkened a little so the white text on the total stays readable.
- **Authorised signatory**: tap **Upload** to use a photo of your signature on
  white paper (the paper is removed automatically), or **Sign here** to sign
  with a finger. Add the signatory's name and designation (e.g. Proprietor,
  Principal, Director). The signature prints above "Authorised signatory" on
  every A4 bill.

Everything can be changed later in **More › Settings › Business profile**.

### Bill fields
Service types add fields such as Roll / Admission no. (schools), Stylist
(salons), Job card no. (repairs) or PO / Work order no. (research and
consultancy). Fill them under **Bill details** in the bill editor; filled fields
print in the bill's Details box and on thermal receipts. Rename, clear or add
fields (up to four) in **Settings › Invoice settings › Bill fields**, for
example "Vehicle no." for a transporter. Invoice-style types also offer an
optional **Due date**.

## 3. Add items and parties
- **Items tab › Add item**: name, sale price, unit, purchase price (for profit),
  GST rate and HSN, opening stock and a low-stock alert. Scan the barcode to
  bill it later with the scanner. Mark best sellers as **favourite** to show them
  as tiles in Counter billing.
- **Parties tab › Add party**: customers and suppliers, with phone, GSTIN, state
  and any amount pending from before (opening balance).

## 4. Make a bill
- Tap **New Sale** (Home or Sales tab).
- The customer is **Cash Customer** by default. Tap **Change** to pick a party
  or add one.
- Tap **Tap to add items**, tap items (or scan), adjust quantity with + / −.
  Tap a line to change price, discount or GST.
- **Fully received** is on for cash sales. Turn it off for credit and enter the
  amount received now; the rest goes to the party's balance.
- Tap **Save**. On the bill screen: **WhatsApp**, **Share PDF**, **Print**
  (Wi-Fi printers / Save as PDF) or **Thermal** (Bluetooth receipt printer).

After saving, a green **Saved** banner confirms the bill number and total.
Amounts can be typed with Indian grouping (1,00,000) or with a decimal comma
(12,50 is read as ₹12.50).

Purchases, estimates and returns use the same screen: **More › Purchases /
Estimates**, or the menu (⋮) on a bill for **Sale return** and **Duplicate**.
Open an estimate and tap **Convert to sale invoice** when the customer confirms.

A **sale return** (credit note) for a customer first reduces what is due on
that customer's oldest unpaid bills; a **purchase return** (debit note) does the
same for what you owe a supplier. A return larger than what is due stays as a
balance in the party's favour, to be settled by a refund (Payment out or in).

### The Sales list and Home screen
- The Sales list groups bills by day, with the number of bills and the day's
  total. Bills past their due date carry a red **Overdue · N days** label; the
  **Overdue** filter shows only those.
- Home shows today's sales, an **Overdue** card when any bill is past its due
  date (tap it to open the overdue list), and this month's **Top sellers**. The
  share button on the sales card sends today's summary (sales, received, bills)
  to WhatsApp or any app.

## 5. Counter billing (walk-in customers)
**More › Counter billing** (or the Counter button on Home): tap tiles, then
**Charge**. Choose Cash (enter cash given to see change), UPI (the customer
scans the QR on your screen) or Card, then **Paid · Save bill**.

## 6. Payments and expenses
- **Payment in / Payment out**: from a party's page or **More › Payments**. The
  amount settles that party's oldest unpaid bills first.
- **Remind** on a party page opens WhatsApp with a polite reminder and your UPI ID.
- **More › Expenses** for rent, salary, electricity and other costs.

## 7. Reports
**More › Reports**: sales and purchase registers, profit & loss, day book,
item-wise sales, stock, GST summary (GSTR-1 and GSTR-3B totals), party balances,
expenses and cash flow. Pick a period (today, 7 days, this month, last month,
this financial year or custom) and use the share button for PDF or Excel (CSV).
The GST summary helps you or your accountant file returns; verify figures on the
GST portal before filing.

## 8. Thermal printer
Pair the printer in the phone's Bluetooth settings (PIN usually 0000 or 1234).
Then **Settings › Printer**: allow Nearby devices, choose the printer, set 58 mm
or 80 mm paper and tap **Print test page**. **Print logo on receipts** prints
your logo in black and white at the top of each receipt.

### Tamil and other scripts on receipts
Thermal printers cannot print Tamil, Hindi or other non-English letters in
their own font, so the app prints those lines as small pictures. They look
slightly larger and take a little longer to print. English lines print as
normal text.

## 9. Backup — do this every week
Your data lives only on your phone. **More › Backup & restore › Back up now**
and choose **Google Drive** in the file picker (free) or Downloads. On a new
phone, install the app and use **Restore from file**. The app also keeps a daily
copy inside the phone (last 7 days) to undo mistakes. Before a restore replaces
your data, the app checks the file and keeps the current database as a safety
copy, so a bad file cannot wipe your records. That copy appears in the list of
automatic copies marked **Before restore**; tap **Restore** on it to undo a
restore. Backups include your logo and signature. If the app stays open all
day, the daily copy is still made (the app checks every hour).

## 10. App lock
**Settings › Security › App lock** asks for your fingerprint, face or phone
screen lock (PIN, pattern, password) when the app opens and again after the app
has been in the background for a minute. A bill you were making is still there
after unlocking. The switch is available only when the phone has a screen lock;
if the screen lock is later removed, the app opens without asking.
