package com.propfolio.rentals;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.documents.BillRepository;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountRequest;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountResponse;
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

    public UtilityAccountService(CurrentLandlord currentLandlord, UtilityAccountRepository accounts,
            PropertyService propertyService, SplitRuleRepository splitRules, BillRepository bills) {
        this.currentLandlord = currentLandlord;
        this.accounts = accounts;
        this.propertyService = propertyService;
        this.splitRules = splitRules;
        this.bills = bills;
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
