package com.propfolio.rentals.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.propfolio.rentals.LeaseService;
import com.propfolio.rentals.web.LeaseDtos.LeaseResponse;
import com.propfolio.rentals.web.LeaseDtos.LeaseUpdateRequest;

/**
 * Editing and ending leases. Leases are created under a unit (POST /api/units/{id}/leases).
 */
@RestController
@RequestMapping("/api/leases")
public class LeaseController {

    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PutMapping("/{leaseId}")
    public LeaseResponse update(@PathVariable UUID leaseId, @Valid @RequestBody LeaseUpdateRequest request) {
        return leaseService.update(leaseId, request);
    }

    @DeleteMapping("/{leaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID leaseId) {
        leaseService.delete(leaseId);
    }
}
