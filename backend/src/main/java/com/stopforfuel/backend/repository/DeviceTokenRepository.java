package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    List<DeviceToken> findByUserId(Long userId);

    void deleteByFcmToken(String fcmToken);

    /**
     * Device tokens for all users whose role has the given permission code.
     */
    @Query("""
        SELECT dt FROM DeviceToken dt
        WHERE dt.userId IN (
            SELECT u.id FROM User u
            WHERE u.role.id IN (
                SELECT rp.role.id FROM RolePermission rp
                WHERE rp.permission.code = :permissionCode
            )
        )
    """)
    List<DeviceToken> findByPermissionCode(@Param("permissionCode") String permissionCode);
}
