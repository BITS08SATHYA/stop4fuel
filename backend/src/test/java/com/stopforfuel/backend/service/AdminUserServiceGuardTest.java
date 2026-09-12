package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.exception.PrivilegeException;
import com.stopforfuel.backend.repository.DesignationRepository;
import com.stopforfuel.backend.repository.RolesRepository;
import com.stopforfuel.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The seniority guards are the half of the model that permissions cannot express: USER_UPDATE
 * says "may touch user management", these say "may touch <i>this</i> user". Without them an
 * OWNER holding USER_UPDATE can demote the proprietor or mint themselves an accomplice.
 */
class AdminUserServiceGuardTest {

    private UserRepository userRepository;
    private RolesRepository rolesRepository;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        rolesRepository = mock(RolesRepository.class);
        DesignationRepository designationRepository = mock(DesignationRepository.class);
        CognitoIdentityProviderClient cognitoClient = mock(CognitoIdentityProviderClient.class);

        service = new AdminUserService(userRepository, rolesRepository, designationRepository, cognitoClient);

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rolesRepository.findByRoleType(anyString())).thenAnswer(inv -> Optional.of(role(inv.getArgument(0))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- helpers ---------------------------------------------------------------------------

    private static Roles role(String type) {
        Roles r = new Roles();
        r.setRoleType(type);
        return r;
    }

    private static User user(long id, String roleType) {
        User u = new Employee();
        u.setId(id);
        u.setName("User " + id);
        u.setRole(role(roleType));
        u.setStatus(EntityStatus.ACTIVE);
        u.setScid(1L);
        return u;
    }

    private void authenticatedAs(String roleType) {
        Map<String, Object> principal = Map.of(
                "sub", "99",
                "name", "Caller",
                "custom:role", roleType,
                "custom:scid", "1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + roleType))));
    }

    private void targetIs(User target) {
        when(userRepository.findByIdAndScid(eq(target.getId()), anyLong())).thenReturn(Optional.of(target));
    }

    // --- role assignment -------------------------------------------------------------------

    @Test
    void ownerCannotPromoteAnyoneToOwner() {
        authenticatedAs("OWNER");
        targetIs(user(7L, "CASHIER"));

        PrivilegeException ex = assertThrows(PrivilegeException.class,
                () -> service.updateUserRole(7L, "OWNER", null));
        assertTrue(ex.getMessage().contains("cannot assign the OWNER role"), ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void ownerCannotPromoteAnyoneToPrime() {
        authenticatedAs("OWNER");
        targetIs(user(7L, "CASHIER"));

        assertThrows(PrivilegeException.class, () -> service.updateUserRole(7L, "PRIME", null));
        verify(userRepository, never()).save(any());
    }

    @Test
    void ownerCannotDemoteThePrime() {
        authenticatedAs("OWNER");
        targetIs(user(1L, "PRIME"));

        PrivilegeException ex = assertThrows(PrivilegeException.class,
                () -> service.updateUserRole(1L, "EMPLOYEE", null));
        assertTrue(ex.getMessage().contains("cannot change the role of a PRIME"), ex.getMessage());
    }

    @Test
    void ownerCannotDemoteAnotherOwner() {
        authenticatedAs("OWNER");
        targetIs(user(5L, "OWNER"));

        assertThrows(PrivilegeException.class, () -> service.updateUserRole(5L, "CASHIER", null));
    }

    @Test
    void ownerMayStillManageJuniorRoles() {
        authenticatedAs("OWNER");
        targetIs(user(7L, "CASHIER"));

        User updated = service.updateUserRole(7L, "EMPLOYEE", null);
        assertEquals("EMPLOYEE", updated.getRole().getRoleType());
    }

    @Test
    void primeMayPromoteToOwner() {
        authenticatedAs("PRIME");
        targetIs(user(7L, "CASHIER"));

        User updated = service.updateUserRole(7L, "OWNER", null);
        assertEquals("OWNER", updated.getRole().getRoleType());
    }

    // --- disable / deactivate ----------------------------------------------------------------

    @Test
    void primeMayAppointASuccessor() {
        // Succession has to be possible, otherwise the last-PRIME guard becomes a trap.
        authenticatedAs("PRIME");
        targetIs(user(7L, "OWNER"));

        User updated = service.updateUserRole(7L, "PRIME", null);
        assertEquals("PRIME", updated.getRole().getRoleType());
    }

    @Test
    void ownerCannotDisableThePrime() {
        authenticatedAs("OWNER");
        targetIs(user(1L, "PRIME"));

        assertThrows(PrivilegeException.class, () -> service.disableUser(1L));
        assertThrows(PrivilegeException.class, () -> service.toggleUserStatus(1L));
    }

    @Test
    void lastActivePrimeCannotBeRemovedEvenByAPrime() {
        authenticatedAs("PRIME");
        targetIs(user(1L, "PRIME"));
        when(userRepository.countByRoleRoleTypeAndScidAndStatus("PRIME", 1L, EntityStatus.ACTIVE))
                .thenReturn(1L);

        PrivilegeException ex = assertThrows(PrivilegeException.class, () -> service.disableUser(1L));
        assertTrue(ex.getMessage().contains("last active PRIME"), ex.getMessage());
    }

    @Test
    void aPrimeCanBeRemovedOnceAnotherPrimeExists() {
        authenticatedAs("PRIME");
        targetIs(user(1L, "PRIME"));
        when(userRepository.countByRoleRoleTypeAndScidAndStatus("PRIME", 1L, EntityStatus.ACTIVE))
                .thenReturn(2L);

        assertDoesNotThrow(() -> service.disableUser(1L));
    }

    // --- passcode / MFA ----------------------------------------------------------------------

    @Test
    void resettingAPeersPasscodeIsRefused() {
        // A reset returns the plaintext code, so it is a login as that user in all but name.
        authenticatedAs("OWNER");
        targetIs(user(5L, "OWNER"));

        assertThrows(PrivilegeException.class, () -> service.resetPasscode(5L));
        assertThrows(PrivilegeException.class, () -> service.resetMfa(5L));
    }

    @Test
    void primeCanResetAJuniorPasscode() {
        authenticatedAs("PRIME");
        targetIs(user(7L, "CASHIER"));

        String passcode = service.resetPasscode(7L);
        assertNotNull(passcode);
        assertEquals(4, passcode.length());
    }

    // --- creation ------------------------------------------------------------------------------

    @Test
    void ownerCannotCreateAPeerOrSuperior() {
        authenticatedAs("OWNER");

        assertThrows(PrivilegeException.class,
                () -> service.createUserWithPhone("Mallory", "9999999999", "OWNER", null, "EMPLOYEE"));
        assertThrows(PrivilegeException.class,
                () -> service.createUserWithPhone("Mallory", "9999999999", "PRIME", null, "EMPLOYEE"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void unauthenticatedCallersHaveNoAuthority() {
        SecurityContextHolder.clearContext();
        targetIs(user(7L, "CASHIER"));

        assertThrows(PrivilegeException.class, () -> service.updateUserRole(7L, "EMPLOYEE", null));
    }
}
