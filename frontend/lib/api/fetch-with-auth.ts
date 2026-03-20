"use client";

import { fetchAuthSession } from "aws-amplify/auth";

const DEV_MODE = !process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;

export async function fetchWithAuth(
    url: string,
    options: RequestInit = {}
): Promise<Response> {
    const headers = new Headers(options.headers || {});

    if (!DEV_MODE) {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();
            if (token) {
                headers.set("Authorization", `Bearer ${token}`);
            }
        } catch {
            // If session fetch fails, redirect to login
            if (typeof window !== "undefined") {
                window.location.href = "/login";
            }
            throw new Error("Not authenticated");
        }
    }

    const response = await fetch(url, { ...options, headers });

    if (response.status === 401) {
        if (typeof window !== "undefined" && !DEV_MODE) {
            window.location.href = "/login";
        }
        throw new Error("Unauthorized");
    }

    return response;
}
