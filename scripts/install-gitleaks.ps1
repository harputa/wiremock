<#
  Installs Gitleaks for this repository only and enables the secret-scanning pre-commit hook.

  - Downloads a pinned Gitleaks release into .tools/gitleaks/ (gitignored); nothing is installed globally.
  - Verifies the download against the release's SHA-256 checksums file.
  - Points this repository's git hooks at .githooks/ (git config --local core.hooksPath).

  Usage (from anywhere inside the repo):  powershell -ExecutionPolicy Bypass -File scripts/install-gitleaks.ps1
#>
$ErrorActionPreference = 'Stop'
$Version = '8.30.1'

$root = (git rev-parse --show-toplevel).Trim()
$dir = Join-Path $root '.tools\gitleaks'
$exe = Join-Path $dir 'gitleaks.exe'

$installed = $false
if (Test-Path $exe) {
    $installed = ((& $exe version) -match [regex]::Escape($Version))
}

if (-not $installed) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $base = "https://github.com/gitleaks/gitleaks/releases/download/v$Version"
    $zipName = "gitleaks_${Version}_windows_x64.zip"
    $sumsName = "gitleaks_${Version}_checksums.txt"
    $zip = Join-Path $dir $zipName
    $sums = Join-Path $dir $sumsName

    Write-Host "Downloading Gitleaks v$Version ..."
    Invoke-WebRequest -Uri "$base/$zipName" -OutFile $zip -UseBasicParsing
    Invoke-WebRequest -Uri "$base/$sumsName" -OutFile $sums -UseBasicParsing

    $expected = Get-Content $sums |
        Where-Object { $_ -match "\s$([regex]::Escape($zipName))$" } |
        ForEach-Object { ($_ -split '\s+')[0].ToLower() }
    $actual = (Get-FileHash -Path $zip -Algorithm SHA256).Hash.ToLower()
    if (-not $expected -or $actual -ne $expected) {
        Remove-Item $zip, $sums -Force
        throw "Checksum verification failed for $zipName (expected '$expected', got '$actual')."
    }

    Expand-Archive -Path $zip -DestinationPath $dir -Force
    Remove-Item $zip, $sums -Force
}

git -C $root config --local core.hooksPath .githooks
Write-Host "Gitleaks $(& $exe version) installed in .tools/gitleaks; pre-commit secret scan enabled for this repository."
