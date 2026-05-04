package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ApplicationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, String> {
}
