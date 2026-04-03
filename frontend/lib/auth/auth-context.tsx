"use client";

import React, {
    createContext,
    useContext,
    useEffect,
    useState,
    useCallback,
} from "react";
import { fetchAuthSession, signOut, signIn } from "aws-amplify/auth";
import { fetchWithAuth } from "@/lib/api/fetch-with-auth";

interface AuthUser {
    id?: number;
    cognitoId: string;
    username?: string;
    name: string;
    email?: string;
    phone?: string;
    role: string;
    designation?: string;
    permissions: string[];
}

interface AuthContextType {
    user: AuthUser | null;
    isLoading: boolean;
    isAuthenticated: boolean;
    accessToken: string | null;
    login: (
        email: string,
        password: string,
    ) => Promise<{
        success: boolean;
        error?: string;
        challengeName?: string;
    }>;
    loginWithPasscode: (
        phone: string,
        passcode: string,
    ) => Promise<{
        success: boolean;
        error?: string;
    }>;
    logout: () => Promise<void>;
    hasPermission: (code: string) => boolean;
}

const AuthContext = createContext<AuthContextType>({
    user: null,
    isLoading: true,
    isAuthenticated: false,
    accessToken: null,
    login: async () => ({ success: false }),
    loginWithPasscode: async () => ({ success: false }),
    logout: async () => {},
    hasPermission: () => false,
});

export function useAuth() {
    return useContext(AuthContext);
}

const DEV_MODE = !process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;

function setAuthCookie(token: string) {
    const secure = window.location.protocol === "https:" ? "; Secure" : "";
    document.cookie = `sff-auth-session=${token}; path=/; max-age=3600; SameSite=Lax${secure}`;
}

function clearAuthCookie() {
    document.cookie = "sff-auth-session=; path=/; max-age=0";
}

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        const host = window.location.hostname;
        // devapp.stopforfuel.com → devapi.stopforfuel.com
        if (host.startsWith("devapp.")) {
            return `${window.location.protocol}//devapi.${host.slice(7)}/api`;
        }
        return (
            process.env.NEXT_PUBLIC_API_URL ||
            `${window.location.protocol}//${host}:8080/api`
        );
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

