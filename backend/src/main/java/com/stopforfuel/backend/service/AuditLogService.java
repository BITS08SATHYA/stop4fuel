package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.AuditLog;
import com.stopforfuel.backend.repository.AuditLogRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    @Transactional
    public void log(String action, String entityType, Long entityId, String description, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDescription(description);
        log.setIpAddress(ipAddress);
        log.setPerformedById(SecurityUtils.getCurrentUserId());
        log.setScid(SecurityUtils.getScid());
        log.setPerformedAt(LocalDateTime.now());
        repository.save(log);
    }

    @Transactional
    public void logLogin(String action, Long userId, String userName, String ipAddress, String description) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setEntityType("USER");
        log.setEntityId(userId);
        log.setPerformedById(userId);
        log.setPerformedByName(userName);
        log.setIpAddress(ipAddress);
        log.setDescription(description);
        log.setScid(SecurityUtils.getScid());
        log.setPerformedAt(LocalDateTime.now());
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(String action, Long userId, int page, int size) {
        return repository.findFiltered(SecurityUtils.getScid(), action, userId, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogins(int limit) {
        return repository.findRecentLogins(SecurityUtils.getScid(), PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getActivityForUser(Long userId) {
        return repository.findByEntityTypeAndEntityIdOrderByPerformedAtDesc("USER", userId);
    }
}
