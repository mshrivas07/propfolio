package com.propfolio.rentals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.rentals.web.TenantDtos.TenantRequest;

class TenantServiceTest extends RentalServiceTestBase {

    @Test
    void emailsAreTrimmedLowerCasedAndDeduplicatedInOrder() {
        var tenant = tenantService.create(new TenantRequest("Pat Roommates",
                List.of(" Pat@Example.com ", "lee@example.com", "pat@example.com"), null, null));

        assertThat(tenant.emails()).containsExactly("pat@example.com", "lee@example.com");
    }

    @Test
    void onlyBlankEmailsAreRejected() {
        assertThatThrownBy(() -> TenantService.normalizeEmails(Arrays.asList(" ", null)))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("emails"));
    }

    @Test
    void updateReplacesEmails() {
        var updated = tenantService.update(data.tenant.id(),
                new TenantRequest("Sam Tenant", List.of("new@example.com"), "555-0100", null));

        assertThat(updated.emails()).containsExactly("new@example.com");
        assertThat(updated.phone()).isEqualTo("555-0100");
    }
}
