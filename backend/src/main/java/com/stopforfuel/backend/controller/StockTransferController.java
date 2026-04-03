package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.StockTransferDTO;
import com.stopforfuel.backend.entity.StockTransfer;
import com.stopforfuel.backend.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService service;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_VIEW')")
    public List<StockTransferDTO> getAll(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<StockTransfer> result;
        if (productId != null) result = service.getByProduct(productId);
        else if (from != null && to != null) result = service.getByDateRange(from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        else result = service.getAll();
        return result.stream().map(StockTransferDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public StockTransferDTO create(@Valid @RequestBody StockTransfer transfer) {
        return StockTransferDTO.from(service.createTransfer(transfer));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public StockTransferDTO update(@PathVariable Long id, @Valid @RequestBody StockTransfer transfer) {
        return StockTransferDTO.from(service.updateTransfer(id, transfer));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTORY_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteTransfer(id);
        return ResponseEntity.noContent().build();
    }
}
