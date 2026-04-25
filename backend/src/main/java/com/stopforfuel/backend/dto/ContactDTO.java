package com.stopforfuel.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * A user the caller is permitted to start a conversation with.
 * Fueled by {@code MessageService#listContactsForCurrentUser()} which applies
 * the admin↔cashier role-pair rule and same-scid filter.
 */
@Getter
@Setter
public class ContactDTO {

    private Long userId;
    private String name;
    private String role;
    private String designation;
    private String status;
}
