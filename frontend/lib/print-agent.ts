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

/** True if the local print agent answers a health check. Never throws. */
export async function probePrintAgent(): Promise<boolean> {
    const { signal, done } = withTimeout(1200);
    try {
        const res = await fetch(agentBaseUrl() + "/health", { signal, cache: "no-store" });
        return res.ok;
    } catch (_) {
        return false;
    } finally {
        done();
    }
}

/**
 * Send raw ESC/POS bytes to the agent for printing.
 * Resolves on success; throws on any failure so the caller can fall back.
 */
export async function sendToPrintAgent(bytes: Uint8Array, jobName: string): Promise<void> {
    const { signal, done } = withTimeout(8000);
    try {
        const res = await fetch(agentBaseUrl() + "/print", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ data: toBase64(bytes), jobName }),
            signal,
            cache: "no-store",
        });
        if (!res.ok) {
            throw new Error(`print agent responded ${res.status}`);
        }
    } finally {
        done();
    }
}
