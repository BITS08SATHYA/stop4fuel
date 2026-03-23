package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface ScidRepository<T extends BaseEntity> extends JpaRepository<T, Long> {

    List<T> findAllByScid(Long scid);

    Optional<T> findByIdAndScid(Long id, Long scid);
}
