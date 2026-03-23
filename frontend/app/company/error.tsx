"use client";

import { SectionError } from "@/components/section-error";

export default function CompanyError({
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
            section="Company"
            backHref="/"
            backLabel="Dashboard"
        />
    );
}
