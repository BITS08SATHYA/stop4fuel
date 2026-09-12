package com.stopforfuel.config;

import java.util.Map;

/**
 * Seniority ranking for roles. A caller may only act on users, and assign roles,
 * strictly below their own rank — so an OWNER can manage ADMIN and below but can
 * neither touch another OWNER nor mint a new one.
 *
 * PRIME sits above OWNER and is the tenant's break-glass tier: it is the only role
 * that can remove an OWNER, reset a passcode, or reach the endpoints that rewrite
 * financial history. SYSTEM_ADMIN is the vendor-side support tier and sits between
 * the two so it can assist without being removable by the customer's own OWNER.
 */
public final class RoleHierarchy {

    public static final String PRIME = "PRIME";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String OWNER = "OWNER";

    private static final Map<String, Integer> RANKS = Map.of(
            PRIME, 100,
            SYSTEM_ADMIN, 90,
            OWNER, 80,
            "ADMIN", 60,
            "CASHIER", 40,
            "EMPLOYEE", 20,
            "DEALER", 15,
            "CUSTOMER", 10
    );

    private RoleHierarchy() {}

    /** Rank for a role name; unknown or null roles rank 0 (no authority over anyone). */
    public static int rank(String roleType) {
        if (roleType == null) return 0;
        return RANKS.getOrDefault(roleType.trim().toUpperCase(), 0);
    }

    public static boolean isPrime(String roleType) {
        return PRIME.equalsIgnoreCase(roleType == null ? null : roleType.trim());
    }

    /** True when {@code callerRole} outranks {@code targetRole} strictly. */
    public static boolean outranks(String callerRole, String targetRole) {
        return rank(callerRole) > rank(targetRole);
    }
}
