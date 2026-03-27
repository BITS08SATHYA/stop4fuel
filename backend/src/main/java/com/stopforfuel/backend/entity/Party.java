package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "party")
@Getter
@Setter
public class Party extends SimpleBaseEntity {

    @NotBlank(message = "Party type is required")
    @Column(name = "party_type", nullable = false)
    private String partyType;
}
