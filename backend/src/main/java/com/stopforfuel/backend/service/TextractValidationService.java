package com.stopforfuel.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TextractValidationService {

    private final TextractClient textractClient;

    /**
     * Validates that the given image contains the expected bill number.
     * Returns true if the bill number is found in the OCR text, or if Textract is unavailable (fail open).
     */
    public boolean validateBillNumber(byte[] imageBytes, String expectedBillNo) {
        try {
            DetectDocumentTextRequest request = DetectDocumentTextRequest.builder()
                    .document(Document.builder()
                            .bytes(SdkBytes.fromByteArray(imageBytes))
                            .build())
                    .build();

            DetectDocumentTextResponse response = textractClient.detectDocumentText(request);

            String ocrText = response.blocks().stream()
                    .filter(b -> b.blockType() == BlockType.LINE || b.blockType() == BlockType.WORD)
                    .map(Block::text)
                    .collect(Collectors.joining(" "));

            log.info("OCR extracted text for bill {}: {}", expectedBillNo, ocrText);

            return matchesBillNumber(ocrText, expectedBillNo);
        } catch (Exception e) {
            log.warn("Textract validation failed, allowing upload (fail open): {}", e.getMessage());
            return true;
        }
    }

    private boolean matchesBillNumber(String ocrText, String expectedBillNo) {
        // Direct substring match (case-insensitive)
        if (ocrText.toLowerCase().contains(expectedBillNo.toLowerCase())) {
            return true;
        }

        // Normalized match: strip spaces and common OCR confusions
        String normalizedOcr = normalize(ocrText);
        String normalizedBill = normalize(expectedBillNo);

        if (normalizedOcr.contains(normalizedBill)) {
            return true;
        }

        // Try without slash (e.g., "C2634" matches "C26/34")
        String billNoSlash = expectedBillNo.replaceAll("[/\\\\-]", "").toLowerCase();
        if (normalizedOcr.contains(billNoSlash)) {
            return true;
        }

        return false;
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("\\s+", "")
                .replace("l", "1")
                .replace("i", "1")
                .replace("o", "0")
                .replaceAll("[/\\\\-]", "");
    }
}
