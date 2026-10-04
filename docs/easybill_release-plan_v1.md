# Modern Kallaa Petti: Release Plan (v1)

Status on 4 October 2026 (updated for 2.0.0). This plan covers the first production release signed
with a real release key. It was prepared after a strict audit of the 1.4.0 code
(money and data, printing and reports, screens, user-facing text, release
settings). The audit findings that affect users are fixed in 2.0.0; this
document lists what the owner must still decide and do.

## 0. Checklist for the first public release (do these in order)

**Status (4 October 2026):** `main` is the default branch (step 1 done). The release key has
been created (steps 2 and 3: key file and note delivered to the owner). Certificate SHA-256:
`42:65:F6:84:1D:D6:FC:BC:AA:6A:B3:0F:4F:4D:7C:1C:3A:12:C3:0A:6D:30:AA:4A:EC:8E:36:7E:F7:33:D0:F8`.
Step 4 (GitHub secrets and variable) done. Next: the test run (step 5).

On Windows use PowerShell and Git for Windows. The code is already on the `main` branch.

1. **Make `main` the default branch.** GitHub › your repository › Settings › Branches ›
   change the default branch to `main`. Afterwards the old branch
   `claude/vigilant-newton-5zeowg` can be deleted (Settings › Branches, or the branch list).
2. **Create the release key on your own computer** (once). From the repository folder:
   `powershell -ExecutionPolicy Bypass -File tools\make-release-keystore.ps1`
   (needs `keytool`, which comes with Android Studio or any JDK; the script looks for it).
   It asks for a password, writes the key to `C:\Users\<you>\kallaa-petti-signing\`, prints the
   certificate fingerprint and copies the key (as text) to the clipboard.
3. **Save the key safely.** Copy `kallaa-petti-release.jks` to two different places (for example a
   pen drive and an encrypted cloud folder) and put the password in a password manager. Without
   this key no update can ever be installed over the installed app.
4. **Add the GitHub secrets and the variable** (repository › Settings › Secrets and variables › Actions):
   - Secrets: `RELEASE_KEYSTORE_BASE64` (paste from the clipboard), `RELEASE_KEY_ALIAS` = `kallaapetti`,
     `RELEASE_STORE_PASSWORD` and `RELEASE_KEY_PASSWORD` (both the same password).
   - Variable (the Variables tab): `EXPECTED_CERT_SHA256` = the lower-case fingerprint the script printed.
   Then delete the `.base64` file.
5. **Dry run.** On GitHub: **Actions › Release APK › Run workflow** (right side), keep the branch
   `main`, type `2.0.0-rc1` and press **Run workflow**. (Pushing a tag `v2.0.0-rc1` from git does
   the same.) Wait for the run to turn green, then download the APK from the new pre-release on the
   Releases page, install it on a phone and test: make a bill, share the PDF, print on the Bluetooth
   printer, scan a barcode, add a customer from contacts, switch on the app lock, take a backup and
   restore it. Then delete the test release and its tag (Releases › the release › Delete; Tags ›
   v2.0.0-rc1 › Delete).
6. **Real release.** **Actions › Release APK › Run workflow**, type `2.0.0`. The workflow tests,
   builds, signs, verifies the certificate against `EXPECTED_CERT_SHA256`, creates the tag `v2.0.0`
   and publishes the release with the APK, a checksum file and the mapping file.
7. **Check the download.** On the release page, download the APK and `.sha256`; in PowerShell
   `Get-FileHash .\ModernKallaaPetti-v2.0.0.apk -Algorithm SHA256` must match the `.sha256` file.
8. **Move existing test phones once** (section 1 below): back up, uninstall the test APK, install the
   release APK, restore. After this, updates install over the app and keep the data.
9. **Afterwards:** note the release in your records; keep the key copies and every release's mapping
   file. For Google Play later, see section 4 (decision 7).

If a step fails in the Actions tab, open the run and read the red step: it names what is missing
(a secret, a tag not on `main`, a version that does not match `app/build.gradle.kts`).

## 1. What changes for people who already have a test APK

All earlier test APKs (0.1.0 to 1.4.0) were signed with a temporary test key that
exists only in the build sandbox. Android will not install an APK signed with a
different key over an installed app. It shows "App not installed" or a signature
conflict. The first production-signed APK therefore needs a one-time move:

1. Open the old app: **More › Backup and restore › Back up now**. Save the file to
   Google Drive or Downloads. (Do not rely on the copies kept inside the app;
   uninstalling deletes them.)
2. Uninstall the old app.
3. Install the new signed APK.
4. Open the new app and choose **Restore from file**.

After this, every later update installs over the previous version and keeps the data.
The debug build (package name ends with `.debug`) is a separate app and is not affected.

## 2. The release key

| Item | Decision |
|---|---|
| Algorithm | RSA 4096, valid for 100 years (keytool `-validity 36500`) |
| File type | PKCS12 `.jks`; the store and key passwords are the same |
| Alias | `kallaapetti` |
| Where it lives | Your own computer, never in the repository. Two copies in two separate safe places (for example a pen drive and an encrypted cloud folder). The password goes in a password manager. |
| If it is lost | No update can ever be installed over the installed app, on GitHub or on Play. Users would have to uninstall and restore from a backup file. |

Create it with `tools\make-release-keystore.ps1` (Windows PowerShell) or `bash tools/make-release-keystore.sh` (Git Bash, Linux, macOS) (asks for a password twice,
prints the certificate fingerprint, writes the key outside the repository). The same
key must be used for GitHub releases and, later, for Google Play (upload the key to
Play App Signing), so that users can move between the two without reinstalling.

## 3. One-time GitHub setup

1. **Default branch.** A `main` branch now exists on GitHub, holding the same code as
   `claude/vigilant-newton-5zeowg`. In the repository's Settings › Branches, change the
   default branch to `main`; the old branch can then be deleted. The release workflow
   refuses tags that are not on the default branch.
2. **Secrets** (Settings › Secrets and variables › Actions › Secrets):
   `RELEASE_KEYSTORE_BASE64` (the `.base64` file the script wrote),
   `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD` (the same value),
   `RELEASE_KEY_ALIAS` (`kallaapetti`). Delete the `.base64` file afterwards.
3. **Variable** (same page, Variables): `EXPECTED_CERT_SHA256`, the SHA-256
   fingerprint printed by the script, lower case, no colons. The workflow stops if the
   signed APK carries any other certificate.
4. **Tag protection**: add a rule for `v*` so only the owner can create release tags.
5. Optional: put the secrets in an environment named `release` with yourself as the
   required reviewer, so a stray tag cannot sign anything.

## 4. Decisions needed before the first public release

| # | Decision | Recommendation |
|---|---|---|
| 1 | **Licence.** Done: MIT, `LICENSE`, copyright Dr Aran Castro. | None. |
| 2 | **Open-source notices.** Done: the About and credits screen and the README list every component and licence, including Google's own terms for the scanner. | None. |
| 3 | **Internet permission.** Done: declared in the manifest. The app's own code makes no network calls; Google Play services needs it to download the scanner module on first use. | None. The privacy policy says this plainly. |
| 4 | **Privacy policy.** Done: `docs/privacy-policy.md`, linked in the app (About and credits) and the README. | For Google Play later, use the page link `https://github.com/AranCastro/EasyBill/blob/main/docs/privacy-policy.md` or publish it on draran.online. |
| 5 | **Cloud backup of app data (Android Auto Backup).** It copies the live database to the user's Google account. Since 2.0.0 this happens only when the phone can encrypt the copy end to end (a screen lock is set). | Keep. After the first release, test once on a real phone: `adb shell bmgr backupnow online.draran.billing`, uninstall, reinstall, check the data. If it ever restores a damaged database, switch off `allowBackup` and rely on the in-app backup file. |
| 6 | **Android developer verification.** Google is introducing registration of developers and package names for apps installed outside Play on certified devices. | Check the current rules and register `online.draran.billing` with the release key fingerprint if required. |
| 7 | **Google Play.** Needs a developer account, a closed test for new personal accounts, the Data safety form, content rating and the privacy policy link. The upload is an `.aab` (`./gradlew :app:bundleRelease`). | Decide after the GitHub release has been used for a few weeks. Use the same key. |

