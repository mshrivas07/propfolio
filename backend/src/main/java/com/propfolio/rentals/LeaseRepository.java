package com.propfolio.rentals;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseRepository extends JpaRepository<Lease, UUID> {
}
