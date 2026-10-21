package com.propfolio.rentals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.propfolio.rentals.web.LeaseDtos.LeaseCreateRequest;
import com.propfolio.rentals.web.PropertyDtos.PropertyRequest;
import com.propfolio.rentals.web.PropertyDtos.PropertyResponse;
import com.propfolio.rentals.web.SplitRuleDtos.SplitRuleRequest;
import com.propfolio.rentals.web.TenantDtos.TenantRequest;
import com.propfolio.rentals.web.TenantDtos.TenantResponse;
import com.propfolio.rentals.web.UnitDtos.UnitRequest;
import com.propfolio.rentals.web.UnitDtos.UnitResponse;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountRequest;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountResponse;

/**
 * Builds a made-up two-unit house through the real services:
 * Upper unit pays 70% of hydro and a $50 flat for internet; Lower unit pays 30% of hydro.
 */
final class RentalTestData {

    final PropertyResponse property;
    final UnitResponse upper;
    final UnitResponse lower;
    final UtilityAccountResponse hydro;
    final UtilityAccountResponse internet;
    final TenantResponse tenant;

    RentalTestData(PropertyService properties, UnitService units, UtilityAccountService accounts,
            TenantService tenants) {
        property = properties.create(new PropertyRequest("12 Sample St " + UUID.randomUUID().toString().substring(0, 6),
                "12 Sample St", null, "Testville", "ON", "A1A 1A1"));
        upper = units.create(property.id(), new UnitRequest("Upper", null));
        lower = units.create(property.id(), new UnitRequest("Lower", null));
        hydro = accounts.create(property.id(), new UtilityAccountRequest(UtilityType.ELECTRICITY, "Sample Hydro", "H-1"));
        internet = accounts.create(property.id(), new UtilityAccountRequest(UtilityType.INTERNET, "Sample Net", null));
        tenant = tenants.create(new TenantRequest("Sam Tenant", List.of("sam@example.com"), null, null));
    }

    static SplitRuleRequest percent(UnitResponse unit, UtilityAccountResponse account, String value) {
        return new SplitRuleRequest(unit.id(), account.id(), ShareType.PERCENT, new BigDecimal(value));
    }

    static SplitRuleRequest flat(UnitResponse unit, UtilityAccountResponse account, String value) {
        return new SplitRuleRequest(unit.id(), account.id(), ShareType.FLAT, new BigDecimal(value));
    }

    static LeaseCreateRequest lease(TenantResponse tenant, String start, String end) {
        return new LeaseCreateRequest(tenant.id(), LocalDate.parse(start), end == null ? null : LocalDate.parse(end));
    }
}
