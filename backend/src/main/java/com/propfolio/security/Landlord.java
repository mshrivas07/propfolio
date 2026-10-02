package com.propfolio.security;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.propfolio.common.persistence.TimestampedEntity;

/**
 * Profile row for a signed-in user. The id is the Supabase auth user id (JWT "sub"),
 * so it is assigned by us, not generated.
 */
@Entity
@Table(name = "landlord")
public class Landlord extends TimestampedEntity {

    @Id
    private UUID id;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    protected Landlord() {
        // for JPA
    }

    public Landlord(UUID id, String email) {
        this.id = id;
        this.email = email;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
