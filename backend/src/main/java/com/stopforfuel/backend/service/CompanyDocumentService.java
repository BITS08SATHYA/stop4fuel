package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Company;
import com.stopforfuel.backend.entity.CompanyDocument;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.CompanyDocumentRepository;
import com.stopforfuel.backend.repository.CompanyRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyDocumentService {

    private final CompanyDocumentRepository documentRepository;

    private final CompanyRepository companyRepository;

    private final S3StorageService s3StorageService;

    @Transactional(readOnly = true)
    public List<CompanyDocument> getDocumentsByCompany(Long companyId) {
        return documentRepository.findByCompanyIdAndScid(companyId, SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public CompanyDocument getDocumentById(Long id) {
        return documentRepository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    public CompanyDocument createDocument(Long companyId, CompanyDocument document) {
        Company company = companyRepository.findByIdAndScid(companyId, SecurityUtils.getScid())
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        document.setCompany(company);
        return documentRepository.save(document);
    }

    public CompanyDocument updateDocument(Long id, CompanyDocument updated) {
        CompanyDocument existing = getDocumentById(id);
        existing.setDocumentType(updated.getDocumentType());
        existing.setDocumentName(updated.getDocumentName());
        existing.setDescription(updated.getDescription());
        existing.setExpiryDate(updated.getExpiryDate());
        return documentRepository.save(existing);
    }

    public CompanyDocument uploadFile(Long documentId, MultipartFile file) throws IOException {
        validateFileType(file);
        CompanyDocument document = getDocumentById(documentId);

        String ext = getExtension(file.getOriginalFilename());
        String key = "companies/" + document.getCompany().getId() + "/documents/" + documentId + "." + ext;

        if (document.getFileUrl() != null && !document.getFileUrl().isEmpty()) {
            s3StorageService.delete(document.getFileUrl());
        }

        s3StorageService.upload(key, file);
        document.setFileUrl(key);
        document.setFileName(file.getOriginalFilename());
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public String getFilePresignedUrl(Long documentId) {
        CompanyDocument document = getDocumentById(documentId);
        if (document.getFileUrl() == null || document.getFileUrl().isEmpty()) {
            throw new ResourceNotFoundException("No file uploaded for this document");
        }
        return s3StorageService.getPresignedUrl(document.getFileUrl());
    }

    public void deleteDocument(Long id) {
        CompanyDocument document = getDocumentById(id);
        if (document.getFileUrl() != null && !document.getFileUrl().isEmpty()) {
            s3StorageService.delete(document.getFileUrl());
        }
        documentRepository.delete(document);
    }

    private void validateFileType(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size exceeds maximum allowed: 10MB");
        }
        String contentType = file.getContentType();
        String[] allowedTypes = {"image/jpeg", "image/png", "image/webp", "application/pdf"};
        boolean valid = false;
        for (String type : allowedTypes) {
            if (type.equals(contentType)) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType + ". Allowed: JPEG, PNG, WebP, PDF");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
