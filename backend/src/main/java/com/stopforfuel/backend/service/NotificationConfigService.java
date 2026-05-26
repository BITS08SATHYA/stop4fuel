package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.NotificationConfig;
import com.stopforfuel.backend.repository.NotificationConfigRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationConfigService {

    public static final String SHIFT_CLOSE_STOCK = "SHIFT_CLOSE_STOCK";

    private final NotificationConfigRepository repository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<NotificationConfig> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    @Transactional(readOnly = true)
    public Optional<NotificationConfig> getByAlertType(String alertType) {
        return repository.findByAlertTypeAndScid(alertType, SecurityUtils.getScid());
    }

    public NotificationConfig save(NotificationConfig config) {
        Long scid = SecurityUtils.getScid();

        if (SHIFT_CLOSE_STOCK.equals(config.getAlertType())
                && !permissionService.hasPermission(currentRole(), "STOCK_NOTIFICATION_CONFIGURE")) {
            throw new AccessDeniedException("Only owners may configure the shift-close stock notification");
        }

        // Check if config already exists for this alert type
        Optional<NotificationConfig> existing = repository.findByAlertTypeAndScid(config.getAlertType(), scid);
        if (existing.isPresent()) {
            NotificationConfig existingConfig = existing.get();
            existingConfig.setEnabled(config.isEnabled());
            existingConfig.setNotifyRoles(config.getNotifyRoles());
            existingConfig.setChannels(config.getChannels());
            existingConfig.setLowStockThreshold(config.getLowStockThreshold());
            existingConfig.setEmailRecipients(config.getEmailRecipients());
            return repository.save(existingConfig);
        }

        if (config.getScid() == null) {
            config.setScid(scid);
        }
        return repository.save(config);
    }

    private String currentRole() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "EMPLOYEE";
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            String role = jwt.getClaimAsString("custom:role");
            if (role != null) return role.toUpperCase();
        }
        if (auth.getPrincipal() instanceof java.util.Map<?, ?> map) {
            Object role = map.get("custom:role");
            if (role != null) return role.toString().toUpperCase();
        }
        for (var ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if (a.startsWith("ROLE_")) return a.substring(5);
        }
        return "EMPLOYEE";
    }

    public void delete(Long id) {
        NotificationConfig config = repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Notification config not found"));
        repository.delete(config);
    }
}
