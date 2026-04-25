"use client";

import { useRef, useState, useEffect, KeyboardEvent } from "react";
import { Send } from "lucide-react";

interface MessageComposerProps {
    disabled?: boolean;
    onSend: (text: string) => void | Promise<void>;
}

const MAX_LEN = 4000;

export function MessageComposer({ disabled, onSend }: MessageComposerProps) {
    const [text, setText] = useState("");
    const [inFlight, setInFlight] = useState(false);
    const ref = useRef<HTMLTextAreaElement | null>(null);

    // Auto-grow up to ~5 lines.
    useEffect(() => {
        if (!ref.current) return;
        ref.current.style.height = "auto";
        ref.current.style.height = Math.min(ref.current.scrollHeight, 120) + "px";
    }, [text]);

    const submit = async () => {
        const trimmed = text.trim();
        if (!trimmed || disabled || inFlight) return;
        setInFlight(true);
        try {
            await onSend(trimmed);
            setText("");
        } finally {
            setInFlight(false);
        }
    };

    const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            submit();
        }
    };

    const sendDisabled = disabled || inFlight || text.trim().length === 0;

    return (
        <div className="flex items-end gap-2 border-t border-border px-3 py-2 bg-background/60">
            <textarea
                ref={ref}
                value={text}
                onChange={(e) => setText(e.target.value.slice(0, MAX_LEN))}
                onKeyDown={onKey}
                placeholder="Write a message…"
                rows={1}
                className="flex-1 resize-none bg-transparent text-sm text-foreground placeholder:text-muted-foreground focus:outline-none py-1 px-2 min-h-[32px] max-h-[120px]"
                disabled={disabled}
            />
            <button
                onClick={submit}
                disabled={sendDisabled}
                className="p-2 rounded-full bg-primary text-primary-foreground disabled:opacity-40 disabled:cursor-not-allowed hover:brightness-110 transition-all"
                aria-label="Send"
            >
                <Send className="w-4 h-4" />
            </button>
        </div>
    );
}
