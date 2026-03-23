package com.stopforfuel.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_category")
@Getter
@Setter
public class CustomerCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must not exceed 100 characters")
    @Column(name = "category_name", nullable = false)
    private String categoryName;

    /** GOVERNMENT or NON_GOVERNMENT */
    @NotBlank(message = "Category type is required")
    @Size(max = 20)
    @Column(name = "category_type", nullable = false)
    private String categoryType;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "customerCategory")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<Customer> customers = new ArrayList<>();
}
