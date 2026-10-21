package com.propfolio.rentals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

    Optional<Unit> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Unit> findAllByLandlordIdAndPropertyIdOrderByNameAsc(UUID landlordId, UUID propertyId);

    List<Unit> findAllByLandlordId(UUID landlordId);

    boolean existsByPropertyId(UUID propertyId);

    boolean existsByPropertyIdAndNameIgnoreCase(UUID propertyId, String name);

    boolean existsByPropertyIdAndNameIgnoreCaseAndIdNot(UUID propertyId, String name, UUID id);

    long countByPropertyId(UUID propertyId);
}
