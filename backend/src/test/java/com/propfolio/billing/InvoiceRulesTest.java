package com.propfolio.billing;

import static com.propfolio.billing.InvoiceAction.ARCHIVE;
import static com.propfolio.billing.InvoiceAction.CANCEL;
import static com.propfolio.billing.InvoiceAction.CREATE_EMAIL_DRAFT;
import static com.propfolio.billing.InvoiceAction.CREATE_INVOICE;
import static com.propfolio.billing.InvoiceAction.DELETE;
import static com.propfolio.billing.InvoiceAction.DELETE_PAYMENT;
import static com.propfolio.billing.InvoiceAction.EDIT_DUE_DATE;
import static com.propfolio.billing.InvoiceAction.MARK_SENT;
import static com.propfolio.billing.InvoiceAction.RECORD_PAYMENT;
import static com.propfolio.billing.InvoiceAction.UNARCHIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.propfolio.common.error.ConflictException;

/** The full table of allowed moves, one status at a time. */
class InvoiceRulesTest {

    @Test
    void allowedMovesPerStatus() {
        assertThat(InvoiceRules.available(InvoiceStatus.OPEN, 2)).containsExactly(CREATE_INVOICE, EDIT_DUE_DATE);
        assertThat(InvoiceRules.available(InvoiceStatus.OPEN, 0)).containsExactly(EDIT_DUE_DATE);
        assertThat(InvoiceRules.available(InvoiceStatus.INVOICE_CREATED, 2))
                .containsExactly(DELETE, CREATE_EMAIL_DRAFT, EDIT_DUE_DATE);
        assertThat(InvoiceRules.available(InvoiceStatus.EMAIL_DRAFTED, 2))
                .containsExactly(DELETE, CREATE_EMAIL_DRAFT, MARK_SENT);
        assertThat(InvoiceRules.available(InvoiceStatus.SENT, 2)).containsExactly(RECORD_PAYMENT, CANCEL);
        assertThat(InvoiceRules.available(InvoiceStatus.PARTIALLY_PAID, 2)).containsExactly(RECORD_PAYMENT, DELETE_PAYMENT);
        assertThat(InvoiceRules.available(InvoiceStatus.PAID, 2)).containsExactly(DELETE_PAYMENT, ARCHIVE);
        assertThat(InvoiceRules.available(InvoiceStatus.ARCHIVED, 2)).containsExactly(UNARCHIVE);
        assertThat(InvoiceRules.available(InvoiceStatus.CANCELLED, 2)).isEmpty();
    }

    @Test
    void refusedMoveExplainsItself() {
        assertThatThrownBy(() -> InvoiceRules.require(MARK_SENT, InvoiceStatus.INVOICE_CREATED, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email draft");
    }

    @Test
    void statusFollowsTheAmountReceived() {
        BigDecimal total = new BigDecimal("60.00");
        assertThat(InvoiceRules.statusForReceived(BigDecimal.ZERO, total)).isEqualTo(InvoiceStatus.SENT);
        assertThat(InvoiceRules.statusForReceived(new BigDecimal("35.00"), total)).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(InvoiceRules.statusForReceived(new BigDecimal("60.00"), total)).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void owedFromCreatedOnButNotOpenOrCancelled() {
        assertThat(InvoiceRules.countsAsOwed(InvoiceStatus.OPEN)).isFalse();
        assertThat(InvoiceRules.countsAsOwed(InvoiceStatus.CANCELLED)).isFalse();
        assertThat(InvoiceRules.countsAsOwed(InvoiceStatus.INVOICE_CREATED)).isTrue();
        assertThat(InvoiceRules.countsAsOwed(InvoiceStatus.ARCHIVED)).isTrue();
    }
}
