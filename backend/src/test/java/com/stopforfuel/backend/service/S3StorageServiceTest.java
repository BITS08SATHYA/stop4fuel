package com.stopforfuel.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;

    @InjectMocks
    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3StorageService, "bucket", "test-bucket");
    }

    @Test
    void upload_withMultipartFile_callsS3PutObject() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String result = s3StorageService.upload("invoices/1/bill-pic.jpg", file);

        assertEquals("invoices/1/bill-pic.jpg", result);
        verify(s3Client).putObject(argThat((PutObjectRequest req) ->
                "test-bucket".equals(req.bucket()) && "invoices/1/bill-pic.jpg".equals(req.key())
        ), any(RequestBody.class));
    }

    @Test
    void upload_withByteArray_callsS3PutObject() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String result = s3StorageService.upload("statements/1/statement.pdf", new byte[]{1, 2, 3}, "application/pdf");

        assertEquals("statements/1/statement.pdf", result);
        verify(s3Client).putObject(argThat((PutObjectRequest req) ->
                "test-bucket".equals(req.bucket()) && "statements/1/statement.pdf".equals(req.key())
        ), any(RequestBody.class));
    }

    @Test
    void getPresignedUrl_returnsUrl() throws Exception {
        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URI("https://s3.example.com/test").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        String result = s3StorageService.getPresignedUrl("invoices/1/bill-pic.jpg");

        assertNotNull(result);
        assertTrue(result.contains("s3.example.com"));
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void delete_callsS3DeleteObject() {
        s3StorageService.delete("invoices/1/bill-pic.jpg");

        verify(s3Client).deleteObject(argThat((DeleteObjectRequest req) ->
                "test-bucket".equals(req.bucket()) && "invoices/1/bill-pic.jpg".equals(req.key())
        ));
    }
}
