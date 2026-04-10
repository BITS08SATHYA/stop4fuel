package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.InvoiceBillPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceBillPhotoRepository extends JpaRepository<InvoiceBillPhoto, Long> {
    List<InvoiceBillPhoto> findByInvoiceBillIdOrderByCreatedAtAsc(Long invoiceBillId);
    List<InvoiceBillPhoto> findByInvoiceBillIdAndPhotoTypeOrderByCreatedAtAsc(Long invoiceBillId, String photoType);
    long countByInvoiceBillIdAndPhotoType(Long invoiceBillId, String photoType);
}
