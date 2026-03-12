package com.stopforfuel.backend.entity.transaction;
import com.stopforfuel.backend.entity.ExpenseType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@DiscriminatorValue("EXPENSE")
@Getter @Setter
public class ExpenseTransaction extends ShiftTransaction {
    @Column(name = "expense_amount", precision = 19, scale = 4)
    private BigDecimal expenseAmount;

    @Column(name = "expense_description")
    private String expenseDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_type_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ExpenseType expenseType;
}
