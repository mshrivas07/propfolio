package com.propfolio.documents;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<Bill, UUID> {

    /** Used by rentals to block deleting a utility account that already has bills. */
    boolean existsByUtilityAccountId(UUID utilityAccountId);
}
