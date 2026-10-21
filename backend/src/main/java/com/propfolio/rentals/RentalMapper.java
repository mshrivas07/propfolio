package com.propfolio.rentals;

import java.util.List;

import com.propfolio.rentals.web.LeaseDtos.LeaseResponse;
import com.propfolio.rentals.web.PropertyDtos.PropertyResponse;
import com.propfolio.rentals.web.SplitRuleDtos.SplitRuleResponse;
import com.propfolio.rentals.web.UnitDtos.UnitResponse;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountResponse;

/**
 * Entity → response conversions. Entities never leave the service layer.
 */
final class RentalMapper {

    private RentalMapper() {
    }

    static PropertyResponse toResponse(Property p, long unitCount, long utilityAccountCount) {
        return new PropertyResponse(p.getId(), p.getName(), p.getAddressLine1(), p.getAddressLine2(),
                p.getCity(), p.getProvince(), p.getPostalCode(), unitCount, utilityAccountCount);
    }

    static UnitResponse toResponse(Unit u, List<LeaseResponse> leases) {
        return new UnitResponse(u.getId(), u.getPropertyId(), u.getName(), u.getNotes(), leases);
    }

    static UtilityAccountResponse toResponse(UtilityAccount a) {
        return new UtilityAccountResponse(a.getId(), a.getPropertyId(), a.getUtilityType(),
                a.getProviderName(), a.getAccountNumber());
    }

    static SplitRuleResponse toResponse(SplitRule r) {
        return new SplitRuleResponse(r.getId(), r.getUnitId(), r.getUtilityAccountId(), r.getShareType(),
                r.getShareValue());
    }

    /** Trims text and turns blank optional fields into null. */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
