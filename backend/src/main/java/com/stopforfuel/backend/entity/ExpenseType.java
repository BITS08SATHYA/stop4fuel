package com.stopforfuel.backend.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expense_type")
@Getter @Setter
public class ExpenseType extends SimpleBaseEntity {
    @Column(name = "type_name", nullable = false)
    private String typeName;
}
