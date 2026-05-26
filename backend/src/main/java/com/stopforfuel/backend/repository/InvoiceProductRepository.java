package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.InvoiceProduct;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvoiceProductRepository extends JpaRepository<InvoiceProduct, Long> {

    @Query("""
        select ip.product.id
        from InvoiceProduct ip
        where ip.scid = :scid
          and ip.product.active = true
          and ip.invoiceBill.date >= :since
        group by ip.product.id
        order by count(ip) desc
        """)
    List<Long> findTopSellingProductIds(@Param("scid") Long scid,
                                        @Param("since") LocalDateTime since,
                                        Pageable pageable);
}
