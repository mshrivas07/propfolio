package com.propfolio.rentals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Optional<Property> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Property> findAllByLandlordIdOrderByNameAsc(UUID landlordId);

    boolean existsByLandlordIdAndNameIgnoreCase(UUID landlordId, String name);

    boolean existsByLandlordIdAndNameIgnoreCaseAndIdNot(UUID landlordId, String name, UUID id);
}
