package com.propfolio.rentals;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.rentals.web.LeaseDtos.LeaseResponse;
import com.propfolio.rentals.web.PropertyDtos.AccountPercentTotal;
import com.propfolio.rentals.web.PropertyDtos.PropertyDetailResponse;
import com.propfolio.rentals.web.PropertyDtos.PropertyRequest;
import com.propfolio.rentals.web.PropertyDtos.PropertyResponse;
import com.propfolio.rentals.web.SplitRuleDtos.SplitRuleResponse;
import com.propfolio.security.CurrentLandlord;

/**
 * Properties, plus the combined detail view (units, leases, utility accounts, split rules).
 */
@Service
@Transactional
public class PropertyService {

    private final CurrentLandlord currentLandlord;
    private final PropertyRepository properties;
    private final UnitRepository units;
    private final UtilityAccountRepository utilityAccounts;
    private final SplitRuleRepository splitRules;
    private final LeaseRepository leases;
    private final LeaseService leaseService;

    public PropertyService(CurrentLandlord currentLandlord, PropertyRepository properties, UnitRepository units,
            UtilityAccountRepository utilityAccounts, SplitRuleRepository splitRules, LeaseRepository leases,
            LeaseService leaseService) {
        this.currentLandlord = currentLandlord;
        this.properties = properties;
        this.units = units;
        this.utilityAccounts = utilityAccounts;
        this.splitRules = splitRules;
        this.leases = leases;
        this.leaseService = leaseService;
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> list() {
        UUID landlordId = currentLandlord.id();
        return properties.findAllByLandlordIdOrderByNameAsc(landlordId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PropertyDetailResponse detail(UUID propertyId) {
        UUID landlordId = currentLandlord.id();
        Property property = require(propertyId, landlordId);

        List<Unit> unitRows = units.findAllByLandlordIdAndPropertyIdOrderByNameAsc(landlordId, propertyId);
        List<UtilityAccount> accountRows = utilityAccounts
                .findAllByLandlordIdAndPropertyIdOrderByUtilityTypeAscProviderNameAsc(landlordId, propertyId);
        List<SplitRule> ruleRows = accountRows.isEmpty() ? List.of()
                : splitRules.findAllByLandlordIdAndUtilityAccountIdIn(landlordId,
                        accountRows.stream().map(UtilityAccount::getId).toList());

        List<LeaseResponse> leaseResponses = unitRows.isEmpty() ? List.of()
                : leaseService.toResponses(leases.findAllByLandlordIdAndUnitIdIn(landlordId,
                        unitRows.stream().map(Unit::getId).toList()));
        Map<UUID, List<LeaseResponse>> leasesByUnit = leaseResponses.stream()
                .sorted((a, b) -> b.startDate().compareTo(a.startDate()))
                .collect(Collectors.groupingBy(LeaseResponse::unitId));

        List<SplitRuleResponse> ruleResponses = ruleRows.stream().map(RentalMapper::toResponse).toList();
        List<AccountPercentTotal> totals = accountRows.stream()
                .map(account -> new AccountPercentTotal(account.getId(), SplitRuleService.percentTotal(
                        ruleRows.stream().filter(r -> r.getUtilityAccountId().equals(account.getId())).toList())))
                .toList();

        return new PropertyDetailResponse(
                RentalMapper.toResponse(property, unitRows.size(), accountRows.size()),
                unitRows.stream()
                        .map(u -> RentalMapper.toResponse(u, leasesByUnit.getOrDefault(u.getId(), List.of())))
                        .toList(),
                accountRows.stream().map(RentalMapper::toResponse).toList(),
                ruleResponses,
                totals);
    }

    public PropertyResponse create(PropertyRequest request) {
        UUID landlordId = currentLandlord.id();
        String name = request.name().trim();
        if (properties.existsByLandlordIdAndNameIgnoreCase(landlordId, name)) {
            throw duplicateName(name);
        }
        Property property = new Property(landlordId, name);
        apply(property, request);
        return toResponse(properties.save(property));
    }

    public PropertyResponse update(UUID propertyId, PropertyRequest request) {
        UUID landlordId = currentLandlord.id();
        Property property = require(propertyId, landlordId);
        String name = request.name().trim();
        if (properties.existsByLandlordIdAndNameIgnoreCaseAndIdNot(landlordId, name, propertyId)) {
            throw duplicateName(name);
        }
        property.setName(name);
        apply(property, request);
        return toResponse(property);
    }

    public void delete(UUID propertyId) {
        UUID landlordId = currentLandlord.id();
        Property property = require(propertyId, landlordId);
        if (units.existsByPropertyId(propertyId) || utilityAccounts.existsByPropertyId(propertyId)) {
            throw new ConflictException(
                    "%s still has units or utility accounts. Delete those first.".formatted(property.getName()));
        }
        properties.delete(property);
    }

    /** Loads the property if it belongs to the landlord; otherwise 404. Used by the other rental services. */
    public Property require(UUID propertyId, UUID landlordId) {
        return properties.findByIdAndLandlordId(propertyId, landlordId)
                .orElseThrow(() -> new NotFoundException("Property"));
    }

    private PropertyResponse toResponse(Property property) {
        return RentalMapper.toResponse(property, units.countByPropertyId(property.getId()),
                utilityAccounts.countByPropertyId(property.getId()));
    }

    private static void apply(Property property, PropertyRequest request) {
        property.setAddressLine1(RentalMapper.clean(request.addressLine1()));
        property.setAddressLine2(RentalMapper.clean(request.addressLine2()));
        property.setCity(RentalMapper.clean(request.city()));
        property.setProvince(RentalMapper.clean(request.province()));
        property.setPostalCode(RentalMapper.clean(request.postalCode()));
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException("You already have a property named \"%s\".".formatted(name));
    }
}
