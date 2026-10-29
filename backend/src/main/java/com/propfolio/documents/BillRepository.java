package com.propfolio.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    Optional<Bill> findByIdAndLandlordId(UUID id, UUID landlordId);

    /** Duplicate check: the same file (by SHA-256) uploaded before by this landlord. */
    Optional<Bill> findFirstByLandlordIdAndFileSha256(UUID landlordId, String fileSha256);

    List<Bill> findAllByLandlordIdOrderByCreatedAtDesc(UUID landlordId);

    List<Bill> findAllByLandlordIdAndStatusOrderByCreatedAtDesc(UUID landlordId, BillStatus status);

    List<Bill> findAllByLandlordIdAndUtilityAccountIdInOrderByCreatedAtDesc(UUID landlordId,
            List<UUID> utilityAccountIds);

    List<Bill> findAllByLandlordIdAndStatusAndUtilityAccountIdInOrderByCreatedAtDesc(UUID landlordId,
            BillStatus status, List<UUID> utilityAccountIds);

    /** Used by rentals to block deleting a utility account that already has bills. */
    boolean existsByUtilityAccountId(UUID utilityAccountId);
}