## 5. Version numbers

- `versionName` is `MAJOR.MINOR.PATCH` (for example 2.0.0).
- `versionCode` is `MAJOR × 10000 + MINOR × 100 + PATCH` (2.0.0 gives 20000). It must
  never repeat or go down; Play does not allow reuse.
- The tag is `v` plus the exact `versionName` (`v2.0.0`). The workflow checks this.
- Tags with a hyphen (`v1.5.1-rc1`) are published as pre-releases.

## 6. What the release workflow now does

Triggered by a tag `vX.Y.Z`:

1. Checks that the tag is on the default branch and equals `versionName`.
2. Checks that all four secrets and `EXPECTED_CERT_SHA256` are set.
3. Runs all unit tests (including `core:common`) and lint on every module.
4. Builds the minified release APK.
5. Aligns it (16 KB pages), signs it (v2 and v3 signatures), verifies the signature,
   compares the certificate fingerprint with `EXPECTED_CERT_SHA256`, and writes a
   SHA-256 checksum file.
6. Keeps the R8 mapping file (needed to read crash reports) as a workflow artifact
   for a year and attaches it to the release.
7. Takes the release notes from the matching entry of the README changelog and
   publishes the GitHub release with the APK, checksum and mapping file.

The key is written to a temporary file with owner-only permissions and removed at
the end of the step.

## 7. Step-by-step runbook

1. Finish the code and update the README changelog. Run
   `./gradlew testDebugUnitTest :core:model:test :core:common:test lintDebug`. The
   working tree must be clean (`git status`).
2. Choose the licence (decision 1) and add `LICENSE`. Add the privacy policy page.
3. Create the key (section 2). Note the fingerprint.
4. Do the one-time GitHub setup (section 3).
5. **Dry run.** Push a tag `v2.0.0-rc1`, wait for the workflow, download the APK,
   install it on a real phone and test: create a bill, share the PDF, print on the
   Bluetooth printer, scan a barcode, turn on the app lock, take a backup and restore
   it. Delete the test release and tag afterwards.
6. Update `versionName` and `versionCode` in `app/build.gradle.kts`, commit to
   `main`, then `git tag v2.0.0` and `git push origin v2.0.0`.
7. When the workflow finishes, download the APK and the checksum from the release
   page, and check them on your computer:
   `sha256sum -c ModernKallaaPetti-v2.0.0.apk.sha256`.
8. Tell existing test users to follow section 1 once.
9. Keep every release's mapping file and the key backups.

To sign on your own computer instead (same method as the workflow):
`./gradlew :app:assembleRelease` then
`bash tools/sign-release.sh ~/kallaa-petti-signing/kallaa-petti-release.jks 2.0.0`.

## 8. Checked and found sound

- Manifest: only the launcher activity is exported; Bluetooth permissions are limited
  (legacy ones only up to Android 11); no camera permission (Google code scanner);
  the file provider shares only the temporary share folder; clear-text traffic is off;
  the release build is not debuggable.
- R8: the shrunk build keeps the names stored in the database and settings
  (document types, business types, theme); no missing-class warnings.
- Native libraries (two AndroidX libraries) are 16 KB aligned for all four ABIs.
- No key, password or keystore file has ever been committed.
