package com.propfolio.rentals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilityAccountRepository extends JpaRepository<UtilityAccount, UUID> {

    Optional<UtilityAccount> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<UtilityAccount> findAllByLandlordIdAndPropertyIdOrderByUtilityTypeAscProviderNameAsc(
            UUID landlordId, UUID propertyId);

    List<UtilityAccount> findAllByLandlordId(UUID landlordId);

    boolean existsByPropertyId(UUID propertyId);

    long countByPropertyId(UUID propertyId);
}
