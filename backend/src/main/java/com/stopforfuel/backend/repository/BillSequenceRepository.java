package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.BillSequence;
import com.stopforfuel.backend.enums.BillType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillSequenceRepository extends JpaRepository<BillSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillSequence> findByTypeAndFyYear(BillType type, Integer fyYear);

    /** Lockless read used by admin peek operations — no need to block concurrent auto-gen. */
    @Query("SELECT b FROM BillSequence b WHERE b.type = :type AND b.fyYear = :fyYear")
    Optional<BillSequence> peekByTypeAndFyYear(@Param("type") BillType type, @Param("fyYear") Integer fyYear);
}
