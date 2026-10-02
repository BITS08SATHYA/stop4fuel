package com.stopforfuel.config;

import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevJwtAuthFilterTest {

    private static final String SECRET = "unit-test-signing-key-at-least-32-chars-long";

    private JwtTokenProvider providerWithSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(new MockEnvironment());
        ReflectionTestUtils.setField(provider, "secret", SECRET);
        return provider;
    }

    private static UserRepository usersReturning(EntityStatus status, String roleType) {
        UserRepository repo = mock(UserRepository.class);
        UserRepository.SessionState state = new UserRepository.SessionState() {
            public EntityStatus getStatus() { return status; }
            public String getRoleType() { return roleType; }
        };
        when(repo.findSessionStateById(anyLong())).thenReturn(Optional.of(state));
        return repo;
    }

    private Authentication authenticateWith(String header, Cookie cookie) throws Exception {
        return authenticateWith(header, cookie, usersReturning(EntityStatus.ACTIVE, "CASHIER"), new MockHttpServletResponse());
    }

    private Authentication authenticateWith(String header, Cookie cookie, UserRepository users,
                                            MockHttpServletResponse response) throws Exception {
        JwtTokenProvider provider = providerWithSecret();
        DevJwtAuthFilter filter = new DevJwtAuthFilter(provider, users);

        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("Authorization", "Bearer " + header);
        }
        if (cookie != null) {
            request.setCookies(cookie);
        }
        filter.doFilter(request, response, new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMfaPendingTokenAsSession() throws Exception {
        String mfaToken = providerWithSecret().generateMfaToken(42L, null);

        assertNull(authenticateWith(mfaToken, null),
                "A half-finished login token must not authenticate a request — that would let a "
                        + "correct passcode alone bypass TOTP for the token's lifetime.");
    }

    @Test
    void rejectsMfaPendingTokenPresentedAsCookie() throws Exception {
        String mfaToken = providerWithSecret().generateMfaToken(42L, null);

        assertNull(authenticateWith(null, new Cookie("sff-auth-session", mfaToken)),
                "The cookie path must reject the MFA token too, not just the Authorization header.");
    }

    @Test
    void acceptsFullSessionToken() throws Exception {
        String session = providerWithSecret()
                .generateToken(42L, "CASHIER", 1L, "Test User", "9999999999", "Cashier");

        Authentication auth = authenticateWith(session, null);

        assertNotNull(auth, "A completed-login session token must still authenticate.");
        assertTrue(auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_CASHIER")),
                "Role from the token should become the granted authority.");
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() throws Exception {
        JwtTokenProvider other = new JwtTokenProvider(new MockEnvironment());
        ReflectionTestUtils.setField(other, "secret", "a-completely-different-key-32-chars-min");
        String foreign = other.generateToken(1L, "OWNER", 1L, "Attacker", null, null);

        assertNull(authenticateWith(foreign, null),
                "Rotating the signing key must invalidate tokens minted with the old one.");
    }

    @Test
    void signsOutADeactivatedUserMidSession() throws Exception {
        String session = providerWithSecret()
                .generateToken(42L, "CASHIER", 1L, "Test User", "9999999999", "Cashier");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication auth = authenticateWith(session, null, usersReturning(EntityStatus.INACTIVE, "CASHIER"), response);

        assertNull(auth, "A deactivated user's still-valid token must stop working immediately.");
        assertEquals(401, response.getStatus());
    }

    @Test
    void signsOutAUserWhoseRoleChangedSinceLogin() throws Exception {
        String session = providerWithSecret()
                .generateToken(42L, "PRIME", 1L, "Test User", "9999999999", "Prime");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Authentication auth = authenticateWith(session, null, usersReturning(EntityStatus.ACTIVE, "ADMIN"), response);

        assertNull(auth, "A demoted user must not keep acting on the role baked into an old token.");
        assertEquals(401, response.getStatus());
    }

    @Test
    void signsOutADeletedUser() throws Exception {
        String session = providerWithSecret()
                .generateToken(42L, "CASHIER", 1L, "Test User", "9999999999", "Cashier");
        UserRepository users = mock(UserRepository.class);
        when(users.findSessionStateById(anyLong())).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertNull(authenticateWith(session, null, users, response));
        assertEquals(401, response.getStatus());
    }
}
