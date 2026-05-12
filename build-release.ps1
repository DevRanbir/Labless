$ErrorActionPreference = "Stop"

# ---- Config ----
$JDK_HOME     = "C:\Program Files\Java\jdk-25"
$MVN_CMD      = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
$M2_REPO      = "$env:USERPROFILE\.m2\repository"
$PROJECT_DIR  = "$PSScriptRoot\java-gui-mail-labeler"
$OUTPUT_DIR   = "$PSScriptRoot\Labless-Portable"
$ICON_FILE    = "$PSScriptRoot\Lables.ico"
$APP_NAME     = "Labless"
$MAIN_CLASS   = "com.labless.app.MainApplication"
$APP_VERSION  = "1.0.0"
$FX_VERSION   = "21.0.5"
$JPACKAGE     = "$JDK_HOME\bin\jpackage.exe"
$FAT_JAR      = "$PROJECT_DIR\target\labless-0.1.0-SNAPSHOT.jar"
$RCEDIT       = "$PSScriptRoot\.tools\rcedit.exe"
$WIX_DIR      = "$PSScriptRoot\.tools\wix"

Write-Host "== Labless Release Builder ==" -ForegroundColor Cyan

# ---- Step 0: Maven ----
Write-Host "[0/5] Maven build..." -ForegroundColor Yellow
& $MVN_CMD -f "$PROJECT_DIR\pom.xml" clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven failed" }
Write-Host "  Maven OK" -ForegroundColor Green

# ---- Step 1: Verify ----
Write-Host "[1/5] Verifying tools..." -ForegroundColor Yellow
foreach ($t in @($JPACKAGE,$FAT_JAR,$ICON_FILE,$RCEDIT)) {
    if (-not (Test-Path $t)) { throw "Not found: $t" }
}
if (-not (Test-Path "$WIX_DIR\candle.exe")) { throw "WiX not found: $WIX_DIR" }
Write-Host "  All tools OK" -ForegroundColor Green

# ---- Step 2: Input folder ----
Write-Host "[2/5] Preparing input..." -ForegroundColor Yellow
$APP_INPUT = "$PSScriptRoot\.build-input"
if (Test-Path $APP_INPUT) { Remove-Item $APP_INPUT -Recurse -Force }
New-Item -ItemType Directory -Path $APP_INPUT | Out-Null
Copy-Item $FAT_JAR "$APP_INPUT\labless.jar"

$FX_JARS = @(
    "$M2_REPO\org\openjfx\javafx-base\$FX_VERSION\javafx-base-$FX_VERSION-win.jar",
    "$M2_REPO\org\openjfx\javafx-controls\$FX_VERSION\javafx-controls-$FX_VERSION-win.jar",
    "$M2_REPO\org\openjfx\javafx-fxml\$FX_VERSION\javafx-fxml-$FX_VERSION-win.jar",
    "$M2_REPO\org\openjfx\javafx-graphics\$FX_VERSION\javafx-graphics-$FX_VERSION-win.jar",
    "$M2_REPO\org\openjfx\javafx-media\$FX_VERSION\javafx-media-$FX_VERSION-win.jar",
    "$M2_REPO\org\openjfx\javafx-web\$FX_VERSION\javafx-web-$FX_VERSION-win.jar"
)
foreach ($j in $FX_JARS) { if (-not (Test-Path $j)) { throw "Missing FX JAR: $j" } }
foreach ($j in $FX_JARS) { Copy-Item $j $APP_INPUT }
$FX_MODULE_PATH = $FX_JARS -join ";"

$ADD_MODULES = "javafx.controls,javafx.fxml,javafx.web,javafx.media,java.logging,java.sql,java.naming,java.net.http,java.desktop,jdk.unsupported,jdk.crypto.ec,jdk.httpserver"
Write-Host "  Ready" -ForegroundColor Green

# ---- Common jpackage args ----
$COMMON_ARGS = @(
    "--name",         $APP_NAME,
    "--app-version",  $APP_VERSION,
    "--vendor",       "Labless",
    "--description",  "Intelligent Email Labeling App",
    "--copyright",    "2025 Labless",
    "--input",        $APP_INPUT,
    "--main-jar",     "labless.jar",
    "--main-class",   $MAIN_CLASS,
    "--module-path",  $FX_MODULE_PATH,
    "--add-modules",  $ADD_MODULES,
    "--icon",         $ICON_FILE,
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--java-options", "--enable-native-access=javafx.graphics",
    "--java-options", "--enable-native-access=javafx.web",
    "--java-options", "-Xms256m",
    "--java-options", "-Xmx512m"
)

