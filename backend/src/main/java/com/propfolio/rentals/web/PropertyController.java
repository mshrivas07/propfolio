package com.propfolio.rentals.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.rentals.PropertyService;
import com.propfolio.rentals.UnitService;
import com.propfolio.rentals.UtilityAccountService;
import com.propfolio.rentals.web.PropertyDtos.PropertyDetailResponse;
import com.propfolio.rentals.web.PropertyDtos.PropertyRequest;
import com.propfolio.rentals.web.PropertyDtos.PropertyResponse;
import com.propfolio.rentals.web.UnitDtos.UnitRequest;
import com.propfolio.rentals.web.UnitDtos.UnitResponse;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountRequest;
import com.propfolio.rentals.web.UtilityAccountDtos.UtilityAccountResponse;

/**
 * Properties, and the units and utility accounts created under them.
 */
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;
    private final UnitService unitService;
    private final UtilityAccountService utilityAccountService;

    public PropertyController(PropertyService propertyService, UnitService unitService,
            UtilityAccountService utilityAccountService) {
        this.propertyService = propertyService;
        this.unitService = unitService;
        this.utilityAccountService = utilityAccountService;
    }

    @GetMapping
    public List<PropertyResponse> list() {
        return propertyService.list();
    }

    @GetMapping("/{propertyId}")
    public PropertyDetailResponse detail(@PathVariable UUID propertyId) {
        return propertyService.detail(propertyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@Valid @RequestBody PropertyRequest request) {
        return propertyService.create(request);
    }

    @PutMapping("/{propertyId}")
    public PropertyResponse update(@PathVariable UUID propertyId, @Valid @RequestBody PropertyRequest request) {
        return propertyService.update(propertyId, request);
    }

    @DeleteMapping("/{propertyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID propertyId) {
        propertyService.delete(propertyId);
    }

    @PostMapping("/{propertyId}/units")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse createUnit(@PathVariable UUID propertyId, @Valid @RequestBody UnitRequest request) {
        return unitService.create(propertyId, request);
    }

    @PostMapping("/{propertyId}/utility-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public UtilityAccountResponse createUtilityAccount(@PathVariable UUID propertyId,
            @Valid @RequestBody UtilityAccountRequest request) {
        return utilityAccountService.create(propertyId, request);
    }
}
