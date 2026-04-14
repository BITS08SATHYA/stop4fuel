"use client";

import { fetchAuthSession } from "aws-amplify/auth";

const DEV_MODE = !process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;

export async function fetchWithAuth(
    url: string,
    options: RequestInit = {}
): Promise<Response> {
    const headers = new Headers(options.headers || {});

    if (!headers.has("X-Request-Id")) {
        const rid =
            typeof crypto !== "undefined" && crypto.randomUUID
                ? crypto.randomUUID()
                : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
        headers.set("X-Request-Id", rid);
    }

    // In Cognito mode, add Authorization header from Cognito session
    if (!DEV_MODE) {
        try {
            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();
            if (token) {
                headers.set("Authorization", `Bearer ${token}`);
            }
        } catch {
            if (typeof window !== "undefined") {
                window.location.href = "/login";
            }
            throw new Error("Not authenticated");
        }
    }

    // Always include credentials so httpOnly cookie is sent
    const response = await fetch(url, { ...options, headers, credentials: "include" });

    if (response.status === 401) {
        if (typeof window !== "undefined") {
            window.location.href = "/login";
        }
        throw new Error("Unauthorized");
    }

    return response;
}
