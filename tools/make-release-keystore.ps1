# Creates the release signing key for Modern Kallaa Petti ONCE, outside the repository (Windows PowerShell).
# Run from the repository folder:
#   powershell -ExecutionPolicy Bypass -File tools\make-release-keystore.ps1
# Keep two copies of the .jks file in two different safe places and write the password in a password manager.
# If the key is lost, no update can ever be installed over the installed app.
param([string]$OutDir = (Join-Path $HOME "kallaa-petti-signing"))
$ErrorActionPreference = "Stop"

function Find-Keytool {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = @(
        (Join-Path $env:JAVA_HOME "bin\keytool.exe"),
        (Join-Path $env:ProgramFiles "Android\Android Studio\jbr\bin\keytool.exe"),
        (Join-Path $env:ProgramFiles "Android\Android Studio\jre\bin\keytool.exe")
    )
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    throw "keytool was not found. Install a JDK or Android Studio, or add the bin folder of the JDK to PATH."
}

function ConvertTo-PlainText([System.Security.SecureString]$secure) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}

$keytool = Find-Keytool
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ks = Join-Path $OutDir "kallaa-petti-release.jks"
if (Test-Path $ks) { throw "A key already exists at $ks. It is not replaced." }

$first = ConvertTo-PlainText (Read-Host "Choose a keystore password (at least 12 characters; it is also used for the key)" -AsSecureString)
$again = ConvertTo-PlainText (Read-Host "Type it again" -AsSecureString)
if ($first -ne $again -or $first.Length -lt 12) { throw "The passwords differ or are shorter than 12 characters." }

# A PKCS12 keystore uses one password for the store and the key, so both are the same here.
& $keytool -genkeypair -v -keystore $ks -storetype PKCS12 -alias kallaapetti -keyalg RSA -keysize 4096 `
    -validity 36500 -storepass $first -keypass $first -dname "CN=Modern Kallaa Petti, O=Draran, C=IN"
if ($LASTEXITCODE -ne 0) { throw "keytool could not create the key." }

$listing = (& $keytool -list -v -keystore $ks -storepass $first) | Out-String
if ($listing -notmatch 'SHA256:\s*([0-9A-Fa-f:]+)') { throw "The certificate fingerprint could not be read." }
$fingerprint = $Matches[1]
$forGitHub = $fingerprint.Replace(":", "").ToLower()

$b64 = Join-Path $OutDir "kallaa-petti-release.jks.base64"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)) | Set-Content -NoNewline -Path $b64
Set-Clipboard -Value (Get-Content -Raw -Path $b64)

Write-Host ""
Write-Host "Key file:      $ks"
Write-Host "Fingerprint:   $fingerprint"
Write-Host ""
Write-Host "GitHub variable EXPECTED_CERT_SHA256 =" $forGitHub
Write-Host "GitHub secret  RELEASE_KEYSTORE_BASE64 = (already copied to the clipboard; also saved in $b64)"
Write-Host "GitHub secret  RELEASE_KEY_ALIAS       = kallaapetti"
Write-Host "GitHub secrets RELEASE_STORE_PASSWORD and RELEASE_KEY_PASSWORD = the same password you just typed"
Write-Host ""
Write-Host "Now copy the .jks file to two separate safe places, paste the secrets into GitHub, then delete the .base64 file."
Write-Host "Never commit the key."
