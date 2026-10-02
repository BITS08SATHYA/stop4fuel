package com.stopforfuel.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminDeniedPermissionsTest {

    @Test
    void deniesEveryDeleteAndUserUpdate() {
        assertTrue(DataInitializer.isAdminDenied("EMPLOYEE_DELETE"));
        assertTrue(DataInitializer.isAdminDenied("INVOICE_DELETE"));
        assertTrue(DataInitializer.isAdminDenied("PAYMENT_DELETE"));
        assertTrue(DataInitializer.isAdminDenied("USER_DELETE"));
        assertTrue(DataInitializer.isAdminDenied("USER_UPDATE"));
    }

    @Test
    void keepsDayToDayPermissions() {
        assertFalse(DataInitializer.isAdminDenied("INVOICE_CREATE"));
        assertFalse(DataInitializer.isAdminDenied("EMPLOYEE_UPDATE"));
        assertFalse(DataInitializer.isAdminDenied("USER_VIEW"));
        assertFalse(DataInitializer.isAdminDenied("USER_CREATE"));
    }
}
