package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatementPdfGeneratorTest {

    private final StatementPdfGenerator generator = new StatementPdfGenerator();

    @Test
    void generate_withValidData_returnsPdfBytes() {
        Statement statement = buildStatement();
        List<InvoiceBill> bills = List.of(buildBill());

        byte[] result = generator.generate(statement, bills, "Test Fuel Station");

        assertNotNull(result);
        assertTrue(result.length > 0);
        // PDF magic bytes: %PDF
        assertEquals('%', (char) result[0]);
        assertEquals('P', (char) result[1]);
        assertEquals('D', (char) result[2]);
        assertEquals('F', (char) result[3]);
    }

    @Test
    void generate_withEmptyBills_returnsPdfBytes() {
        Statement statement = buildStatement();

        byte[] result = generator.generate(statement, new ArrayList<>(), "Test Fuel Station");

        assertNotNull(result);
        assertTrue(result.length > 0);
        assertEquals('%', (char) result[0]);
        assertEquals('P', (char) result[1]);
    }

    @Test
    void generate_withNullAmounts_doesNotThrow() {
        Statement statement = buildStatement();
        statement.setTotalAmount(null);
        statement.setRoundingAmount(null);
        statement.setNetAmount(null);
        statement.setReceivedAmount(null);
        statement.setBalanceAmount(null);

        InvoiceBill bill = buildBill();
        bill.setNetAmount(null);

        byte[] result = assertDoesNotThrow(() ->
                generator.generate(statement, List.of(bill), "Test Fuel Station"));

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    private Statement buildStatement() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setName("Test Customer");

        Statement statement = new Statement();
        statement.setStatementNo("S26/100");
        statement.setCustomer(customer);
        statement.setFromDate(LocalDate.of(2026, 3, 1));
        statement.setToDate(LocalDate.of(2026, 3, 31));
        statement.setStatementDate(LocalDate.of(2026, 3, 31));
        statement.setTotalAmount(new BigDecimal("5000.75"));
        statement.setRoundingAmount(new BigDecimal("0.25"));
        statement.setNetAmount(new BigDecimal("5001.00"));
        statement.setReceivedAmount(BigDecimal.ZERO);
        statement.setBalanceAmount(new BigDecimal("5001.00"));
        return statement;
    }

    private InvoiceBill buildBill() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Diesel");

        InvoiceProduct ip = new InvoiceProduct();
        ip.setProduct(product);
        ip.setQuantity(new BigDecimal("50"));

        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setVehicleNumber("TN01AB1234");

        InvoiceBill bill = new InvoiceBill();
        bill.setBillNo("C26/1");
        bill.setDate(LocalDateTime.of(2026, 3, 5, 10, 0));
        bill.setNetAmount(new BigDecimal("5000.75"));
        bill.setVehicle(vehicle);
        bill.setProducts(new ArrayList<>(List.of(ip)));
        return bill;
    }
}
