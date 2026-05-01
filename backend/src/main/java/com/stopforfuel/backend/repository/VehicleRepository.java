package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Vehicle;
import com.stopforfuel.backend.enums.EntityStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);
    Optional<Vehicle> findByVehicleNumber(String vehicleNumber);

    @Query("SELECT v FROM Vehicle v LEFT JOIN FETCH v.customer WHERE LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Vehicle> findByVehicleNumberContainingIgnoreCaseWithCustomer(@Param("search") String search);

    /**
     * Suggestion-style search used by /api/vehicles/search.
     * Joins customer + vehicleType so the suggestion row can render plate · type · owner
     * without N+1. Excludes INACTIVE — picking an inactive vehicle from a typeahead is a
     * dead end at submit time. Optional typeName filter (null = any type) keeps the
     * endpoint generic so future "trucks-only" reuse doesn't need a new query.
     * search="" sentinel since binding null String inside LOWER() trips Postgres type inference.
     */
    @Query("SELECT v FROM Vehicle v " +
           "LEFT JOIN FETCH v.customer c " +
           "LEFT JOIN FETCH v.vehicleType vt " +
           "WHERE v.status <> com.stopforfuel.backend.enums.EntityStatus.INACTIVE " +
           "  AND (:search = '' OR LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "  AND (:typeName IS NULL OR LOWER(vt.typeName) = LOWER(:typeName)) " +
           "ORDER BY v.vehicleNumber ASC")
    List<Vehicle> findForSuggestion(@Param("search") String search,
                                    @Param("typeName") String typeName);

    @Query("SELECT v FROM Vehicle v LEFT JOIN FETCH v.customer")
    List<Vehicle> findAllWithCustomer();

    @Query("SELECT v FROM Vehicle v LEFT JOIN FETCH v.customer WHERE v.customer.id = :customerId")
    List<Vehicle> findByCustomerIdWithCustomer(@Param("customerId") Long customerId);

    /**
     * Paginated search with all ManyToOne relationships eagerly fetched in a single query
     * (eliminates N+1 on vehicleType, preferredProduct, customer).
     * search: "" means no filter (cannot be null — Postgres can't infer type of null inside LOWER()).
     * status / customerId are directly compared so null is safe as "no filter".
     */
    @Query(value = "SELECT v FROM Vehicle v " +
                   "LEFT JOIN FETCH v.customer c " +
                   "LEFT JOIN FETCH v.vehicleType " +
                   "LEFT JOIN FETCH v.preferredProduct " +
                   "WHERE (:search = '' OR LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
                   "       OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                   "  AND (:status IS NULL OR v.status = :status) " +
                   "  AND (:customerId IS NULL OR c.id = :customerId)",
           countQuery = "SELECT COUNT(v) FROM Vehicle v " +
                        "LEFT JOIN v.customer c " +
                        "WHERE (:search = '' OR LOWER(v.vehicleNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "       OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        "  AND (:status IS NULL OR v.status = :status) " +
                        "  AND (:customerId IS NULL OR c.id = :customerId)")
    Page<Vehicle> searchPaged(@Param("search") String search,
                              @Param("status") EntityStatus status,
                              @Param("customerId") Long customerId,
                              Pageable pageable);

    List<Vehicle> findByVehicleNumberContainingIgnoreCase(String vehicleNumber);
    List<Vehicle> findByCustomerId(Long customerId);
    List<Vehicle> findByCustomerIsNull();
    List<Vehicle> findByIdIn(List<Long> ids);

    // Vehicle extends SimpleBaseEntity (no scid field) — use count() instead
}
