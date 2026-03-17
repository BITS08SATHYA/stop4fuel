package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.BillSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillSequenceRepository extends JpaRepository<BillSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillSequence> findByTypeAndFyYear(String type, Integer fyYear);
}
