package com.propfolio.security;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The landlord making the current request. Every service that reads or writes
 * landlord data gets the id from here and filters by it.
 *
 * <p>The first call for a new user creates their landlord row. Known ids are cached
 * in memory so later requests skip the database check.
 */
@Component
public class CurrentLandlord {

    private final LandlordProvisioner provisioner;
    private final Set<UUID> provisioned = ConcurrentHashMap.newKeySet();

    public CurrentLandlord(LandlordProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    /** Landlord id (the Supabase user id from the JWT "sub" claim). */
    public UUID id() {
        Jwt jwt = currentJwt();
        UUID landlordId = UUID.fromString(jwt.getSubject());
        if (!provisioned.contains(landlordId)) {
            try {
                provisioner.ensureExists(landlordId, jwt.getClaimAsString("email"));
            } catch (DataIntegrityViolationException alreadyCreatedByParallelRequest) {
                // Another request inserted the same row first; nothing to do.
            }
            provisioned.add(landlordId);
        }
        return landlordId;
    }

    private static Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AuthenticationCredentialsNotFoundException("No signed-in user for this request");
    }
}
