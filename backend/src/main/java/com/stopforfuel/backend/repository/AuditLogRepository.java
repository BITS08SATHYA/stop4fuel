package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByScidOrderByPerformedAtDesc(Long scid, Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE a.scid = :scid " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:userId IS NULL OR a.performedById = :userId OR a.entityId = :userId) " +
           "ORDER BY a.performedAt DESC")
    Page<AuditLog> findFiltered(@Param("scid") Long scid,
                                @Param("action") String action,
                                @Param("userId") Long userId,
                                Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByPerformedAtDesc(String entityType, Long entityId);

    long countByActionAndScidAndPerformedAtAfter(String action, Long scid, LocalDateTime after);

    @Query("SELECT a FROM AuditLog a WHERE a.scid = :scid AND a.action IN ('LOGIN_SUCCESS', 'LOGIN_FAILED') " +
           "ORDER BY a.performedAt DESC")
    List<AuditLog> findRecentLogins(@Param("scid") Long scid, Pageable pageable);
}