export function getDashboardType(designation?: string, role?: string): "owner" | "cashier" | "employee" | "customer" {
    if (role === "CUSTOMER") return "customer";
    if (role === "OWNER" || role === "ADMIN") return "owner";

    const designationMap: Record<string, "owner" | "cashier" | "employee"> = {
        "Manager": "owner",
        "Supervisor": "owner",
        "Cashier": "cashier",
    };
    return designationMap[designation || ""] || "employee";
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [accessToken, setAccessToken] = useState<string | null>(null);

    const loadUser = useCallback(async () => {
        try {
            // Always check for passcode token first (works in both dev and prod)
            const storedToken = localStorage.getItem("sff-token");
            if (storedToken) {
                const res = await fetch(
                    `${getApiBaseUrl()}/auth/me`,
                    { headers: { Authorization: `Bearer ${storedToken}` } }
                );
                if (res.ok) {
                    const data = await res.json();
                    setUser({
                        id: data.id,
                        cognitoId: data.cognitoId || "",
                        username: data.username,
                        name: data.name || "User",
                        email: data.email,
                        phone: data.phone,
                        role: data.role || "EMPLOYEE",
                        designation: data.designation,
                        permissions: data.permissions || [],
                    });
                    setAccessToken(storedToken);
                    setAuthCookie(storedToken);
                    setIsLoading(false);
                    return;
                } else {
                    // Token expired or invalid, clear it
                    localStorage.removeItem("sff-token");
                    clearAuthCookie();
                }
            }

            if (DEV_MODE) {
                // No token and no Cognito: send to login page
                setUser(null);
                setIsLoading(false);
                return;
            }

            const session = await fetchAuthSession();
            const token = session.tokens?.accessToken?.toString();

            if (!token) {
                setUser(null);
                setAccessToken(null);
                clearAuthCookie();
                setIsLoading(false);
                return;
            }

            setAccessToken(token);
            setAuthCookie(token);

            const idToken = session.tokens?.idToken;
            const cognitoId =
                (idToken?.payload?.sub as string) || "";
            const role =
                (idToken?.payload?.["custom:role"] as string) ||
                "EMPLOYEE";

            const res = await fetchWithAuth(
                `${getApiBaseUrl()}/auth/me`,
            );

            if (res.ok) {
                const data = await res.json();
                setUser({
                    id: data.id,
                    cognitoId: data.cognitoId || cognitoId,
                    username: data.username,
                    name:
                        data.name ||
                        (idToken?.payload?.name as string) ||
                        "User",
                    email:
                        data.email ||
                        (idToken?.payload?.email as string),
                    phone: data.phone,
                    role: data.role || role,
                    designation: data.designation,
                    permissions: data.permissions || [],
                });
            } else {
                setUser({
                    cognitoId,
                    name:
                        (idToken?.payload?.name as string) || "User",
                    email: idToken?.payload?.email as string,
                    role,
                    permissions: [],
                });
            }
        } catch {
            setUser(null);
            setAccessToken(null);
            clearAuthCookie();
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadUser();
    }, [loadUser]);

    const login = useCallback(
        async (
            email: string,
            password: string,
        ): Promise<{
            success: boolean;
            error?: string;
            challengeName?: string;
        }> => {
            if (DEV_MODE) return { success: true };
            try {
                const result = await signIn({
                    username: email,
                    password,
                });
                if (result.isSignedIn) {
                    await loadUser();
                    return { success: true };
                }
                if (result.nextStep?.signInStep) {
                    return {
                        success: false,
                        challengeName: result.nextStep.signInStep,
                    };
                }
                return { success: false, error: "Sign in incomplete" };
            } catch (err: unknown) {
                const error = err as Error;
                return {
                    success: false,
                    error: error.message || "Sign in failed",
                };
            }
        },
        [loadUser],
    );

    const loginWithPasscode = useCallback(
        async (
            phone: string,
            passcode: string,
        ): Promise<{ success: boolean; error?: string }> => {
            try {
                const res = await fetch(
                    `${getApiBaseUrl()}/auth/login`,
                    {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ phone, passcode }),
                    }
                );

                if (!res.ok) {
                    const data = await res.json();
                    return { success: false, error: data.error || "Sign in failed" };
                }

                const data = await res.json();
                const token = data.token;

                setAccessToken(token);
                setAuthCookie(token);
                localStorage.setItem("sff-token", token);

                const userData = data.user;
                setUser({
                    id: userData.id,
                    cognitoId: userData.cognitoId || "",
                    username: userData.username,
                    name: userData.name || "User",
                    email: userData.email,
                    phone: userData.phone,
                    role: userData.role || "EMPLOYEE",
                    designation: userData.designation,
                    permissions: userData.permissions || [],
                });

                return { success: true };
            } catch (err: unknown) {
                const error = err as Error;
                return {
                    success: false,
                    error: error.message || "Sign in failed",
                };
            }
        },
        [],
    );

    const logout = useCallback(async () => {
        try {
            if (!DEV_MODE) {
                await signOut();
            }
            setUser(null);
            setAccessToken(null);
            clearAuthCookie();
            localStorage.removeItem("sff-token");
            window.location.href = "/login";
        } catch {
            clearAuthCookie();
            localStorage.removeItem("sff-token");
            window.location.href = "/login";
        }
    }, []);

    const hasPermission = useCallback(
        (code: string) => {
            if (!user) return false;
            if (user.role === "OWNER") return true;
            return user.permissions.includes(code);
        },
        [user],
    );

    return (
        <AuthContext.Provider
            value={{
                user,
                isLoading,
                isAuthenticated: !!user,
                accessToken,
                login,
                loginWithPasscode,
                logout,
                hasPermission,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
}
