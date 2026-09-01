package com.stopforfuel.config;

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

import static org.junit.jupiter.api.Assertions.*;

class DevJwtAuthFilterTest {

    private static final String SECRET = "unit-test-signing-key-at-least-32-chars-long";

    private JwtTokenProvider providerWithSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(new MockEnvironment());
        ReflectionTestUtils.setField(provider, "secret", SECRET);
        return provider;
    }

    private Authentication authenticateWith(String header, Cookie cookie) throws Exception {
        JwtTokenProvider provider = providerWithSecret();
        DevJwtAuthFilter filter = new DevJwtAuthFilter(provider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader("Authorization", "Bearer " + header);
        }
        if (cookie != null) {
            request.setCookies(cookie);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
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
}
