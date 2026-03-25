"use client";

import { useAuth } from "@/lib/auth/auth-context";

interface PermissionGateProps {
  permission: string;
  children: React.ReactNode;
}

export function PermissionGate({ permission, children }: PermissionGateProps) {
  const { hasPermission } = useAuth();
  if (!hasPermission(permission)) return null;
  return <>{children}</>;
}
