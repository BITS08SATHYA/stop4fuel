"use client";

import { SectionError } from "@/components/section-error";

export default function MarketingError({
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
            section="Marketing"
            backHref="/"
            backLabel="Dashboard"
        />
    );
}
