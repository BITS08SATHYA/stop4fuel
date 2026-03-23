"use client";

import { SectionError } from "@/components/section-error";

export default function OperationsError({
    error,
    reset,
}: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    return (
        <SectionError
            error={error}
            reset={reset}
            section="Operations"
            backHref="/"
            backLabel="Dashboard"
        />
    );
}
