# Modern Kallaa Petti: Google Play Release Plan (v1)

Prepared on 29 October 2026, for version 2.1.0. Updated the same day: the app bundle, the
store graphics and the store texts are now ready (sections 2 and 4). The app is already published as a signed APK
on GitHub Releases (see `easybill_release-plan_v1.md`). This plan covers the additional steps
needed to publish the same app on Google Play. Play rules change often; each item marked
*check* should be confirmed in Play Console on the day it is done.

## 0. Summary

| Step | Who | Time |
|---|---|---|
| 1. Developer account (personal), identity verification | Owner | 2 to 7 days |
| 2. Android App Bundle (`.aab`) in the release workflow | Done | |
| 3. Play App Signing with the existing release key | Owner, with the steps below | 30 minutes |
| 4. Store listing (text, icon, feature graphic, screenshots) | Done; owner approves | |
| 5. App content forms (privacy, Data safety, rating, financial features) | Owner, with the answers below | 1 to 2 hours |
| 6. Closed test: 12 testers, 14 days without a break | Owner and testers | 14 days or more |
| 7. Apply for production access, then publish | Owner | up to about 7 days for review |

Table 1. Steps to the first Play release. The full path takes about three to four weeks,
most of it the closed test.

## 1. Developer account

- Register at play.google.com/console with the Google account that should own the app.
  One-time fee of US$25.
- **Account type: Personal**, unless the app is to be published under a registered
  organisation (for example Geospatial Campus as a legal entity). An organisation account
  needs a D-U-N-S number and the organisation's documents.
- Google verifies identity with a government ID and checks the phone number and email.
  Some contact details are shown on the store listing; read the form carefully before
  submitting.
- **Personal accounts created after 13 November 2023 must run a closed test with at least
  12 testers, opted in continuously for at least 14 days, before production is unlocked**
  ([Google Play Console Help, answer 14151465](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-GB)).
  Third-party guides report that some organisation accounts are exempt *(check)*.

## 2. App bundle

Play accepts only Android App Bundles (`.aab`) for new apps. **Done:** the release workflow
now also runs `./gradlew :app:bundleRelease`, signs the bundle with `jarsigner` using the same
keystore secrets, checks its certificate against `EXPECTED_CERT_SHA256` (the run stops on any
other certificate), and attaches `ModernKallaaPetti-vX.Y.Z.aab` and its `.sha256` to the GitHub
release next to the APK. Download the `.aab` from the release page and upload it to Play
Console. `tools/sign-release.sh` signs a locally built bundle the same way.

The first release that carries a bundle is the next one run through the workflow (2.1.0 was
published before this change). To get a bundle for 2.1.0 itself, run the workflow with
version `2.1.1` after raising the version, or sign a local build with the script.

The APK on GitHub and the bundle on Play share one `versionCode` sequence
(`MAJOR × 10000 + MINOR × 100 + PATCH`). A number can never be used twice on Play.

## 3. Signing: keep one key for GitHub and Play

Play App Signing is compulsory for new apps. Play re-signs every download with the **app
signing key**. If Play generates a new key, phones that installed the GitHub APK cannot
update from Play (and the other way round) without uninstalling.

**Recommendation: give Play the existing release key** (alias `kallaapetti`, SHA-256
`42:65:F6:84:…:F7:33:D0:F8`):

1. Play Console › the app › Test and release › Setup › App signing › choose to use your own
   key ("Export and upload a key from Java keystore").
2. Download Google's PEPK tool from that page and run it on the computer that holds
   `kallaa-petti-release.jks` (the page shows the exact command). It writes an encrypted
   file; upload that file.
3. Upload the first `.aab`, signed with the same key (it then also serves as the upload key).
4. After the upload, confirm that the SHA-256 shown under App signing equals the fingerprint
   above.

Never upload the `.jks` file or its password anywhere else. Keep both backup copies.

## 4. Store listing

**Done:** all texts and images are in `fastlane/metadata/android/en-IN/` (the layout read by
Play upload tools and by F-Droid). Choose **English (India) – en-IN** as the default language
in Play Console and paste from these files:

| File | Use in Play Console |
|---|---|
| `title.txt` | App name |
| `short_description.txt` | Short description |
| `full_description.txt` | Full description |
| `changelogs/20100.txt` | Release notes for 2.1.0 (versionCode 20100) |
| `images/icon.png` | App icon, 512 × 512 |
| `images/featureGraphic.png` | Feature graphic, 1024 × 500 |
| `images/phoneScreenshots/1.png` to `7.png` | Phone screenshots, 1080 × 1920, in this order |

Table 2a. Files to upload. The screenshots come from the real app with a demo shop
(`StoreScreenshotsTest`); `python3 tools/store/make_store_graphics.py` adds the captions and
rebuilds every image after a screen changes.

| Field | Limit | Draft |
|---|---|---|
| App name | 30 characters | Modern Kallaa Petti (19) |
| Short description | 80 characters | Free GST billing, stock, payments and reports for Indian shops. Works offline. (78) |
| Category | | Business |
| Contact email | | arancastro17@gmail.com |
| Website | | https://draran.online |
| Privacy policy | public web page, not a PDF | https://github.com/AranCastro/EasyBill/blob/main/docs/privacy-policy.md (or a copy on draran.online) |

Table 2. Store listing fields and drafts.

**Full description (draft, under 4,000 characters):**

