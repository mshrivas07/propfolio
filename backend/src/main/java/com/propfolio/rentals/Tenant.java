package com.propfolio.rentals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * A person who receives invoices. Can have several email addresses.
 */
@Entity
@Table(name = "tenant")
public class Tenant extends LandlordOwnedEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "emails", nullable = false)
    private List<String> emails = new ArrayList<>();

    @Column(name = "phone")
    private String phone;

    @Column(name = "notes")
    private String notes;

    protected Tenant() {
        // for JPA
    }

    public Tenant(UUID landlordId, String fullName, List<String> emails) {
        super(landlordId);
        this.fullName = fullName;
        this.emails = new ArrayList<>(emails);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
