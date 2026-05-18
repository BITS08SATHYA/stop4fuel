# StopForFuel Print Agent - uninstaller (run as Administrator)

$ErrorActionPreference = "SilentlyContinue"
$TaskName   = "StopForFuel Print Agent"
$InstallDir = Join-Path $env:ProgramFiles "StopForFuel PrintAgent"

$admin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()
         ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Write-Host "Run this in an ELEVATED PowerShell (Run as Administrator)." -ForegroundColor Red
    exit 1
}

Stop-ScheduledTask     -TaskName $TaskName
Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false

# The exe may still be running from the task session - stop it before deleting.
Get-Process StopForFuelPrintAgent -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1
Remove-Item -Recurse -Force $InstallDir

Write-Host "StopForFuel Print Agent removed." -ForegroundColor Green
