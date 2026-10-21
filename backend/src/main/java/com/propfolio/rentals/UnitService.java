package com.propfolio.rentals;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.InvoiceRepository;
import com.propfolio.common.error.ConflictException;
import com.propfolio.common.error.NotFoundException;
import com.propfolio.rentals.web.UnitDtos.UnitRequest;
import com.propfolio.rentals.web.UnitDtos.UnitResponse;
import com.propfolio.security.CurrentLandlord;

/**
 * Units inside a property. Deleting a unit also deletes its split rules,
 * but is blocked while it has leases or invoices.
 */
@Service
@Transactional
public class UnitService {

    private final CurrentLandlord currentLandlord;
    private final UnitRepository units;
    private final PropertyService propertyService;
    private final LeaseRepository leases;
    private final SplitRuleRepository splitRules;
    private final InvoiceRepository invoices;
    private final LeaseService leaseService;

    public UnitService(CurrentLandlord currentLandlord, UnitRepository units, PropertyService propertyService,
            LeaseRepository leases, SplitRuleRepository splitRules, InvoiceRepository invoices,
            LeaseService leaseService) {
        this.currentLandlord = currentLandlord;
        this.units = units;
        this.propertyService = propertyService;
        this.leases = leases;
        this.splitRules = splitRules;
        this.invoices = invoices;
        this.leaseService = leaseService;
    }

    public UnitResponse create(UUID propertyId, UnitRequest request) {
        UUID landlordId = currentLandlord.id();
        propertyService.require(propertyId, landlordId);
        String name = request.name().trim();
        if (units.existsByPropertyIdAndNameIgnoreCase(propertyId, name)) {
            throw duplicateName(name);
        }
        Unit unit = new Unit(landlordId, propertyId, name);
        unit.setNotes(RentalMapper.clean(request.notes()));
        return RentalMapper.toResponse(units.save(unit), List.of());
    }

    public UnitResponse update(UUID unitId, UnitRequest request) {
        UUID landlordId = currentLandlord.id();
        Unit unit = require(unitId, landlordId);
        String name = request.name().trim();
        if (units.existsByPropertyIdAndNameIgnoreCaseAndIdNot(unit.getPropertyId(), name, unitId)) {
            throw duplicateName(name);
        }
        unit.setName(name);
        unit.setNotes(RentalMapper.clean(request.notes()));
        return RentalMapper.toResponse(unit, leaseService.toResponses(
                leases.findAllByLandlordIdAndUnitIdOrderByStartDateDesc(landlordId, unitId)));
    }

    public void delete(UUID unitId) {
        UUID landlordId = currentLandlord.id();
        Unit unit = require(unitId, landlordId);
        if (invoices.existsByUnitId(unitId)) {
            throw new ConflictException("%s has invoices, so it can't be deleted.".formatted(unit.getName()));
        }
        if (leases.existsByUnitId(unitId)) {
            throw new ConflictException(
                    "%s has leases. Delete its leases first.".formatted(unit.getName()));
        }
        splitRules.deleteAllByUnitId(unitId);
        units.delete(unit);
    }

    public Unit require(UUID unitId, UUID landlordId) {
        return units.findByIdAndLandlordId(unitId, landlordId).orElseThrow(() -> new NotFoundException("Unit"));
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException("This property already has a unit named \"%s\".".formatted(name));
    }
}
