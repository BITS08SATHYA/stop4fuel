# StopForFuel Print Agent - installer (run on each counter PC, as Administrator)
#
# What it does:
#   1. Copies StopForFuelPrintAgent.exe to Program Files
#   2. Lets you pick the thermal printer (defaults to a detected TVS RP3150)
#   3. Writes config.json
#   4. Registers an auto-start Scheduled Task (runs at logon, auto-restarts,
#      highest privileges, in the counter user's session so it sees the printer)
#   5. Starts it and verifies the health endpoint
#
# Re-running it is safe: it replaces the previous install in place.

$ErrorActionPreference = "Stop"
$TaskName   = "StopForFuel Print Agent"
$InstallDir = Join-Path $env:ProgramFiles "StopForFuel PrintAgent"
$ExeName    = "StopForFuelPrintAgent.exe"
$Port       = 17777

# --- must run elevated ----------------------------------------------------
$admin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
         ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Write-Host "Please run this script in an ELEVATED PowerShell (Run as Administrator)." -ForegroundColor Red
    exit 1
}

# Accept the exe either next to this script or in the .\dist subfolder
# (so the print-agent folder works as-is, however it was copied over).
$candidates = @(
    (Join-Path $PSScriptRoot $ExeName),
    (Join-Path $PSScriptRoot "dist\$ExeName")
)
$srcExe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $srcExe) {
    Write-Host "Cannot find $ExeName. Looked in:" -ForegroundColor Red
    $candidates | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

Write-Host "`n=== StopForFuel Print Agent installer ===`n" -ForegroundColor Cyan

# --- copy files -----------------------------------------------------------
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item -Force $srcExe (Join-Path $InstallDir $ExeName)
Write-Host "Installed to: $InstallDir"

# --- pick the printer -----------------------------------------------------
$printers = @(Get-CimInstance Win32_Printer | Select-Object -ExpandProperty Name)
if ($printers.Count -eq 0) {
    Write-Host "No printers found on this PC. Install the TVS RP3150 driver first." -ForegroundColor Red
    exit 1
}

Write-Host "`nInstalled printers:"
for ($i = 0; $i -lt $printers.Count; $i++) {
    Write-Host ("  [{0}] {1}" -f $i, $printers[$i])
}

# Pre-select a TVS / RP3150 thermal if present.
$defaultIdx = 0
for ($i = 0; $i -lt $printers.Count; $i++) {
    if ($printers[$i] -match "RP3150|TVS|Thermal|STAR") { $defaultIdx = $i; break }
}

$choice = Read-Host ("`nPick the THERMAL printer number [default {0} = {1}]" -f $defaultIdx, $printers[$defaultIdx])
if ([string]::IsNullOrWhiteSpace($choice)) { $idx = $defaultIdx } else { $idx = [int]$choice }
if ($idx -lt 0 -or $idx -ge $printers.Count) {
    Write-Host "Invalid choice." -ForegroundColor Red
    exit 1
}
$chosenPrinter = $printers[$idx]
Write-Host ("Selected printer: {0}" -f $chosenPrinter) -ForegroundColor Green

# --- write config ---------------------------------------------------------
$config = @{ port = $Port; printer = $chosenPrinter } | ConvertTo-Json
Set-Content -Path (Join-Path $InstallDir "config.json") -Value $config -Encoding UTF8

# --- register auto-start task --------------------------------------------
Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue

$action   = New-ScheduledTaskAction  -Execute (Join-Path $InstallDir $ExeName)
$trigger  = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
              -StartWhenAvailable -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
              -ExecutionTimeLimit (New-TimeSpan -Seconds 0)
$principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) `
              -LogonType Interactive -RunLevel Highest

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
    -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "Registered auto-start task: '$TaskName'"

# --- start now & verify ---------------------------------------------------
Start-ScheduledTask -TaskName $TaskName
Start-Sleep -Seconds 3

try {
    $h = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/health" -f $Port) -TimeoutSec 5
    Write-Host ("`nAgent is RUNNING. Printer = {0}" -f $h.printer) -ForegroundColor Green
    Write-Host "`nInstall complete. Open the StopForFuel app and click Print -" -ForegroundColor Cyan
    Write-Host "the receipt should go straight to the thermal printer." -ForegroundColor Cyan
} catch {
    Write-Host "`nAgent did not answer on port $Port yet." -ForegroundColor Yellow
    Write-Host "Check the log: $InstallDir\print-agent.log" -ForegroundColor Yellow
}
