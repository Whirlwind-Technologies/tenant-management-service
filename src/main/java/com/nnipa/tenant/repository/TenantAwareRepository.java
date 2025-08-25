package com.nnipa.tenant.repository;

import com.nnipa.tenant.config.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository interface for tenant-aware entities.
 * Automatically filters queries based on current tenant context.
 */
@NoRepositoryBean
public interface TenantAwareRepository<T, ID extends Serializable>
        extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /**
     * Find entity by ID within current tenant context.
     */
    default Optional<T> findByIdInTenant(ID id) {
        List<T> results = findAll(withTenant().and(hasId(id)));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Find all entities within current tenant context.
     */
    default List<T> findAllInTenant() {
        return findAll(withTenant());
    }

    /**
     * Count entities within current tenant context.
     */
    default long countInTenant() {
        return count(withTenant());
    }

    /**
     * Check if entity exists within current tenant context.
     */
    default boolean existsByIdInTenant(ID id) {
        return count(withTenant().and(hasId(id))) > 0;
    }

    /**
     * Delete entity by ID within current tenant context.
     */
    default void deleteByIdInTenant(ID id) {
        findByIdInTenant(id).ifPresent(this::delete);
    }

    /**
     * Specification to filter by current tenant.
     */
    default Specification<T> withTenant() {
        return (root, query, criteriaBuilder) -> {
            String tenantId = TenantContext.getCurrentTenant();
            if (tenantId == null) {
                // No tenant context - return no results for safety
                return criteriaBuilder.disjunction();
            }

            // Check if entity has tenant_id field
            try {
                Path<String> tenantPath = root.get("tenantId");
                return criteriaBuilder.equal(tenantPath, UUID.fromString(tenantId));
            } catch (IllegalArgumentException e) {
                // Entity doesn't have tenant_id field
                return criteriaBuilder.conjunction();
            }
        };
    }

    /**
     * Specification to filter by ID.
     */
    default Specification<T> hasId(ID id) {
        return (root, query, criteriaBuilder) -> {
            try {
                Path<Object> idPath = root.get("id");
                return criteriaBuilder.equal(idPath, id);
            } catch (IllegalArgumentException e) {
                return criteriaBuilder.disjunction();
            }
        };
    }

    /**
     * Specification to filter by field value.
     */
    default Specification<T> hasFieldValue(String fieldName, Object value) {
        return (root, query, criteriaBuilder) -> {
            try {
                Path<Object> fieldPath = root.get(fieldName);
                return criteriaBuilder.equal(fieldPath, value);
            } catch (IllegalArgumentException e) {
                return criteriaBuilder.disjunction();
            }
        };
    }

    /**
     * Combine tenant filter with custom specification.
     */
    default Specification<T> withTenantAnd(Specification<T> spec) {
        return withTenant().and(spec);
    }
}