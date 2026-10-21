package com.propfolio.rentals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitRuleRepository extends JpaRepository<SplitRule, UUID> {

    Optional<SplitRule> findByIdAndLandlordId(UUID id, UUID landlordId);

    Optional<SplitRule> findByLandlordIdAndUnitIdAndUtilityAccountId(UUID landlordId, UUID unitId,
            UUID utilityAccountId);

    List<SplitRule> findAllByUtilityAccountId(UUID utilityAccountId);

    List<SplitRule> findAllByLandlordIdAndUtilityAccountIdIn(UUID landlordId, List<UUID> utilityAccountIds);

    void deleteAllByUnitId(UUID unitId);

    void deleteAllByUtilityAccountId(UUID utilityAccountId);
}
