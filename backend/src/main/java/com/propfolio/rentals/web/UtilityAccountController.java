package com.propfolio.rentals.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.rentals.UtilityAccountService;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountRequest;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountResponse;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountSummary;

/**
 * Listing, editing and deleting utility accounts. Created under a property.
 */
@RestController
@RequestMapping("/api/utility-accounts")
public class UtilityAccountController {

    private final UtilityAccountService utilityAccountService;

    public UtilityAccountController(UtilityAccountService utilityAccountService) {
        this.utilityAccountService = utilityAccountService;
    }

    /** All of the landlord's utility accounts across properties (used by the bill upload picker). */
    @GetMapping
    public List<UtilityAccountSummary> listAll() {
        return utilityAccountService.listAll();
    }

    @PutMapping("/{accountId}")
    public UtilityAccountResponse update(@PathVariable UUID accountId,
            @Valid @RequestBody UtilityAccountRequest request) {
        return utilityAccountService.update(accountId, request);
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID accountId) {
        utilityAccountService.delete(accountId);
    }
}
