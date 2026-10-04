# Modern Kallaa Petti: Release Plan (v1)

Status on 4 October 2026. This plan covers the first production release signed
with a real release key. It was prepared after a strict audit of the 1.4.0 code
(money and data, printing and reports, screens, user-facing text, release
settings). The audit findings that affect users are fixed in 1.5.0; this
document lists what the owner must still decide and do.

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

Create it with `bash tools/make-release-keystore.sh` (asks for a password twice,
prints the certificate fingerprint, writes the key outside the repository). The same
key must be used for GitHub releases and, later, for Google Play (upload the key to
Play App Signing), so that users can move between the two without reinstalling.

## 3. One-time GitHub setup

1. **Default branch.** The repository's only branch is
   `claude/vigilant-newton-5zeowg`. Rename it to `main` (Settings › Branches), then
   `git branch -m main` locally. The release workflow refuses tags that are not on
   the default branch.
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
| 1 | **Licence.** The repository is public and the README calls the app free, but there is no `LICENSE` file, so by default all rights are reserved. | Choose MIT or Apache-2.0 (permissive) or GPL-3.0 (changes must stay open). Add the file and a line in the README. |
| 2 | **Open-source notices.** The README lists only three components. The app also uses AndroidX, Compose, Hilt, Room and Kotlin libraries (Apache-2.0), and Google Play services and ML Kit for the barcode scanner (Google's own terms). | Add an "Open-source licences" screen in the app (the AboutLibraries plugin) and list the Google components honestly in the README. |
| 3 | **Internet permission.** The release manifest contains INTERNET and ACCESS_NETWORK_STATE, added by the Google barcode-scanner libraries. The app's own code makes no network calls. The first scan needs Google Play services to download the scanner module. | Say this plainly in the privacy policy and the user guide. Do not remove the permission without testing the scanner on a real phone. |
| 4 | **Privacy policy.** None exists. Google Play requires one; GitHub users should also have one. | One short page (GitHub Pages): all data stays on the phone; optional Google account backup; the scanner module; Bluetooth printing; no advertising, no accounts. |
| 5 | **Cloud backup of app data (Android Auto Backup).** It copies the live database to the user's Google account. Since 1.5.0 this happens only when the phone can encrypt the copy end to end (a screen lock is set). | Keep. After the first release, test once on a real phone: `adb shell bmgr backupnow online.draran.billing`, uninstall, reinstall, check the data. If it ever restores a damaged database, switch off `allowBackup` and rely on the in-app backup file. |
| 6 | **Android developer verification.** Google is introducing registration of developers and package names for apps installed outside Play on certified devices. | Check the current rules and register `online.draran.billing` with the release key fingerprint if required. |
| 7 | **Google Play.** Needs a developer account, a closed test for new personal accounts, the Data safety form, content rating and the privacy policy link. The upload is an `.aab` (`./gradlew :app:bundleRelease`). | Decide after the GitHub release has been used for a few weeks. Use the same key. |

## 5. Version numbers

- `versionName` is `MAJOR.MINOR.PATCH` (for example 1.5.0).
- `versionCode` is `MAJOR × 10000 + MINOR × 100 + PATCH` (1.5.0 gives 10500). It must
  never repeat or go down; Play does not allow reuse.
- The tag is `v` plus the exact `versionName` (`v1.5.0`). The workflow checks this.
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
5. **Dry run.** Push a tag `v0.0.1-rc1`, wait for the workflow, download the APK,
   install it on a real phone and test: create a bill, share the PDF, print on the
   Bluetooth printer, scan a barcode, turn on the app lock, take a backup and restore
   it. Delete the test release and tag afterwards.
6. Update `versionName` and `versionCode` in `app/build.gradle.kts`, commit to
   `main`, then `git tag v1.5.0` and `git push origin v1.5.0`.
7. When the workflow finishes, download the APK and the checksum from the release
   page, and check them on your computer:
   `sha256sum -c ModernKallaaPetti-v1.5.0.apk.sha256`.
8. Tell existing test users to follow section 1 once.
9. Keep every release's mapping file and the key backups.

To sign on your own computer instead (same method as the workflow):
`./gradlew :app:assembleRelease` then
`bash tools/sign-release.sh ~/kallaa-petti-signing/kallaa-petti-release.jks 1.5.0`.

## 8. Checked and found sound

- Manifest: only the launcher activity is exported; Bluetooth permissions are limited
  (legacy ones only up to Android 11); no camera permission (Google code scanner);
  the file provider shares only the temporary share folder; clear-text traffic is off;
  the release build is not debuggable.
- R8: the shrunk build keeps the names stored in the database and settings
  (document types, business types, theme); no missing-class warnings.
- Native libraries (two AndroidX libraries) are 16 KB aligned for all four ABIs.
- No key, password or keystore file has ever been committed.
