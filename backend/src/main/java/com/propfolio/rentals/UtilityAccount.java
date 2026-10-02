package com.propfolio.rentals;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * A utility service on a property (e.g. the water account with the city).
 */
@Entity
@Table(name = "utility_account")
public class UtilityAccount extends LandlordOwnedEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "utility_type", nullable = false)
    private UtilityType utilityType;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "account_number")
    private String accountNumber;

    protected UtilityAccount() {
        // for JPA
    }

    public UtilityAccount(UUID landlordId, UUID propertyId, UtilityType utilityType, String providerName) {
        super(landlordId);
        this.propertyId = propertyId;
        this.utilityType = utilityType;
        this.providerName = providerName;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UtilityType getUtilityType() {
        return utilityType;
    }

    public void setUtilityType(UtilityType utilityType) {
        this.utilityType = utilityType;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }
}
