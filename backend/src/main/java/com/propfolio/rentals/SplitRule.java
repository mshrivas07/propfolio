package com.propfolio.rentals;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * How much of one utility account a unit pays: a percentage or a flat amount.
 */
@Entity
@Table(name = "split_rule")
public class SplitRule extends LandlordOwnedEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "utility_account_id", nullable = false)
    private UUID utilityAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "share_type", nullable = false)
    private ShareType shareType;

    @Column(name = "share_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal shareValue;

    protected SplitRule() {
        // for JPA
    }

    public SplitRule(UUID landlordId, UUID unitId, UUID utilityAccountId, ShareType shareType, BigDecimal shareValue) {
        super(landlordId);
        this.unitId = unitId;
        this.utilityAccountId = utilityAccountId;
        this.shareType = shareType;
        this.shareValue = shareValue;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public UUID getUtilityAccountId() {
        return utilityAccountId;
    }

    public void setUtilityAccountId(UUID utilityAccountId) {
        this.utilityAccountId = utilityAccountId;
    }

    public ShareType getShareType() {
        return shareType;
    }

    public void setShareType(ShareType shareType) {
        this.shareType = shareType;
    }

    public BigDecimal getShareValue() {
        return shareValue;
    }

    public void setShareValue(BigDecimal shareValue) {
        this.shareValue = shareValue;
    }
}
