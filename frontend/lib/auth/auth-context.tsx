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
    role: string;
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
    logout: () => Promise<void>;
    hasPermission: (code: string) => boolean;
}

const AuthContext = createContext<AuthContextType>({
    user: null,
    isLoading: true,
    isAuthenticated: false,
    accessToken: null,
    login: async () => ({ success: false }),
    logout: async () => {},
    hasPermission: () => false,
});

export function useAuth() {
    return useContext(AuthContext);
}

const DEV_MODE = !process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;

function setAuthCookie(token: string) {
    document.cookie = `sff-auth-session=${token}; path=/; max-age=3600; SameSite=Lax; Secure`;
}

function clearAuthCookie() {
    document.cookie = "sff-auth-session=; path=/; max-age=0";
}

const getApiBaseUrl = () => {
    if (typeof window !== "undefined") {
        return (
            process.env.NEXT_PUBLIC_API_URL ||
            `${window.location.protocol}//${window.location.hostname}:8080/api`
        );
    }
    return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api";
};

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [accessToken, setAccessToken] = useState<string | null>(null);

    const loadUser = useCallback(async () => {
        try {
            if (DEV_MODE) {
                const res = await fetchWithAuth(
                    `${getApiBaseUrl()}/auth/me`,
                );
                if (res.ok) {
                    const data = await res.json();
                    setUser({
                        cognitoId: data.cognitoId || "dev-user-001",
                        name: data.name || "Dev Owner",
                        email: data.email || "owner@stopforfuel.com",
                        role: data.role || "OWNER",
                        permissions: data.permissions || [],
                        id: data.id,
                        username: data.username,
                    });
                }
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
                    role: data.role || role,
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

    const logout = useCallback(async () => {
        try {
            if (!DEV_MODE) {
                await signOut();
            }
            setUser(null);
            setAccessToken(null);
            clearAuthCookie();
            window.location.href = "/";
        } catch {
            clearAuthCookie();
            window.location.href = "/";
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
                logout,
                hasPermission,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
}
