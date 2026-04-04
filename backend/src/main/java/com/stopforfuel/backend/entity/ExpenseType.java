package com.stopforfuel.backend.entity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expense_type")
@Getter @Setter
public class ExpenseType extends SimpleBaseEntity {
    @jakarta.validation.constraints.NotBlank(message = "Expense type name is required")
    @Column(name = "type_name", nullable = false)
    @JsonProperty("name")
    private String typeName;
}
