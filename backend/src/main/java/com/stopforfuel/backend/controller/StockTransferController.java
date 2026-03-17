package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.StockTransfer;
import com.stopforfuel.backend.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-transfers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StockTransferController {

    private final StockTransferService service;

    @GetMapping
    public List<StockTransfer> getAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (productId != null) return service.getByProduct(productId);
        if (from != null && to != null) return service.getByDateRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        return service.getAll();
    }

    @PostMapping
    public StockTransfer create(@RequestBody StockTransfer transfer) {
        return service.createTransfer(transfer);
    }
}
