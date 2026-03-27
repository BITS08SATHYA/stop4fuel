package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.NotificationConfig;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationConfigRepository extends ScidRepository<NotificationConfig> {
    Optional<NotificationConfig> findByAlertTypeAndScid(String alertType, Long scid);
    List<NotificationConfig> findByEnabledAndScid(boolean enabled, Long scid);
}
