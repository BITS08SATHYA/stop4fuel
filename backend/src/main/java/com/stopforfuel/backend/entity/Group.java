package com.stopforfuel.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_group") // 'group' is a reserved keyword in SQL
@Getter
@Setter
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "description")
    private String description;

    @jakarta.persistence.OneToMany(mappedBy = "group")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<Customer> customers = new java.util.ArrayList<>();
}
