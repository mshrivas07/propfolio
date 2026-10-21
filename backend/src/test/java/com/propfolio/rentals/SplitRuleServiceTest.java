package com.propfolio.rentals;

import static com.propfolio.rentals.RentalTestData.flat;
import static com.propfolio.rentals.RentalTestData.percent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.rentals.web.PropertyDtos.AccountPercentTotal;
import com.propfolio.rentals.web.UnitDtos.UnitRequest;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountRequest;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountResponse;
import com.propfolio.rentals.web.PropertyDtos.PropertyRequest;

class SplitRuleServiceTest extends RentalServiceTestBase {

    @Test
    void percentagesUpToExactly100AreAllowed() {
        splitRuleService.save(percent(data.upper, data.hydro, "70"));
        splitRuleService.save(percent(data.lower, data.hydro, "30"));

        AccountPercentTotal hydroTotal = propertyService.detail(data.property.id()).percentTotals().stream()
                .filter(t -> t.utilityAccountId().equals(data.hydro.id())).findFirst().orElseThrow();
        assertThat(hydroTotal.percentTotal()).isEqualByComparingTo("100");
    }

    @Test
    void percentagesOver100OnOneAccountAreRejected() {
        splitRuleService.save(percent(data.upper, data.hydro, "70"));

        assertThatThrownBy(() -> splitRuleService.save(percent(data.lower, data.hydro, "30.01")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("100.01%");
    }

    @Test
    void singlePercentageOver100IsAFieldError() {
        assertThatThrownBy(() -> splitRuleService.save(percent(data.upper, data.hydro, "101")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("shareValue"));
    }

    @Test
    void replacingAUnitsRuleDoesNotCountItsOldValue() {
        splitRuleService.save(percent(data.upper, data.hydro, "70"));
        splitRuleService.save(percent(data.lower, data.hydro, "30"));

        // Upper changes 70 → 60: total becomes 90, not 160.
        var updated = splitRuleService.save(percent(data.upper, data.hydro, "60"));

        assertThat(updated.shareValue()).isEqualByComparingTo("60");
        assertThat(propertyService.detail(data.property.id()).splitRules()).hasSize(2);
    }

    @Test
    void flatAndPercentCanShareAnAccountAndFlatIsNotCountedInTheTotal() {
        splitRuleService.save(percent(data.upper, data.internet, "100"));
        var flatRule = splitRuleService.save(flat(data.lower, data.internet, "50"));

        assertThat(flatRule.shareType()).isEqualTo(ShareType.FLAT);
        AccountPercentTotal total = propertyService.detail(data.property.id()).percentTotals().stream()
                .filter(t -> t.utilityAccountId().equals(data.internet.id())).findFirst().orElseThrow();
        assertThat(total.percentTotal()).isEqualByComparingTo("100");
    }

    @Test
    void switchingARuleFromPercentToFlatFreesUpItsPercentage() {
        splitRuleService.save(percent(data.upper, data.hydro, "70"));
        splitRuleService.save(flat(data.upper, data.hydro, "50"));

        // Lower can now take 100%.
        splitRuleService.save(percent(data.lower, data.hydro, "100"));
    }

    @Test
    void flatAmountWithMoreThanTwoDecimalsIsRejected() {
        assertThatThrownBy(() -> splitRuleService.save(flat(data.upper, data.internet, "50.125")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("shareValue"));
    }

    @Test
    void unitAndAccountMustBeOnTheSameProperty() {
        var otherProperty = propertyService.create(new PropertyRequest("Other house", null, null, null, null, null));
        UtilityAccountResponse otherWater = utilityAccountService.create(otherProperty.id(),
                new UtilityAccountRequest(UtilityType.WATER, "Other Water", null));

        assertThatThrownBy(() -> splitRuleService.save(percent(data.upper, otherWater, "50")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        e -> assertThat(e.getField()).isEqualTo("utilityAccountId"));
    }

    @Test
    void deletingAUnitRemovesItsSplitRules() {
        var basement = unitService.create(data.property.id(), new UnitRequest("Basement", null));
        splitRuleService.save(percent(basement, data.hydro, "20"));

        unitService.delete(basement.id());

        assertThat(propertyService.detail(data.property.id()).splitRules()).isEmpty();
    }
}
