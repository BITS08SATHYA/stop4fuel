package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "company")
@Getter
@Setter
public class Company extends BaseEntity {

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    @Column(nullable = false)
    private String name;

    @Column(name = "open_date")
    private LocalDate openDate;

    @Column(name = "sap_code")
    private String sapCode;

    @Column(name = "gst_no")
    private String gstNo;

    private String site;

    private String type;

    private String address;

    private String phone;

    private String email;

    @Column(name = "logo_url")
    private String logoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User owner;
}
