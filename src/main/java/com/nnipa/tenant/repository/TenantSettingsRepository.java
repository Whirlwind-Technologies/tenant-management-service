package com.nnipa.tenant.repository;

import com.nnipa.tenant.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {
    Optional<TenantSettings> findByTenantId(UUID tenantId);
}
