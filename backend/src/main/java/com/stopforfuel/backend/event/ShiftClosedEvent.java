package com.stopforfuel.backend.event;

/**
 * Published from ShiftService.doApproveAndClose after the surrounding @Transactional commits.
 * Consumers should use @TransactionalEventListener(phase = AFTER_COMMIT) so a rolled-back
 * close never fires downstream notifications.
 */
public record ShiftClosedEvent(Long shiftId, Long scid) {}
