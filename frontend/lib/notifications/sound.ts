"use client";

/**
 * Short in-browser "ding" generated with the Web Audio API — no audio file needed.
 * Two quick sine-wave blips; quieter than typical OS notification sounds so it
 * won't be jarring when a user is in the middle of other work.
 */

let audioCtx: AudioContext | null = null;

function getCtx(): AudioContext | null {
    if (typeof window === "undefined") return null;
    if (!audioCtx) {
        const Ctx = (window as unknown as { AudioContext?: typeof AudioContext; webkitAudioContext?: typeof AudioContext }).AudioContext
            ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
        if (!Ctx) return null;
        audioCtx = new Ctx();
    }
    return audioCtx;
}

export function playDing() {
    const ctx = getCtx();
    if (!ctx) return;
    if (ctx.state === "suspended") {
        ctx.resume().catch(() => {});
    }

    const now = ctx.currentTime;
    blip(ctx, now, 880);
    blip(ctx, now + 0.12, 1320);
}

function blip(ctx: AudioContext, startAt: number, freq: number) {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "sine";
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(0, startAt);
    gain.gain.linearRampToValueAtTime(0.18, startAt + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, startAt + 0.18);
    osc.connect(gain).connect(ctx.destination);
    osc.start(startAt);
    osc.stop(startAt + 0.2);
}