# ---- Step 3: Build app-image (portable) ----
Write-Host "[3/5] Building portable app-image..." -ForegroundColor Yellow
if (Test-Path $OUTPUT_DIR) { Remove-Item "$OUTPUT_DIR\*" -Recurse -Force }
else { New-Item -ItemType Directory -Path $OUTPUT_DIR | Out-Null }
$JPACKAGE_OUT = "$PSScriptRoot\.jpackage-out"
if (Test-Path $JPACKAGE_OUT) { Remove-Item $JPACKAGE_OUT -Recurse -Force }
New-Item -ItemType Directory -Path $JPACKAGE_OUT | Out-Null

& $JPACKAGE @COMMON_ARGS --type "app-image" --dest $JPACKAGE_OUT
if ($LASTEXITCODE -ne 0) { throw "jpackage app-image failed" }

Copy-Item "$JPACKAGE_OUT\$APP_NAME\*" $OUTPUT_DIR -Recurse -Force
Remove-Item $JPACKAGE_OUT -Recurse -Force

# Inject icon into the exe using rcedit
# jpackage marks output files as ReadOnly - must clear before rcedit can write
Write-Host "  Injecting icon with rcedit..." -ForegroundColor Yellow
$exePath = "$OUTPUT_DIR\Labless.exe"
Set-ItemProperty -Path $exePath -Name IsReadOnly -Value $false
$rResult = Start-Process -FilePath $RCEDIT -ArgumentList "`"$exePath`" --set-icon `"$ICON_FILE`"" -Wait -PassThru -NoNewWindow
if ($rResult.ExitCode -ne 0) { Write-Host "  WARN: rcedit icon injection failed (exit $($rResult.ExitCode))" -ForegroundColor Yellow }
else { Write-Host "  Icon injected OK" -ForegroundColor Green }

Set-Content "$OUTPUT_DIR\Run-Labless.bat" "@echo off`r`ncd /d `"%~dp0`"`r`nstart `"`" `"Labless.exe`"`r`nexit /b 0" -Encoding ASCII
Write-Host "  Portable build done." -ForegroundColor Green

# ---- Step 4: Build MSI installer (registers in Windows Apps list) ----
Write-Host "[4/5] Building MSI installer..." -ForegroundColor Yellow
$MSI_OUT = "$PSScriptRoot\.msi-out"
if (Test-Path $MSI_OUT) { Remove-Item $MSI_OUT -Recurse -Force }
New-Item -ItemType Directory -Path $MSI_OUT | Out-Null

# Add WiX to PATH for jpackage
$env:PATH = "$WIX_DIR;$env:PATH"

& $JPACKAGE @COMMON_ARGS `
    --type "msi" `
    --dest $MSI_OUT `
    --win-dir-chooser `
    --win-shortcut `
    --win-shortcut-prompt `
    --win-menu `
    --win-menu-group "Labless" `
    --win-per-user-install `
    --win-upgrade-uuid "7F3A2B91-4C5D-4E8F-A1B2-C3D4E5F60001"

if ($LASTEXITCODE -ne 0) { throw "jpackage MSI failed" }

$msiFile = Get-ChildItem $MSI_OUT -Filter "*.msi" | Select-Object -First 1
if ($msiFile) {
    Copy-Item $msiFile.FullName "$PSScriptRoot\Labless-Setup-$APP_VERSION.msi"
    Write-Host "  MSI installer: $PSScriptRoot\Labless-Setup-$APP_VERSION.msi" -ForegroundColor Green
}
Remove-Item $MSI_OUT -Recurse -Force

# ---- Step 5: Summary ----
Write-Host "[5/5] Cleaning up..." -ForegroundColor Yellow
Remove-Item $APP_INPUT -Recurse -Force -ErrorAction SilentlyContinue

$portableMB = [math]::Round((Get-ChildItem $OUTPUT_DIR -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB, 1)
$msiFile2 = Get-ChildItem "$PSScriptRoot" -Filter "*.msi" | Select-Object -First 1
$msiMB = if ($msiFile2) { [math]::Round($msiFile2.Length / 1MB, 1) } else { "N/A" }

Write-Host "" 
Write-Host "============================================" -ForegroundColor Green
Write-Host "  BUILD COMPLETE!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host "  PORTABLE : $OUTPUT_DIR ($portableMB MB)" -ForegroundColor Green
Write-Host "  INSTALLER: $PSScriptRoot\Labless-Setup-$APP_VERSION.msi ($msiMB MB)" -ForegroundColor Green
Write-Host ""
Write-Host "  PORTABLE  -> Zip Labless-Portable and share (no install needed)" -ForegroundColor Cyan
Write-Host "  INSTALLER -> Run the .msi to install + appear in Windows Apps list" -ForegroundColor Cyan
Write-Host ""

