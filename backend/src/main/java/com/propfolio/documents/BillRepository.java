package com.propfolio.documents;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Other bills on the same utility account whose billing period overlaps [start, end]
     * (inclusive). Used to warn about, and block verifying, two bills for the same days.
     */
    @Query("""
            select b from Bill b
            where b.utilityAccountId = :utilityAccountId
              and b.id <> :excludeId
              and b.periodStart is not null and b.periodEnd is not null
              and b.periodStart <= :end and b.periodEnd >= :start
            order by b.periodStart
            """)
    List<Bill> findOverlappingPeriod(@Param("utilityAccountId") UUID utilityAccountId,
            @Param("start") LocalDate start, @Param("end") LocalDate end, @Param("excludeId") UUID excludeId);

    /**
     * Verified bills on these utility accounts whose period overlaps [start, end] (inclusive):
     * the bills a lease over those dates should be charged for.
     */
    @Query("""
            select b from Bill b
            where b.landlordId = :landlordId
              and b.utilityAccountId in :utilityAccountIds
              and b.status = com.propfolio.documents.BillStatus.VERIFIED
              and b.periodStart <= :end and b.periodEnd >= :start
            order by b.periodStart
            """)
    List<Bill> findVerifiedOverlapping(@Param("landlordId") UUID landlordId,
            @Param("utilityAccountIds") List<UUID> utilityAccountIds,
            @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** Used by rentals to block deleting a utility account that already has bills. */
    boolean existsByUtilityAccountId(UUID utilityAccountId);
}
