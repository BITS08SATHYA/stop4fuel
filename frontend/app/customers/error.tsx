"use client";

import { SectionError } from "@/components/section-error";

export default function CustomersError({
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
            section="Customers"
            backHref="/"
            backLabel="Dashboard"
        />
    );
}
