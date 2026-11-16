package com.propfolio.billing;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.propfolio.common.persistence.LandlordOwnedEntity;

/**
 * Next invoice number per landlord per year (INV-YYYY-NNNN).
 */
@Entity
// Mirrors invoice_number_sequence_unique in the migration, so the test database enforces it too.
@Table(name = "invoice_number_sequence", uniqueConstraints = @UniqueConstraint(name = "invoice_number_sequence_unique",
        columnNames = {"landlord_id", "sequence_year"}))
public class InvoiceNumberSequence extends LandlordOwnedEntity {

    @Column(name = "sequence_year", nullable = false)
    private int sequenceYear;

    @Column(name = "next_value", nullable = false)
    private int nextValue = 1;

    protected InvoiceNumberSequence() {
        // for JPA
    }

    public InvoiceNumberSequence(UUID landlordId, int sequenceYear) {
        super(landlordId);
        this.sequenceYear = sequenceYear;
    }

    public int getSequenceYear() {
        return sequenceYear;
    }

    public void setSequenceYear(int sequenceYear) {
        this.sequenceYear = sequenceYear;
    }

    public int getNextValue() {
        return nextValue;
    }

    public void setNextValue(int nextValue) {
        this.nextValue = nextValue;
    }
}
