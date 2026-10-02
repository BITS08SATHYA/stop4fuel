package com.stopforfuel.backend.service;

import com.stopforfuel.backend.exception.PrivilegeException;
import com.stopforfuel.config.RoleHierarchy;
import com.stopforfuel.config.SecurityUtils;

/**
 * Rank checks shared by every path that creates, edits or assigns a role to a login.
 *
 * User Management and the Employees screen write the same row (Employee extends User), so
 * the seniority rules must hold on both — otherwise the Employees screen is a side door that
 * lets a junior role block a senior one or mint an account above its own rank.
 */
public final class SeniorityGuard {

    private SeniorityGuard() {}

    static String callerRole() {
        String role = SecurityUtils.getCurrentRole();
        if (role == null) {
            throw new PrivilegeException("Not authenticated");
        }
        return role;
    }

    /**
     * Refuse when the caller is not senior enough to act on a user holding {@code targetRole}.
     * Strict — an OWNER cannot touch another OWNER — except that PRIME may act on a peer PRIME,
     * because nothing sits above the top tier to remove a second, bad PRIME.
     */
    public static void assertOutranks(String targetRole, String action) {
        String caller = callerRole();
        if (RoleHierarchy.isPrime(caller) && RoleHierarchy.isPrime(targetRole)) return;
        if (!RoleHierarchy.outranks(caller, targetRole)) {
            throw new PrivilegeException(
                    "A " + caller + " cannot " + action + " a " + (targetRole == null ? "user" : targetRole)
                            + ". This action is reserved for a more senior role.");
        }
    }

    /**
     * Refuse when the caller hands out a role at or above their own rank. PRIME may appoint
     * another PRIME so succession stays possible.
     */
    public static void assertMayAssign(String newRoleType) {
        String caller = callerRole();
        if (RoleHierarchy.isPrime(caller) && RoleHierarchy.isPrime(newRoleType)) return;
        if (!RoleHierarchy.outranks(caller, newRoleType)) {
            throw new PrivilegeException(
                    "A " + caller + " cannot assign the " + newRoleType + " role. You may only assign roles "
                            + "below your own.");
        }
    }
}
