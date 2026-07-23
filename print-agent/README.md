# StopForFuel Print Agent

One-click thermal printing for the StopForFuel POS, without the browser print
dialog and without depending on which browser/shortcut staff use.

## How it works

```
 Browser (StopForFuel web app)                 Counter PC
 ┌───────────────────────────┐   POST ESC/POS  ┌──────────────────────────┐
 │ Print button              │ ──────────────▶ │ Print Agent (this)       │
 │ buildInvoiceEscPos()      │  127.0.0.1:17777│ raw → Windows spooler →  │
 │ → sendToPrintAgent()      │                 │ TVS RP3150 thermal       │
 │   (falls back to browser  │                 └──────────────────────────┘
 │    print if agent is down)│
 └───────────────────────────┘
```

- The agent is **dumb**: it only relays raw ESC/POS bytes to the printer. All
  receipt formatting lives in the web app (`frontend/lib/escpos-invoice.ts`),
  so updating the receipt never requires touching installed machines.
- Browsers treat `http://127.0.0.1` as a secure origin, so the HTTPS app may
  call it — no mixed-content block, no certificate setup.
- It runs as an auto-start Scheduled Task: survives browser closes and reboots.
- If the agent is not installed/running, the app silently falls back to the
  old browser-print popup. Nothing breaks on a dev laptop or any other PC.

## Files

| File | Purpose |
|------|---------|
| `agent.js` | The agent (Node core only, no dependencies) |
| `package.json` | pkg build config |
| `install.ps1` | Per-PC installer (copy, pick printer, register task) |
| `uninstall.ps1` | Remove agent + task |
| `dist/StopForFuelPrintAgent.exe` | Pre-built single-file binary (Node bundled) |

## Build the .exe (only if you change `agent.js`)

A pre-built `dist/StopForFuelPrintAgent.exe` is produced by:

```bash
cd print-agent
npm run build:win        # → dist/StopForFuelPrintAgent.exe (node18-win-x64)
```

(Cross-builds fine from Linux/Mac; no Windows needed to build.)

## Install on a counter PC (one time per PC)

1. Copy these three files to the counter PC (any folder), keeping them together:
   - `dist/StopForFuelPrintAgent.exe`
   - `install.ps1`
   - `uninstall.ps1`
   > Place `StopForFuelPrintAgent.exe` **next to** `install.ps1`.
2. Make sure the **TVS RP3150 driver is installed** and the printer prints a
   Windows test page.
3. Right-click PowerShell → **Run as Administrator**, then:
   ```powershell
   cd <folder you copied the files to>
   powershell -ExecutionPolicy Bypass -File .\install.ps1
   ```
4. When prompted, pick the **thermal** printer (it pre-selects a detected
   TVS/RP3150). The script writes `config.json`, registers the auto-start
   task, starts it, and verifies `http://127.0.0.1:17777/health`.
5. Open StopForFuel → create a bill → **Print**. It should print straight to
   the thermal printer with no dialog.

## Test it works

From the counter PC:

```powershell
# Agent up?
Invoke-RestMethod http://127.0.0.1:17777/health

# What printers does it see?
Invoke-RestMethod http://127.0.0.1:17777/printers
```

`/health` returns the configured printer name. A real end-to-end test is just
clicking **Print** in the app.

## Change the printer later

Edit `C:\Program Files\StopForFuel PrintAgent\config.json` (set `"printer"`
to the exact Windows printer name, or `""` for the Windows default) and
restart the task:

```powershell
Stop-ScheduledTask  -TaskName "StopForFuel Print Agent"
Start-ScheduledTask -TaskName "StopForFuel Print Agent"
```

## Uninstall

```powershell
powershell -ExecutionPolicy Bypass -File .\uninstall.ps1
```

## Troubleshooting

- **Nothing prints, app shows the browser popup instead** — the agent isn't
  reachable. Check `Invoke-RestMethod http://127.0.0.1:17777/health` and the
  log at `C:\Program Files\StopForFuel PrintAgent\print-agent.log`.
- **`/health` works but no paper** — wrong printer in `config.json`, or the
  printer isn't visible to the logged-in user. Run `/printers` to see exact
  names; copy the name verbatim into `config.json`.
- **Garbled output** — the printer isn't in ESC/POS mode. Set the TVS RP3150
  to ESC/POS (not STAR-line) emulation in its utility/DIP settings.
- **Dot-matrix slip is slow and clipped at the edges** — that is the browser
  fallback, not the agent path. The agent sends the MSP 250 plain ESC/P text,
  which prints in a couple of seconds and cannot overrun the carriage; the
  browser sends a rasterised page, which the 9-pin head plots dot by dot and
  the OS driver crops at its unprintable margins. The app now says which
  failure it hit — "agent is not running" (restart it) versus "could not
  print: …" (the dot-matrix printer name beside the Printer selector doesn't
  match a `/printers` name). Once the agent path is live, speed and alignment
  are tuned in the app: Invoices → History → the gear beside the printer
  pickers (quality Draft/NLQ, pitch, margins, and a test slip).
- **Restarting the agent needs the process killed, not the task stopped** — the
  scheduled task launches the exe detached and then exits, so
  `Stop-ScheduledTask` does nothing and a fresh start dies on "port in use":

  ```powershell
  Get-Process StopForFuelPrintAgent | Stop-Process -Force
  Start-ScheduledTask -TaskName "StopForFuel Print Agent"
  ```
- **Port 17777 in use** — change `"port"` in `config.json`, and set the same
  on each browser via `localStorage.setItem('printAgentUrl','http://127.0.0.1:<port>')`.
