package com.propfolio.rentals;

import static org.mockito.BDDMockito.given;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.security.CurrentLandlord;

/**
 * Runs the real services against the in-memory test database, signed in as one landlord.
 * Each test rolls back.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class RentalServiceTestBase {

    protected final UUID landlordId = UUID.randomUUID();

    @MockitoBean
    protected CurrentLandlord currentLandlord;

    @Autowired protected PropertyService propertyService;
    @Autowired protected UnitService unitService;
    @Autowired protected UtilityAccountService utilityAccountService;
    @Autowired protected SplitRuleService splitRuleService;
    @Autowired protected TenantService tenantService;
    @Autowired protected LeaseService leaseService;

    protected RentalTestData data;

    @BeforeEach
    void signInAndSeed() {
        given(currentLandlord.id()).willReturn(landlordId);
        data = new RentalTestData(propertyService, unitService, utilityAccountService, tenantService);
    }
}
