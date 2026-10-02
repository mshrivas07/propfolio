package com.propfolio.security;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LandlordRepository extends JpaRepository<Landlord, UUID> {
}
