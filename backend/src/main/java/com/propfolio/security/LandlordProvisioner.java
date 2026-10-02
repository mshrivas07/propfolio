package com.propfolio.security;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the landlord profile row the first time a new user calls the API.
 * Runs in its own transaction so a duplicate insert from a parallel request
 * cannot spoil the caller's transaction.
 */
@Service
public class LandlordProvisioner {

    private final LandlordRepository landlordRepository;

    public LandlordProvisioner(LandlordRepository landlordRepository) {
        this.landlordRepository = landlordRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(UUID landlordId, String email) {
        if (!landlordRepository.existsById(landlordId)) {
            landlordRepository.saveAndFlush(new Landlord(landlordId, email));
        }
    }
}
