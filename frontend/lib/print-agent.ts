// ---------------------------------------------------------------------------
// Client for the local StopForFuel print agent.
//
// The agent is a tiny service installed once on a counter PC. It listens on
// 127.0.0.1 and relays raw ESC/POS bytes straight to the TVS RP3150 thermal
// printer — no browser print dialog, survives browser/PC restarts.
//
// Browsers treat http://127.0.0.1 as a secure origin, so the HTTPS app is
// allowed to call it with no mixed-content block and no certificate setup.
//
// Everything here fails soft: any error just means "agent not available",
// and the caller falls back to browser printing.
// ---------------------------------------------------------------------------

const DEFAULT_AGENT_URL = "http://127.0.0.1:17777";

/** Per-machine override, e.g. a counter PC running the agent on another port. */
function agentBaseUrl(): string {
    if (typeof window !== "undefined") {
        try {
            const o = window.localStorage.getItem("printAgentUrl");
            if (o && /^https?:\/\//.test(o)) return o.replace(/\/+$/, "");
        } catch (_) { /* localStorage blocked — use default */ }
    }
    return DEFAULT_AGENT_URL;
}

function withTimeout(ms: number): { signal: AbortSignal; done: () => void } {
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), ms);
    return { signal: ctrl.signal, done: () => clearTimeout(t) };
}

/** Chunked base64 — avoids blowing the call stack on large byte arrays. */
function toBase64(bytes: Uint8Array): string {
    let s = "";
    const CHUNK = 0x8000;
    for (let i = 0; i < bytes.length; i += CHUNK) {
        s += String.fromCharCode(...bytes.subarray(i, i + CHUNK));
    }
    return btoa(s);
}

export interface PrintAgentHealth {
    version: string;
    printer: string;
}

/**
 * The agent's /health payload, or null if it didn't answer. Never throws.
 *
 * Timeouts here are deliberately loose. Loopback answers in milliseconds when
 * the machine is idle, but these calls fire while a counter PC is loading the
 * whole app, and an abort is indistinguishable from "no agent installed" — the
 * app then hides the printer picker and prints through the browser dialog for
 * the rest of the session. Waiting an extra second beats being wrong about that.
 */
export async function getPrintAgentHealth(): Promise<PrintAgentHealth | null> {
    const { signal, done } = withTimeout(4000);
    try {
        const res = await fetch(agentBaseUrl() + "/health", { signal, cache: "no-store" });
        if (!res.ok) return null;
        const body = await res.json();
        return { version: String(body?.version || "?"), printer: String(body?.printer || "") };
    } catch (_) {
        return null;
    } finally {
        done();
    }
}

/** True if the local print agent answers a health check. Never throws. */
export async function probePrintAgent(): Promise<boolean> {
    return (await getPrintAgentHealth()) !== null;
}

/**
 * List the Windows printers the agent can see. Returns [] on any failure
 * (agent down, old version without /printers, network error) so callers can
 * fall back to a free-text printer name. Never throws.
 *
 * Retries once, because this one is genuinely slow — the agent shells out to
 * PowerShell for a CIM query — and it runs on page mount, in competition with
 * everything else the app is loading. A single miss leaves the cashier typing a
 * printer name by hand all session instead of picking it from a list.
 */
export async function listPrintAgentPrinters(): Promise<string[]> {
    for (let attempt = 0; attempt < 2; attempt++) {
        if (attempt) await new Promise((r) => setTimeout(r, 1500));
        const { signal, done } = withTimeout(8000);
        try {
            const res = await fetch(agentBaseUrl() + "/printers", { signal, cache: "no-store" });
            if (!res.ok) continue;
            const body = await res.json();
            const names = Array.isArray(body?.printers)
                ? body.printers.filter((p: unknown): p is string => typeof p === "string")
                : [];
            if (names.length) return names;
        } catch (_) {
            // fall through to the retry
        } finally {
            done();
        }
    }
    return [];
}

/**
 * Send raw bytes to the agent for printing.
 * Resolves on success; throws on any failure so the caller can fall back.
 *
 * `printer` optionally names the Windows printer to spool to, overriding the
 * agent's configured default. Used to send dot-matrix (ESC/P) jobs to the
 * MSP 250 from a counter PC whose agent default is the thermal printer; omit it
 * and the job goes to the configured default (thermal).
 */
export async function sendToPrintAgent(bytes: Uint8Array, jobName: string, printer?: string): Promise<void> {
    const { signal, done } = withTimeout(8000);
    try {
        const res = await fetch(agentBaseUrl() + "/print", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ data: toBase64(bytes), jobName, ...(printer ? { printer } : {}) }),
            signal,
            cache: "no-store",
        });
        if (!res.ok) {
            // Surface the agent's own error text, not just the status. It names the
            // printer the spooler rejected, which is the difference between "the
            // agent is down" and "config.json still points at a printer that was
            // unplugged months ago" — two failures that otherwise look identical.
            let detail = "";
            try { detail = String((await res.json())?.error || ""); } catch (_) { /* no JSON body */ }
            throw new Error(detail || `print agent responded ${res.status}`);
        }
    } finally {
        done();
    }
}
