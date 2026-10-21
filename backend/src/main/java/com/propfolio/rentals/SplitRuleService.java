package com.propfolio.rentals;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.InvalidRequestException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.rentals.web.SplitRuleDtos.SplitRuleRequest;
import com.propfolio.rentals.web.SplitRuleDtos.SplitRuleResponse;
import com.propfolio.security.CurrentLandlord;

/**
 * How much of each utility account each unit pays.
 *
 * <p>Rules: one rule per unit per account; unit and account on the same property;
 * PERCENT rules on an account add up to at most 100 (the landlord pays the rest);
 * FLAT rules may sit alongside PERCENT rules and are not counted in that total.
 */
@Service
@Transactional
public class SplitRuleService {

    static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CurrentLandlord currentLandlord;
    private final SplitRuleRepository splitRules;
    private final UnitService unitService;
    private final UtilityAccountService utilityAccountService;

    public SplitRuleService(CurrentLandlord currentLandlord, SplitRuleRepository splitRules,
            UnitService unitService, UtilityAccountService utilityAccountService) {
        this.currentLandlord = currentLandlord;
        this.splitRules = splitRules;
        this.unitService = unitService;
        this.utilityAccountService = utilityAccountService;
    }

    /** Creates the rule for this unit + account, or replaces the one that exists. */
    public SplitRuleResponse save(SplitRuleRequest request) {
        UUID landlordId = currentLandlord.id();
        Unit unit = unitService.require(request.unitId(), landlordId);
        UtilityAccount account = utilityAccountService.require(request.utilityAccountId(), landlordId);

        if (!unit.getPropertyId().equals(account.getPropertyId())) {
            throw new InvalidRequestException("utilityAccountId",
                    "This utility account belongs to a different property than the unit");
        }

        BigDecimal value = request.shareValue().stripTrailingZeros();
        if (request.shareType() == ShareType.PERCENT) {
            if (value.compareTo(HUNDRED) > 0) {
                throw new InvalidRequestException("shareValue", "A percentage can't be more than 100");
            }
            List<SplitRule> others = splitRules.findAllByUtilityAccountId(account.getId()).stream()
                    .filter(rule -> !rule.getUnitId().equals(unit.getId()))
                    .toList();
            BigDecimal total = percentTotal(others).add(value);
            if (total.compareTo(HUNDRED) > 0) {
                throw new ConflictException(
                        "Units would pay %s%% of %s in total. Percentages on one utility account can't add up to more than 100%%."
                                .formatted(total.stripTrailingZeros().toPlainString(), account.getProviderName()));
            }
        } else if (value.scale() > 2) {
            throw new InvalidRequestException("shareValue", "Use at most 2 decimal places for a dollar amount");
        }

        SplitRule rule = splitRules
                .findByLandlordIdAndUnitIdAndUtilityAccountId(landlordId, unit.getId(), account.getId())
                .orElseGet(() -> new SplitRule(landlordId, unit.getId(), account.getId(), request.shareType(),
                        request.shareValue()));
        rule.setShareType(request.shareType());
        rule.setShareValue(request.shareValue());
        return RentalMapper.toResponse(splitRules.save(rule));
    }

    public void delete(UUID ruleId) {
        UUID landlordId = currentLandlord.id();
        SplitRule rule = splitRules.findByIdAndLandlordId(ruleId, landlordId)
                .orElseThrow(() -> new NotFoundException("Split rule"));
        splitRules.delete(rule);
    }

    /** Sum of PERCENT rules (FLAT rules are ignored). */
    static BigDecimal percentTotal(Collection<SplitRule> rules) {
        return rules.stream()
                .filter(rule -> rule.getShareType() == ShareType.PERCENT)
                .map(SplitRule::getShareValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
