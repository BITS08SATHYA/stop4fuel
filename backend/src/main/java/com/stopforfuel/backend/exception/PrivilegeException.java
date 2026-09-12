package com.stopforfuel.backend.exception;

/**
 * Thrown when an authenticated caller is not senior enough for the action they
 * attempted — assigning a role at or above their own rank, acting on a peer, or
 * reaching a PRIME-only endpoint. Distinct from {@link BusinessException} so it
 * surfaces as 403 rather than 400.
 */
public class PrivilegeException extends RuntimeException {
    public PrivilegeException(String message) {
        super(message);
    }
}
