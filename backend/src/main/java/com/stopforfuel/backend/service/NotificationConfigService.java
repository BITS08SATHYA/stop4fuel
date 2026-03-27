package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.NotificationConfig;
import com.stopforfuel.backend.repository.NotificationConfigRepository;
import com.stopforfuel.config.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationConfigService {

    @Autowired
    private NotificationConfigRepository repository;

    public List<NotificationConfig> getAll() {
        return repository.findAllByScid(SecurityUtils.getScid());
    }

    public Optional<NotificationConfig> getByAlertType(String alertType) {
        return repository.findByAlertTypeAndScid(alertType, SecurityUtils.getScid());
    }

    public NotificationConfig save(NotificationConfig config) {
        Long scid = SecurityUtils.getScid();

        // Check if config already exists for this alert type
        Optional<NotificationConfig> existing = repository.findByAlertTypeAndScid(config.getAlertType(), scid);
        if (existing.isPresent()) {
            NotificationConfig existingConfig = existing.get();
            existingConfig.setEnabled(config.isEnabled());
            existingConfig.setNotifyRoles(config.getNotifyRoles());
            existingConfig.setChannels(config.getChannels());
            return repository.save(existingConfig);
        }

        if (config.getScid() == null) {
            config.setScid(scid);
        }
        return repository.save(config);
    }

    public void delete(Long id) {
        NotificationConfig config = repository.findByIdAndScid(id, SecurityUtils.getScid())
                .orElseThrow(() -> new RuntimeException("Notification config not found"));
        repository.delete(config);
    }
}
