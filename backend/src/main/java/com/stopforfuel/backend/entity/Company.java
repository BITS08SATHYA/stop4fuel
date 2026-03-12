package com.stopforfuel.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "company")
@Getter
@Setter
public class Company extends BaseEntity {

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
