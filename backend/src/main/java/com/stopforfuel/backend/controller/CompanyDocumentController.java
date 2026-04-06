package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.CompanyDocumentDTO;
import com.stopforfuel.backend.entity.CompanyDocument;
import com.stopforfuel.backend.service.CompanyDocumentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/companies/{companyId}/documents")
public class CompanyDocumentController {

    @Autowired
    private CompanyDocumentService documentService;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public List<CompanyDocument> getDocuments(@PathVariable Long companyId) {
        return documentService.getDocumentsByCompany(companyId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public CompanyDocument getDocument(@PathVariable Long companyId, @PathVariable Long id) {
        return documentService.getDocumentById(id);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'SETTINGS_CREATE')")
    public CompanyDocument createDocument(@PathVariable Long companyId, @Valid @RequestBody CompanyDocument document) {
        return documentService.createDocument(companyId, document);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_UPDATE')")
    public CompanyDocument updateDocument(@PathVariable Long companyId, @PathVariable Long id,
                                          @Valid @RequestBody CompanyDocument document) {
        return documentService.updateDocument(id, document);
    }

    @PostMapping("/{id}/upload")
    @PreAuthorize("hasPermission(null, 'SETTINGS_UPDATE')")
    public CompanyDocument uploadFile(@PathVariable Long companyId, @PathVariable Long id,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        return documentService.uploadFile(id, file);
    }

    @GetMapping("/{id}/file-url")
    @PreAuthorize("hasPermission(null, 'SETTINGS_VIEW')")
    public ResponseEntity<Map<String, String>> getFileUrl(@PathVariable Long companyId, @PathVariable Long id) {
        String url = documentService.getFilePresignedUrl(id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'SETTINGS_DELETE')")
    public void deleteDocument(@PathVariable Long companyId, @PathVariable Long id) {
        documentService.deleteDocument(id);
    }
}