> Modern Kallaa Petti is a free billing app for shops, traders, salons, clinics, tutors and
> other small businesses in India. Everything is stored on your phone. There is no sign-up,
> no advertisement and no subscription.
>
> Bills: GST and non-GST bills, estimates, purchase bills, sale and purchase returns, UPI QR
> on every bill, PDF sharing on WhatsApp, and Bluetooth thermal printing (58 mm and 80 mm).
> Counter billing for walk-in customers, with barcode scanning.
>
> Stock and parties: items with HSN, tax rate and stock alerts; customers and suppliers with
> balances, statements and payment reminders. Add a customer from the phone's contacts.
>
> Money: payments in and out, expenses, and Capital and loans to track your own money and
> loans from family or a bank until they are repaid.
>
> Reports: sales and purchase registers, profit and loss, day book, item-wise sales, stock,
> GST summary (GSTR-1 and GSTR-3B totals), party balances, expenses, cash flow, and capital
> and loans, as PDF or Excel (CSV).
>
> Safety: backup to Google Drive or a pen drive, restore with checks, and an optional app
> lock. Free and open source under the MIT licence.

**Graphics (Claude can produce these from the app's own screens and icon):**

- App icon: 512 × 512 px, 32-bit PNG, at most 1 MB.
- Feature graphic: 1024 × 500 px, JPEG or 24-bit PNG without transparency.
- Phone screenshots: two to eight, JPEG or 24-bit PNG, each side 320 to 3,840 px, and the
  long side no more than twice the short side. The current test screenshots (1233 × 2673)
  are too tall for this rule, so new ones will be made at 1080 × 1920.

## 5. App content forms (Play Console › Policy › App content)

| Form | Answer |
|---|---|
| Privacy policy | The URL in Table 2 |
| Ads | No ads |
| App access | All features work without an account or login |
| Content rating (IARC questionnaire) | Business utility; no violence, gambling, user-generated content shared with others or location sharing. Expected rating: Everyone / 3+ |
| Target audience | 18 and over (a business tool, not designed for children) |
| News app | No |
| Government app | No |
| Financial features | The app keeps the shop's own records only. It does not lend money, arrange loans, take payments or hold money. Capital and loans is a record of money the owner borrowed, not a loan product. Answer that the app provides no financial services *(check the exact wording of the options)* |
| Health | No |
| Data safety | See below |

Table 3. App content declarations.

**Data safety.** The app's own code sends nothing off the phone; bills, parties and reports
stay in its database. Two Google components need care:

- **Google code scanner (ML Kit, through Google Play services).** Google's ML Kit data
  disclosure lists device information, application information, device or other identifiers,
  performance metrics, API configuration and error codes, sent to Google over HTTPS for
  diagnostics and not passed to third parties
  ([ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)).
  Declare these as collected (App info and performance; Device or other IDs), purpose
  "Analytics" or "App functionality", encrypted in transit, not shared.
- **Android Auto Backup** copies the database to the user's own Google account. This is a
  system service under the user's control, not collection by the developer.

Data deletion: the user deletes data by clearing app storage or uninstalling; no account
exists, so no account-deletion page is needed.

**Permissions to explain if Play asks:** Internet (used by Google Play services for the
scanner module), Bluetooth connect and scan (thermal printer). The app does not ask for the
camera, contacts, location, SMS or storage permissions.

## 6. Closed test (14 days)

1. Play Console › Testing › Closed testing › create a track, upload the `.aab`.
2. Add at least 15 testers (more than 12, in case some drop out) by email list or a
   Google Group. Good candidates: Geospatial Campus colleagues, family, and shop owners
   who already use the GitHub APK, such as Mansoor Bohari.
3. Each tester opens the opt-in link, accepts, and installs from Play. Testers who use the
   GitHub APK must back up first, uninstall it, install from Play, then restore. This step
   is not needed if Play uses the same signing key (section 3).
4. Keep all testers opted in for 14 days in a row. Ask them to use the app at least every
   few days and to send feedback; publish one small update during the test if possible.
5. Day 15: Dashboard › Apply for production. Answer the questions about the test, the
   feedback received and the changes made.

## 7. Production release

1. Production › Create release › upload the `.aab` (or promote the tested release).
2. Release notes from the README changelog (Indian English, under 500 characters per
   language).
3. Countries: India first; add others later if wanted.
4. Staged rollout: 20 per cent for two days, then 100 per cent if there are no crashes in
   Play Console › Quality › Android vitals.
5. Upload the R8 mapping file for that version (it is attached to each GitHub release) so
   crash reports are readable.

## 8. After launch

- Every later version: raise `versionName` and `versionCode`, run the release workflow
  (APK and `.aab`), upload the `.aab` to Play, publish the same version on GitHub.
- Answer Play policy emails promptly; most give 30 days.
- **Android developer verification** for apps installed outside Play started on
  30 September 2026 in Brazil, Indonesia, Singapore and Thailand, with a global rollout
  planned for 2027; India is not in the first group
  ([Android Authority](https://androidauthority.com/android-sideloading-developer-verification-first-wave-rollout-3717921)).
  A verified Play developer account is expected to cover the GitHub APK as well *(check when
  the rule reaches India)*.

## 9. Status

Done: the signed `.aab` in the release workflow (section 2), the icon, feature graphic and
seven screenshots, and the store texts with release notes (section 4). The remaining steps
need the owner: the developer account (section 1), Play App Signing with the existing key
(section 3), the App content forms (section 5), and the closed test (section 6).
