package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.entity.UtilityBill;
import com.stopforfuel.backend.service.UtilityBillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/utility-bills")
public class UtilityBillController {

    @Autowired
    private UtilityBillService utilityBillService;

    @GetMapping
    public List<UtilityBill> getAllBills(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return utilityBillService.getBillsByType(type);
        }
        return utilityBillService.getAllBills();
    }

    @GetMapping("/pending")
    public List<UtilityBill> getPendingBills() {
        return utilityBillService.getPendingBills();
    }

    @PostMapping
    public UtilityBill createBill(@RequestBody UtilityBill bill) {
        return utilityBillService.createBill(bill);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UtilityBill> updateBill(@PathVariable Long id, @RequestBody UtilityBill bill) {
        try {
            return ResponseEntity.ok(utilityBillService.updateBill(id, bill));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBill(@PathVariable Long id) {
        utilityBillService.deleteBill(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload-pdf")
    public ResponseEntity<UtilityBill> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            UtilityBill parsed = utilityBillService.parseTnebPdf(file);
            return ResponseEntity.ok(parsed);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/upload-bulk")
    public ResponseEntity<List<UtilityBill>> uploadBulkPdfs(@RequestParam("files") List<MultipartFile> files) {
        List<UtilityBill> parsed = utilityBillService.parseBulkPdfs(files);
        return ResponseEntity.ok(parsed);
    }
}
