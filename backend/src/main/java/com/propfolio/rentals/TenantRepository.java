package com.propfolio.rentals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Tenant> findAllByLandlordIdOrderByFullNameAsc(UUID landlordId);
}
