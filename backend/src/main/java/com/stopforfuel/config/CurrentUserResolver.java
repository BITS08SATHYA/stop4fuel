package com.stopforfuel.config;

import com.stopforfuel.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the caller's database user id across both authentication paths.
 *
 * {@link SecurityUtils#getCurrentUserId()} only understands the passcode session, whose
 * principal carries the numeric id directly. A Cognito session's subject is the pool's UUID,
 * so it needs a lookup — and without one, every web session would come through as an unknown
 * actor: unattributed in the audit trail and exempt from the delete throttle, which short-
 * circuits on a null actor.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    /** Database user id of the caller, or null when genuinely unauthenticated. */
    public Long currentUserId() {
        Long direct = SecurityUtils.getCurrentUserId();
        if (direct != null) return direct;

        String cognitoId = SecurityUtils.getCognitoId();
        if (cognitoId == null) return null;

        return userRepository.findByCognitoId(cognitoId)
                .map(com.stopforfuel.backend.entity.User::getId)
                .orElse(null);
    }
}
