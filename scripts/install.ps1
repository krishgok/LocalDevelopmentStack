[CmdletBinding()]
param(
    [string]$Version = "",
    [string]$InstallDir = "$env:USERPROFILE\.localdevstack\bin"
)

$ErrorActionPreference = "Stop"
$REPO = "krishgok/localdevstack"
$BINARY_NAME = "localdevstack.exe"

if (-not $Version) {
    $release = Invoke-RestMethod "https://api.github.com/repos/$REPO/releases/latest"
    $Version = $release.tag_name -replace '^v', ''
}

$Tarball   = "localdevstack-${Version}-windows-x64.zip"
$Url       = "https://github.com/$REPO/releases/download/v${Version}/$Tarball"
$Sha256Url = "${Url}.sha256"

Write-Host "Installing LocalDevelopmentStack v${Version} for Windows x64..."

$TmpDir = New-TemporaryFile | ForEach-Object { Remove-Item $_; New-Item -ItemType Directory -Path $_.FullName }
$TmpZip = Join-Path $TmpDir $Tarball

try {
    Invoke-WebRequest -Uri $Url -OutFile $TmpZip -UseBasicParsing

    $ExpectedLine = (Invoke-WebRequest -Uri $Sha256Url -UseBasicParsing).Content.Trim()
    $Expected = ($ExpectedLine -split '\s+')[0].ToLower()
    $Actual   = (Get-FileHash $TmpZip -Algorithm SHA256).Hash.ToLower()

    if ($Actual -ne $Expected) {
        throw "Checksum mismatch!`n  Expected: $Expected`n  Got: $Actual"
    }

    Expand-Archive -Path $TmpZip -DestinationPath $TmpDir -Force

    if (-not (Test-Path $InstallDir)) {
        New-Item -ItemType Directory -Path $InstallDir | Out-Null
    }

    $Src = Join-Path $TmpDir "localdevstack-windows-x64.exe"
    $Dst = Join-Path $InstallDir $BINARY_NAME
    Copy-Item $Src $Dst -Force

    $UserPath = [System.Environment]::GetEnvironmentVariable("PATH", "User")
    if ($UserPath -notlike "*$InstallDir*") {
        [System.Environment]::SetEnvironmentVariable("PATH", "$UserPath;$InstallDir", "User")
        Write-Host "Added $InstallDir to your PATH. Restart your terminal to use 'localdevstack'."
    }

    Write-Host ""
    Write-Host "LocalDevelopmentStack v${Version} installed to $Dst"
    Write-Host ""
    Write-Host "Run: localdevstack --help"

} finally {
    Remove-Item $TmpDir -Recurse -Force -ErrorAction SilentlyContinue
}
