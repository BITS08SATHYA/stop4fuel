package com.stopforfuel.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
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
}
