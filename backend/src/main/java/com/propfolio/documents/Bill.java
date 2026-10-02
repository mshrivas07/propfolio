package com.propfolio.documents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * An uploaded utility bill and the values extracted from it.
 */
@Entity
@Table(name = "bill")
public class Bill extends LandlordOwnedEntity {

    @Column(name = "utility_account_id")
    private UUID utilityAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillStatus status = BillStatus.UPLOADED;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "file_sha256", nullable = false)
    private String fileSha256;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "amount_due", precision = 12, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_json")
    private String extractionJson;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected Bill() {
        // for JPA
    }

    public Bill(UUID landlordId, String originalFilename, String storagePath, String contentType, long fileSizeBytes, String fileSha256) {
        super(landlordId);
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.fileSha256 = fileSha256;
    }

    public UUID getUtilityAccountId() {
        return utilityAccountId;
    }

    public void setUtilityAccountId(UUID utilityAccountId) {
        this.utilityAccountId = utilityAccountId;
    }

    public BillStatus getStatus() {
        return status;
    }

    public void setStatus(BillStatus status) {
        this.status = status;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
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

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getAmountDue() {
        return amountDue;
    }

    public void setAmountDue(BigDecimal amountDue) {
        this.amountDue = amountDue;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getExtractionJson() {
        return extractionJson;
    }

    public void setExtractionJson(String extractionJson) {
        this.extractionJson = extractionJson;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
