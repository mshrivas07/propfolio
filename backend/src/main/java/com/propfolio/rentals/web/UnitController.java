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

import com.propfolio.rentals.LeaseService;
import com.propfolio.rentals.UnitService;
import com.propfolio.rentals.web.LeaseDtos.LeaseCreateRequest;
import com.propfolio.rentals.web.LeaseDtos.LeaseResponse;
import com.propfolio.rentals.web.UnitDtos.UnitRequest;
import com.propfolio.rentals.web.UnitDtos.UnitResponse;

/**
 * A single unit and the leases on it.
 */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final UnitService unitService;
    private final LeaseService leaseService;

    public UnitController(UnitService unitService, LeaseService leaseService) {
        this.unitService = unitService;
        this.leaseService = leaseService;
    }

    @PutMapping("/{unitId}")
    public UnitResponse update(@PathVariable UUID unitId, @Valid @RequestBody UnitRequest request) {
        return unitService.update(unitId, request);
    }

    @DeleteMapping("/{unitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID unitId) {
        unitService.delete(unitId);
    }

    @GetMapping("/{unitId}/leases")
    public List<LeaseResponse> leases(@PathVariable UUID unitId) {
        return leaseService.listForUnit(unitId);
    }

    @PostMapping("/{unitId}/leases")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaseResponse createLease(@PathVariable UUID unitId, @Valid @RequestBody LeaseCreateRequest request) {
        return leaseService.create(unitId, request);
    }
}
