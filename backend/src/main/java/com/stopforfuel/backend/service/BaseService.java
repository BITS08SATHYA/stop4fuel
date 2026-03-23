package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.BaseEntity;
import com.stopforfuel.backend.repository.ScidRepository;
import com.stopforfuel.config.SecurityUtils;

import java.util.List;

/**
 * Base service providing tenant-safe CRUD operations.
 * All queries are automatically filtered by the current user's scid (Site Company ID).
 */
public abstract class BaseService<T extends BaseEntity, R extends ScidRepository<T>> {

    protected final R repository;

    protected BaseService(R repository) {
        this.repository = repository;
    }

    protected Long getScid() {
        return SecurityUtils.getScid();
    }

    public List<T> findAll() {
        return repository.findAllByScid(getScid());
    }

    public T findById(Long id) {
        return repository.findByIdAndScid(id, getScid())
                .orElseThrow(() -> new RuntimeException(getEntityName() + " not found with id: " + id));
    }

    public T save(T entity) {
        if (entity.getScid() == null) {
            entity.setScid(getScid());
        }
        return repository.save(entity);
    }

    public void deleteById(Long id) {
        T entity = findById(id);
        repository.delete(entity);
    }

    protected String getEntityName() {
        return "Entity";
    }
}
