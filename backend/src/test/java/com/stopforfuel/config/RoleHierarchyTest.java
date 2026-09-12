package com.stopforfuel.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoleHierarchyTest {

    @Test
    void primeOutranksEveryOtherRole() {
        for (String role : new String[]{"SYSTEM_ADMIN", "OWNER", "ADMIN", "CASHIER", "EMPLOYEE", "CUSTOMER"}) {
            assertTrue(RoleHierarchy.outranks("PRIME", role), "PRIME should outrank " + role);
            assertFalse(RoleHierarchy.outranks(role, "PRIME"), role + " should not outrank PRIME");
        }
    }

    @Test
    void ownerCannotActOnAPeerOrMintOne() {
        // The whole point of the tier: an OWNER may manage the ranks below, and no further.
        assertFalse(RoleHierarchy.outranks("OWNER", "OWNER"));
        assertFalse(RoleHierarchy.outranks("OWNER", "PRIME"));
        assertFalse(RoleHierarchy.outranks("OWNER", "SYSTEM_ADMIN"));
        assertTrue(RoleHierarchy.outranks("OWNER", "ADMIN"));
        assertTrue(RoleHierarchy.outranks("OWNER", "CASHIER"));
    }

    @Test
    void unknownAndNullRolesHaveNoAuthority() {
        assertEquals(0, RoleHierarchy.rank(null));
        assertEquals(0, RoleHierarchy.rank("SUPERUSER"));
        assertFalse(RoleHierarchy.outranks("SUPERUSER", "CUSTOMER"));
        assertFalse(RoleHierarchy.outranks(null, "CUSTOMER"));
        // ...and nobody outranks a role they cannot identify, so a junk target is not a hole.
        assertTrue(RoleHierarchy.outranks("PRIME", "SUPERUSER"));
    }

    @Test
    void roleNamesAreMatchedCaseAndWhitespaceInsensitively() {
        assertEquals(RoleHierarchy.rank("PRIME"), RoleHierarchy.rank(" prime "));
        assertTrue(RoleHierarchy.isPrime("prime"));
        assertTrue(RoleHierarchy.isPrime(" PRIME "));
        assertFalse(RoleHierarchy.isPrime("PRIMER"));
        assertFalse(RoleHierarchy.isPrime(null));
    }

    @Test
    void cashierOutranksNobodyWhoMatters() {
        assertFalse(RoleHierarchy.outranks("CASHIER", "ADMIN"));
        assertFalse(RoleHierarchy.outranks("CASHIER", "CASHIER"));
        assertTrue(RoleHierarchy.outranks("CASHIER", "CUSTOMER"));
    }
}
