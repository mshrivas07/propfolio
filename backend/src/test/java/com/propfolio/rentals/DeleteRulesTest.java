package com.propfolio.rentals;

import static com.propfolio.rentals.RentalTestData.lease;
import static com.propfolio.rentals.RentalTestData.percent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.propfolio.billing.Invoice;
import com.propfolio.billing.InvoiceRepository;
import com.propfolio.common.error.ConflictException;
import com.propfolio.documents.Bill;
import com.propfolio.documents.BillRepository;
import com.propfolio.rentals.web.PropertyDtos.PropertyRequest;

/**
 * Deleting is blocked with a 409 whenever other records still depend on the row.
 */
class DeleteRulesTest extends RentalServiceTestBase {

    @Autowired private InvoiceRepository invoices;
    @Autowired private BillRepository bills;

    @Test
    void propertyWithUnitsOrAccountsCannotBeDeleted() {
        assertThatThrownBy(() -> propertyService.delete(data.property.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("units or utility accounts");
    }

    @Test
    void emptyPropertyCanBeDeleted() {
        var empty = propertyService.create(new PropertyRequest("Empty lot", null, null, null, null, null));
        propertyService.delete(empty.id());
        assertThat(propertyService.list()).extracting("id").doesNotContain(empty.id());
    }

    @Test
    void unitWithALeaseCannotBeDeleted() {
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));
        assertThatThrownBy(() -> unitService.delete(data.upper.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void unitWithInvoicesCannotBeDeleted() {
        var lease = leaseService.create(data.lower.id(), lease(data.tenant, "2026-01-01", null));
        invoices.save(new Invoice(landlordId, "INV-2026-0001", lease.id(), data.lower.id(),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")));

        assertThatThrownBy(() -> unitService.delete(data.lower.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("invoices");
    }

    @Test
    void tenantWithALeaseCannotBeDeleted() {
        leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));
        assertThatThrownBy(() -> tenantService.delete(data.tenant.id())).isInstanceOf(ConflictException.class);
    }

    @Test
    void leaseWithInvoicesCannotBeDeleted() {
        var lease = leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));
        invoices.save(new Invoice(landlordId, "INV-2026-0002", lease.id(), data.upper.id(),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")));

        assertThatThrownBy(() -> leaseService.delete(lease.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("end date");
    }

    @Test
    void leaseWithoutInvoicesCanBeDeleted() {
        var lease = leaseService.create(data.upper.id(), lease(data.tenant, "2026-01-01", null));
        leaseService.delete(lease.id());
        assertThat(leaseService.listForUnit(data.upper.id())).isEmpty();
    }

    @Test
    void utilityAccountWithBillsCannotBeDeleted() {
        Bill bill = new Bill(landlordId, "hydro.pdf", "x/hydro.pdf", "application/pdf", 10L, "sha-delete-test");
        bill.setUtilityAccountId(data.hydro.id());
        bills.save(bill);

        assertThatThrownBy(() -> utilityAccountService.delete(data.hydro.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("bills");
    }

    @Test
    void utilityAccountWithoutBillsIsDeletedWithItsRules() {
        splitRuleService.save(percent(data.upper, data.internet, "50"));
        utilityAccountService.delete(data.internet.id());

        var detail = propertyService.detail(data.property.id());
        assertThat(detail.utilityAccounts()).extracting("id").doesNotContain(data.internet.id());
        assertThat(detail.splitRules()).isEmpty();
    }
}
