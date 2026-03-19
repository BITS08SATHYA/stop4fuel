package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "person_entity")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
public abstract class PersonEntity extends BaseEntity {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "person_emails", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "email")
    private java.util.Set<String> emails = new java.util.HashSet<>();

    @ElementCollection
    @CollectionTable(name = "person_phones", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "phone_number")
    private java.util.Set<String> phoneNumbers = new java.util.HashSet<>();

    private String address;

    @Column(name = "person_type")
    private String personType; // e.g., "Individual", "Company"
}
