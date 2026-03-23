"use client";

import { useEffect } from "react";

export default function GlobalError({
    error,
    reset,
}: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    useEffect(() => {
        console.error("Global error:", error);
    }, [error]);

    return (
        <html lang="en">
            <body className="flex min-h-screen items-center justify-center bg-gray-950 text-white">
                <div className="mx-auto max-w-md text-center p-8">
                    <div className="mb-6 text-6xl">⚠</div>
                    <h1 className="mb-2 text-2xl font-bold">
                        Something went wrong
                    </h1>
                    <p className="mb-6 text-gray-400">
                        An unexpected error occurred. Please try again or
                        refresh the page.
                    </p>
                    {error.digest && (
                        <p className="mb-4 text-xs text-gray-500">
                            Error ID: {error.digest}
                        </p>
                    )}
                    <button
                        onClick={reset}
                        className="rounded-lg bg-blue-600 px-6 py-2.5 text-sm font-medium text-white transition-colors hover:bg-blue-700"
                    >
                        Try Again
                    </button>
                </div>
            </body>
        </html>
    );
}
