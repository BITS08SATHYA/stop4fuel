"use client";

import { useEffect } from "react";
import { AlertTriangle, RefreshCw, ArrowLeft } from "lucide-react";
import Link from "next/link";

interface SectionErrorProps {
    error: Error & { digest?: string };
    reset: () => void;
    section: string;
    backHref: string;
    backLabel: string;
}

export function SectionError({
    error,
    reset,
    section,
    backHref,
    backLabel,
}: SectionErrorProps) {
    useEffect(() => {
        console.error(`${section} error:`, error);
    }, [section, error]);

    return (
        <div className="flex flex-1 items-center justify-center p-8">
            <div className="mx-auto max-w-md text-center">
                <div className="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-red-500/10">
                    <AlertTriangle className="h-8 w-8 text-red-500" />
                </div>
                <h1 className="mb-2 text-2xl font-bold">
                    {section} Error
                </h1>
                <p className="mb-6 text-muted-foreground">
                    Something went wrong loading {section.toLowerCase()}. This
                    could be a temporary issue — please try again.
                </p>
                {error.digest && (
                    <p className="mb-4 text-xs text-muted-foreground/60">
                        Error ID: {error.digest}
                    </p>
                )}
                <div className="flex items-center justify-center gap-3">
                    <button
                        onClick={reset}
                        className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
                    >
                        <RefreshCw className="h-4 w-4" />
                        Try Again
                    </button>
                    <Link
                        href={backHref}
                        className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2.5 text-sm font-medium transition-colors hover:bg-accent"
                    >
                        <ArrowLeft className="h-4 w-4" />
                        {backLabel}
                    </Link>
                </div>
            </div>
        </div>
    );
}
