"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { fetchAuthSession, signOut } from "aws-amplify/auth";

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
    login: () => void;
    logout: () => Promise<void>;
    hasPermission: (code: string) => boolean;
}

const AuthContext = createContext<AuthContextType>({
    user: null,
    isLoading: true,
    isAuthenticated: false,
    accessToken: null,
    login: () => {},
    logout: async () => {},
    hasPermission: () => false,
});

export function useAuth() {
    return useContext(AuthContext);
}

const DEV_MODE = !process.env.NEXT_PUBLIC_COGNITO_USER_POOL_ID;

const getApiBaseUrl = () => {
    if (typeof window !== 'undefined') {
        return process.env.NEXT_PUBLIC_API_URL || `${window.location.protocol}//${window.location.hostname}:8080/api`;
    }
    return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';
};

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [accessToken, setAccessToken] = useState<string | null>(null);

    const loadUser = useCallback(async () => {
        try {
            if (DEV_MODE) {
                // Dev mode: no Cognito, fetch user from backend (which uses DevAuthFilter)
                const res = await fetch(`${getApiBaseUrl()}/auth/me`);
                if (res.ok) {
                    const data = await res.json();
                    setUser({
                        cognitoId: data.cognitoId || 'dev-user-001',
                        name: data.name || 'Dev Owner',
                        email: data.email || 'owner@stopforfuel.com',
                        role: data.role || 'OWNER',
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
                setIsLoading(false);
                return;
            }

            setAccessToken(token);

            const idToken = session.tokens?.idToken;
            const cognitoId = idToken?.payload?.sub as string || '';
            const role = (idToken?.payload?.['custom:role'] as string) || 'EMPLOYEE';

            // Fetch full user profile + permissions from backend
            const res = await fetch(`${getApiBaseUrl()}/auth/me`, {
                headers: { Authorization: `Bearer ${token}` },
            });

            if (res.ok) {
                const data = await res.json();
                setUser({
                    id: data.id,
                    cognitoId: data.cognitoId || cognitoId,
                    username: data.username,
                    name: data.name || (idToken?.payload?.name as string) || 'User',
                    email: data.email || (idToken?.payload?.email as string),
                    role: data.role || role,
                    permissions: data.permissions || [],
                });
            } else {
                // Fallback if /me fails (first-time user, sync in progress)
                setUser({
                    cognitoId,
                    name: (idToken?.payload?.name as string) || 'User',
                    email: (idToken?.payload?.email as string),
                    role,
                    permissions: [],
                });
            }
        } catch {
            // Not authenticated
            setUser(null);
            setAccessToken(null);
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        loadUser();
    }, [loadUser]);

    const login = useCallback(() => {
        if (DEV_MODE) return;
        // Redirect to Cognito Hosted UI
        const domain = process.env.NEXT_PUBLIC_COGNITO_DOMAIN;
        const clientId = process.env.NEXT_PUBLIC_COGNITO_CLIENT_ID;
        const redirectUri = `${window.location.origin}/auth/callback`;
        window.location.href = `https://${domain}/login?client_id=${clientId}&response_type=code&scope=openid+email+profile&redirect_uri=${encodeURIComponent(redirectUri)}`;
    }, []);

    const logout = useCallback(async () => {
        try {
            if (!DEV_MODE) {
                await signOut();
            }
            setUser(null);
            setAccessToken(null);
            window.location.href = process.env.NEXT_PUBLIC_LANDING_URL || '/login';
        } catch {
            window.location.href = process.env.NEXT_PUBLIC_LANDING_URL || '/login';
        }
    }, []);

    const hasPermission = useCallback((code: string) => {
        if (!user) return false;
        if (user.role === 'OWNER') return true;
        return user.permissions.includes(code);
    }, [user]);

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
