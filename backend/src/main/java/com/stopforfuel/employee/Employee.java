package com.stopforfuel.employee;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String designation;
    private String email;
    private String phone;
    private Double salary;
    private LocalDate joinDate;
    private String status = "Active";
}
