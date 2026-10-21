package com.propfolio.rentals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaseRepository extends JpaRepository<Lease, UUID> {

    Optional<Lease> findByIdAndLandlordId(UUID id, UUID landlordId);

    List<Lease> findAllByLandlordIdAndUnitIdOrderByStartDateDesc(UUID landlordId, UUID unitId);

    List<Lease> findAllByLandlordIdAndTenantIdOrderByStartDateDesc(UUID landlordId, UUID tenantId);

    List<Lease> findAllByLandlordIdAndUnitIdIn(UUID landlordId, List<UUID> unitIds);

    boolean existsByUnitId(UUID unitId);

    boolean existsByTenantId(UUID tenantId);

    /**
     * Leases on the unit whose inclusive date range overlaps [start, end].
     * Pass a far-future {@code end} for an ongoing lease; a stored null end date means ongoing.
     * {@code excludeId} skips the lease being edited (pass a random UUID when creating).
     */
    @Query("""
            select l from Lease l
            where l.unitId = :unitId
              and l.id <> :excludeId
              and l.startDate <= :end
              and (l.endDate is null or l.endDate >= :start)
            """)
    List<Lease> findOverlapping(@Param("unitId") UUID unitId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("excludeId") UUID excludeId);
}
