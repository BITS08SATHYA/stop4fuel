"use strict";

/**
 * StopForFuel local print agent.
 *
 * A tiny HTTP service installed once on a counter PC. It listens on
 * 127.0.0.1 and relays raw ESC/POS bytes straight to the configured Windows
 * printer (the TVS RP3150 STAR thermal). The web app POSTs the receipt here
 * so printing is one click — no browser dialog — and keeps working across
 * browser and PC restarts because this runs as an auto-start task/service.
 *
 * Deliberately dependency-free (Node core only) and "dumb": it does not know
 * anything about invoices. All receipt formatting lives in the web app, so
 * the installed agent rarely needs updating.
 *
 * Raw printing uses the Windows spooler via an embedded PowerShell + Win32
 * (winspool.drv WritePrinter) call — no native modules, robust on Windows.
 */

const http = require("http");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFile } = require("child_process");

const VERSION = "1.1.0"; // 1.1.0: per-request `printer` override (dot-matrix → MSP 250)

// When packaged with pkg, __dirname points inside the virtual snapshot, so
// config/logs must sit next to the real .exe instead.
const APP_DIR = process.pkg ? path.dirname(process.execPath) : __dirname;
const CONFIG_PATH = path.join(APP_DIR, "config.json");
const LOG_PATH = path.join(APP_DIR, "print-agent.log");

function log(msg) {
    const line = `[${new Date().toISOString()}] ${msg}\n`;
    try { fs.appendFileSync(LOG_PATH, line); } catch (_) { /* ignore */ }
    process.stdout.write(line);
}

function loadConfig() {
    const defaults = { port: 17777, printer: "" }; // printer "" → Windows default
    let raw;
    try {
        raw = fs.readFileSync(CONFIG_PATH, "utf8");
    } catch (_) {
        // No config yet — create a default one and use it.
        try { fs.writeFileSync(CONFIG_PATH, JSON.stringify(defaults, null, 2)); } catch (_) { /* ignore */ }
        return defaults;
    }
    try {
        // Strip a UTF-8 BOM if present — Windows PowerShell's Set-Content /
        // Out-File prepend one and JSON.parse() chokes on the leading U+FEFF.
        // Stripping it makes the agent tolerant of how config.json was written.
        const cfg = JSON.parse(raw.replace(/^\uFEFF/, ""));
        return { ...defaults, ...cfg };
    } catch (e) {
        // Malformed config: fall back to defaults but DO NOT overwrite the
        // file — the user's printer choice is in there; clobbering it is what
        // the v1.0.0 BOM bug did. Leave it for inspection.
        log(`config.json is invalid (${e.message}); using defaults. File left untouched: ${CONFIG_PATH}`);
        return defaults;
    }
}

const config = loadConfig();

// --- raw print helper: embedded PowerShell, written to temp once ----------
const PS_SCRIPT = `param([string]$PrinterName, [string]$FilePath)
$code = @"
using System;
using System.Runtime.InteropServices;
public class RawPrinter {
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
  public struct DOCINFOA { [MarshalAs(UnmanagedType.LPWStr)] public string pDocName;
    [MarshalAs(UnmanagedType.LPWStr)] public string pOutputFile;
    [MarshalAs(UnmanagedType.LPWStr)] public string pDataType; }
  [DllImport("winspool.Drv", EntryPoint="OpenPrinterW", SetLastError=true, CharSet=CharSet.Unicode)]
  public static extern bool OpenPrinter(string src, out IntPtr h, IntPtr pd);
  [DllImport("winspool.Drv", EntryPoint="ClosePrinter", SetLastError=true)]
  public static extern bool ClosePrinter(IntPtr h);
  [DllImport("winspool.Drv", EntryPoint="StartDocPrinterW", SetLastError=true, CharSet=CharSet.Unicode)]
  public static extern bool StartDocPrinter(IntPtr h, int level, ref DOCINFOA di);
  [DllImport("winspool.Drv", EntryPoint="EndDocPrinter", SetLastError=true)]
  public static extern bool EndDocPrinter(IntPtr h);
  [DllImport("winspool.Drv", EntryPoint="StartPagePrinter", SetLastError=true)]
  public static extern bool StartPagePrinter(IntPtr h);
  [DllImport("winspool.Drv", EntryPoint="EndPagePrinter", SetLastError=true)]
  public static extern bool EndPagePrinter(IntPtr h);
  [DllImport("winspool.Drv", EntryPoint="WritePrinter", SetLastError=true)]
  public static extern bool WritePrinter(IntPtr h, IntPtr buf, int count, out int written);
  public static bool Send(string printer, byte[] bytes) {
    IntPtr h;
    if (!OpenPrinter(printer, out h, IntPtr.Zero)) return false;
    DOCINFOA di = new DOCINFOA();
    di.pDocName = "StopForFuel Receipt"; di.pDataType = "RAW";
    bool ok = false;
    if (StartDocPrinter(h, 1, ref di)) {
      if (StartPagePrinter(h)) {
        IntPtr p = Marshal.AllocCoTaskMem(bytes.Length);
        Marshal.Copy(bytes, 0, p, bytes.Length);
        int w; ok = WritePrinter(h, p, bytes.Length, out w);
        Marshal.FreeCoTaskMem(p);
        EndPagePrinter(h);
      }
      EndDocPrinter(h);
    }
    ClosePrinter(h);
    return ok;
  }
}
"@
Add-Type -TypeDefinition $code -Language CSharp
$bytes = [System.IO.File]::ReadAllBytes($FilePath)
if (-not $PrinterName) {
  $PrinterName = (Get-CimInstance -Class Win32_Printer -Filter "Default=True").Name
}
if ([RawPrinter]::Send($PrinterName, $bytes)) { exit 0 }
else { Write-Error ("WritePrinter failed for '" + $PrinterName + "'"); exit 1 }
`;

