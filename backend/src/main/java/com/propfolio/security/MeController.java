package com.propfolio.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the signed-in landlord's profile.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final CurrentLandlord currentLandlord;
    private final LandlordRepository landlordRepository;

    public MeController(CurrentLandlord currentLandlord, LandlordRepository landlordRepository) {
        this.currentLandlord = currentLandlord;
        this.landlordRepository = landlordRepository;
    }

    @GetMapping
    public MeResponse me() {
        UUID landlordId = currentLandlord.id();
        Landlord landlord = landlordRepository.findById(landlordId)
                .orElseThrow(() -> new IllegalStateException("Landlord row missing for " + landlordId));
        return new MeResponse(landlord.getId(), landlord.getEmail(), landlord.getDisplayName(), landlord.getCreatedAt());
    }

    public record MeResponse(UUID id, String email, String displayName, Instant createdAt) {
    }
}
