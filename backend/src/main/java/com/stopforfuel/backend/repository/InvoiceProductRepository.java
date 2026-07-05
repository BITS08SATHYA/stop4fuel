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

    @Query("""
        select cast(ib.date as LocalDate), coalesce(sum(ip.quantity), 0), coalesce(sum(ip.amount), 0)
        from InvoiceProduct ip join ip.invoiceBill ib
        where ip.product.id = :productId
          and ib.scid = :scid
          and ib.date >= :fromDate
          and ib.date <= :toDate
        group by cast(ib.date as LocalDate)
        order by cast(ib.date as LocalDate)
        """)
    List<Object[]> getDailySalesByProduct(@Param("productId") Long productId,
                                          @Param("scid") Long scid,
                                          @Param("fromDate") LocalDateTime fromDate,
                                          @Param("toDate") LocalDateTime toDate);

    @Query("""
        select ip.product.id, coalesce(sum(ip.quantity), 0), coalesce(sum(ip.amount), 0)
        from InvoiceProduct ip join ip.invoiceBill ib
        where ib.scid = :scid
          and ib.date >= :fromDate
          and ib.date <= :toDate
        group by ip.product.id
        """)
    List<Object[]> getSalesTotalsByProduct(@Param("scid") Long scid,
                                           @Param("fromDate") LocalDateTime fromDate,
                                           @Param("toDate") LocalDateTime toDate);

    @Query("""
        select ip.product.id, max(ib.date)
        from InvoiceProduct ip join ip.invoiceBill ib
        where ib.scid = :scid
        group by ip.product.id
        """)
    List<Object[]> getLastSaleDateByProduct(@Param("scid") Long scid);
}
