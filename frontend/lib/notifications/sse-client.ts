"use client";

/**
 * Minimal fetch-based SSE client. Unlike the native EventSource, this one can
 * send Authorization: Bearer headers (required by our Spring Security setup).
 *
 * Handles:
 *   - Auto-reconnect with exponential backoff
 *   - Heartbeat comment frames (ignored)
 *   - event/data/id fields per W3C SSE spec
 */

export interface SseEvent {
    event: string;
    data: string;
    id?: string;
}

export interface OpenStreamOptions {
    url: string;
    getAuthToken: () => Promise<string | null>;
    onEvent: (event: SseEvent) => void;
    signal: AbortSignal;
}

const INITIAL_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 30_000;

export async function openStream(opts: OpenStreamOptions): Promise<void> {
    let backoff = INITIAL_BACKOFF_MS;

    while (!opts.signal.aborted) {
        try {
            await connectOnce(opts);
            backoff = INITIAL_BACKOFF_MS;
        } catch (err) {
            if (opts.signal.aborted) return;
            // swallow and retry
        }
        if (opts.signal.aborted) return;
        await sleep(backoff, opts.signal);
        backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
    }
}

async function connectOnce(opts: OpenStreamOptions): Promise<void> {
    const token = await opts.getAuthToken();
    const headers: Record<string, string> = { Accept: "text/event-stream" };
    if (token) headers.Authorization = `Bearer ${token}`;

    const res = await fetch(opts.url, {
        headers,
        credentials: "include",
        signal: opts.signal,
    });
    if (!res.ok || !res.body) {
        throw new Error(`SSE failed with status ${res.status}`);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (!opts.signal.aborted) {
        const { value, done } = await reader.read();
        if (done) return;
        buffer += decoder.decode(value, { stream: true });

        // SSE frames are separated by a blank line ("\n\n")
        let sep: number;
        while ((sep = buffer.indexOf("\n\n")) !== -1) {
            const raw = buffer.slice(0, sep);
            buffer = buffer.slice(sep + 2);
            const parsed = parseFrame(raw);
            if (parsed) opts.onEvent(parsed);
        }
    }
}

function parseFrame(frame: string): SseEvent | null {
    const lines = frame.split("\n");
    let event = "message";
    let data = "";
    let id: string | undefined;
    for (const line of lines) {
        if (line.length === 0) continue;
        if (line.startsWith(":")) continue; // heartbeat / comment
        const colon = line.indexOf(":");
        const field = colon === -1 ? line : line.slice(0, colon);
        const value = colon === -1 ? "" : line.slice(colon + 1).replace(/^ /, "");
        if (field === "event") event = value;
        else if (field === "data") data += (data ? "\n" : "") + value;
        else if (field === "id") id = value;
    }
    if (!data && event === "message") return null;
    return { event, data, id };
}

function sleep(ms: number, signal: AbortSignal): Promise<void> {
    return new Promise(resolve => {
        const t = setTimeout(resolve, ms);
        signal.addEventListener("abort", () => { clearTimeout(t); resolve(); }, { once: true });
    });
}