const PS_PATH = path.join(os.tmpdir(), "stopforfuel-rawprint.ps1");
try { fs.writeFileSync(PS_PATH, PS_SCRIPT); } catch (e) { log("Failed to write PS helper: " + e.message); }

function rawPrint(bytes, printer, cb) {
    // Per-request printer overrides the configured default (e.g. dot-matrix
    // jobs targeting the MSP 250 on a PC whose default is the thermal printer).
    const target = (printer && String(printer).trim()) || config.printer || "";
    const tmpBin = path.join(os.tmpdir(), `sff-receipt-${Date.now()}-${Math.random().toString(36).slice(2)}.bin`);
    fs.writeFile(tmpBin, bytes, (werr) => {
        if (werr) return cb(werr);
        execFile(
            "powershell.exe",
            ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", PS_PATH, "-PrinterName", target, "-FilePath", tmpBin],
            { windowsHide: true, timeout: 20000 },
            (err, stdout, stderr) => {
                fs.unlink(tmpBin, () => { /* best-effort cleanup */ });
                if (err) return cb(new Error((stderr || err.message).trim()));
                cb(null);
            }
        );
    });
}

function listPrinters(cb) {
    execFile(
        "powershell.exe",
        ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
            "Get-CimInstance Win32_Printer | Select-Object -ExpandProperty Name"],
        { windowsHide: true, timeout: 10000 },
        (err, stdout) => {
            if (err) return cb(err);
            cb(null, stdout.split(/\r?\n/).map((s) => s.trim()).filter(Boolean));
        }
    );
}

// --- HTTP server -----------------------------------------------------------
function cors(res) {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type");
    // The StopForFuel app is served over HTTPS from a public origin, while this
    // agent lives on the loopback (a "private"/"local" network in Chrome's
    // Private Network Access model). Without this header Chrome's PNA preflight
    // blocks the call. Safe here: the server only ever binds to 127.0.0.1.
    res.setHeader("Access-Control-Allow-Private-Network", "true");
}

function sendJson(res, code, obj) {
    cors(res);
    res.writeHead(code, { "Content-Type": "application/json" });
    res.end(JSON.stringify(obj));
}

const server = http.createServer((req, res) => {
    if (req.method === "OPTIONS") { cors(res); res.writeHead(204); return res.end(); }

    if (req.method === "GET" && req.url === "/health") {
        return sendJson(res, 200, { ok: true, version: VERSION, printer: config.printer || "(Windows default)" });
    }

    if (req.method === "GET" && req.url === "/printers") {
        return listPrinters((err, names) => {
            if (err) return sendJson(res, 500, { ok: false, error: err.message });
            sendJson(res, 200, { ok: true, printers: names });
        });
    }

    if (req.method === "POST" && req.url === "/print") {
        let body = "";
        req.on("data", (c) => {
            body += c;
            if (body.length > 5_000_000) { req.destroy(); } // ~5 MB sanity cap
        });
        req.on("end", () => {
            let parsed;
            try { parsed = JSON.parse(body); } catch (_) {
                return sendJson(res, 400, { ok: false, error: "invalid JSON" });
            }
            if (!parsed || typeof parsed.data !== "string") {
                return sendJson(res, 400, { ok: false, error: "missing 'data' (base64)" });
            }
            let bytes;
            try { bytes = Buffer.from(parsed.data, "base64"); } catch (_) {
                return sendJson(res, 400, { ok: false, error: "invalid base64" });
            }
            const reqPrinter = typeof parsed.printer === "string" ? parsed.printer.trim() : "";
            rawPrint(bytes, reqPrinter, (perr) => {
                if (perr) {
                    log(`PRINT FAILED (${parsed.jobName || "?"}): ${perr.message}`);
                    return sendJson(res, 500, { ok: false, error: perr.message });
                }
                log(`PRINTED ${bytes.length} bytes (${parsed.jobName || "?"}) -> ${reqPrinter || config.printer || "(default)"}`);
                sendJson(res, 200, { ok: true });
            });
        });
        return;
    }

    sendJson(res, 404, { ok: false, error: "not found" });
});

// Bind to loopback only — never exposed off the machine.
server.listen(config.port, "127.0.0.1", () => {
    log(`StopForFuel print agent v${VERSION} listening on 127.0.0.1:${config.port} ` +
        `(printer: ${config.printer || "Windows default"})`);
});

server.on("error", (e) => {
    log(`Server error: ${e.message}`);
    process.exit(1);
});
