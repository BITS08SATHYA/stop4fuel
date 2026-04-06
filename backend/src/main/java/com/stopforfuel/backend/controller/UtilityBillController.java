package com.stopforfuel.backend.controller;

import jakarta.validation.Valid;
import com.stopforfuel.backend.dto.UtilityBillDTO;
import com.stopforfuel.backend.entity.UtilityBill;
import com.stopforfuel.backend.service.UtilityBillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/utility-bills")
public class UtilityBillController {

    @Autowired
    private UtilityBillService utilityBillService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'FINANCE_VIEW')")
    public List<UtilityBillDTO> getAllBills(@RequestParam(required = false) String type) {
        List<UtilityBill> result;
        if (type != null && !type.isEmpty()) {
            result = utilityBillService.getBillsByType(type);
        } else {
            result = utilityBillService.getAllBills();
        }
        return result.stream().map(UtilityBillDTO::from).toList();
    }

    @GetMapping("/pending")
    @PreAuthorize("hasPermission(null, 'FINANCE_VIEW')")
    public List<UtilityBillDTO> getPendingBills() {
        return utilityBillService.getPendingBills().stream().map(UtilityBillDTO::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'FINANCE_CREATE')")
    public UtilityBillDTO createBill(@Valid @RequestBody UtilityBill bill) {
        return UtilityBillDTO.from(utilityBillService.createBill(bill));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'FINANCE_UPDATE')")
    public ResponseEntity<UtilityBillDTO> updateBill(@PathVariable Long id, @Valid @RequestBody UtilityBill bill) {
        try {
            return ResponseEntity.ok(UtilityBillDTO.from(utilityBillService.updateBill(id, bill)));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'FINANCE_DELETE')")
    public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
        utilityBillService.deleteBill(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload-pdf")
    @PreAuthorize("hasPermission(null, 'FINANCE_UPDATE')")
    public ResponseEntity<UtilityBillDTO> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            UtilityBill parsed = utilityBillService.parseTnebPdf(file);
            return ResponseEntity.ok(UtilityBillDTO.from(parsed));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/upload-bulk")
    @PreAuthorize("hasPermission(null, 'FINANCE_UPDATE')")
    public ResponseEntity<List<UtilityBillDTO>> uploadBulkPdfs(@RequestParam("files") List<MultipartFile> files) {
        List<UtilityBillDTO> parsed = utilityBillService.parseBulkPdfs(files).stream().map(UtilityBillDTO::from).toList();
        return ResponseEntity.ok(parsed);
    }
}
