package com.propfolio.rentals;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.documents.BillRepository;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountRequest;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountResponse;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountSummary;
import com.propfolio.security.CurrentLandlord;

/**
 * Utility accounts on a property. Deleting one also deletes its split rules,
 * but is blocked once bills have been uploaded against it.
 */
@Service
@Transactional
public class UtilityAccountService {

    private final CurrentLandlord currentLandlord;
    private final UtilityAccountRepository accounts;
    private final PropertyService propertyService;
    private final SplitRuleRepository splitRules;
    private final BillRepository bills;
    private final PropertyRepository properties;

    public UtilityAccountService(CurrentLandlord currentLandlord, UtilityAccountRepository accounts,
            PropertyService propertyService, SplitRuleRepository splitRules, BillRepository bills,
            PropertyRepository properties) {
        this.currentLandlord = currentLandlord;
        this.accounts = accounts;
        this.propertyService = propertyService;
        this.splitRules = splitRules;
        this.bills = bills;
        this.properties = properties;
    }

    /** Every utility account the landlord has, sorted by property, then type and provider. */
    @Transactional(readOnly = true)
    public List<UtilityAccountSummary> listAll() {
        UUID landlordId = currentLandlord.id();
        Map<UUID, Property> propertyById = properties.findAllByLandlordIdOrderByNameAsc(landlordId).stream()
                .collect(Collectors.toMap(Property::getId, Function.identity()));
        return accounts.findAllByLandlordId(landlordId).stream()
                .map(a -> new UtilityAccountSummary(a.getId(), a.getPropertyId(),
                        propertyById.get(a.getPropertyId()).getName(), a.getUtilityType(), a.getProviderName(),
                        a.getAccountNumber()))
                .sorted(Comparator.comparing(UtilityAccountSummary::propertyName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(UtilityAccountSummary::utilityType)
                        .thenComparing(UtilityAccountSummary::providerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public UtilityAccountResponse create(UUID propertyId, UtilityAccountRequest request) {
        UUID landlordId = currentLandlord.id();
        propertyService.require(propertyId, landlordId);
        UtilityAccount account = new UtilityAccount(landlordId, propertyId, request.utilityType(),
                request.providerName().trim());
        account.setAccountNumber(RentalMapper.clean(request.accountNumber()));
        return RentalMapper.toResponse(accounts.save(account));
    }

    public UtilityAccountResponse update(UUID accountId, UtilityAccountRequest request) {
        UtilityAccount account = require(accountId, currentLandlord.id());
        account.setUtilityType(request.utilityType());
        account.setProviderName(request.providerName().trim());
        account.setAccountNumber(RentalMapper.clean(request.accountNumber()));
        return RentalMapper.toResponse(account);
    }

    public void delete(UUID accountId) {
        UtilityAccount account = require(accountId, currentLandlord.id());
        if (bills.existsByUtilityAccountId(accountId)) {
            throw new ConflictException("%s has bills, so it can't be deleted.".formatted(account.getProviderName()));
        }
        splitRules.deleteAllByUtilityAccountId(accountId);
        accounts.delete(account);
    }

    public UtilityAccount require(UUID accountId, UUID landlordId) {
        return accounts.findByIdAndLandlordId(accountId, landlordId)
                .orElseThrow(() -> new NotFoundException("Utility account"));
    }
}
