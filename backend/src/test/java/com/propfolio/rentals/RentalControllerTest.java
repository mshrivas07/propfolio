package com.propfolio.rentals;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTP-level checks: status codes, ProblemDetail bodies and landlord isolation.
 * Two landlords sign in with test JWTs; data is created through the API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RentalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final RequestPostProcessor alice = landlord(UUID.randomUUID(), "alice@example.com");
    private final RequestPostProcessor bob = landlord(UUID.randomUUID(), "bob@example.com");

    @Test
    void createAndReadPropertyReturns201Then200() throws Exception {
        String id = createProperty(alice, "Maple House");

        mockMvc.perform(get("/api/properties/" + id).with(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.property.name").value("Maple House"))
                .andExpect(jsonPath("$.units").isArray());
    }

    @Test
    void missingNameReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/properties").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void invalidTenantEmailReturns400ForThatEmail() throws Exception {
        mockMvc.perform(post("/api/tenants").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Sam\", \"emails\": [\"ok@example.com\", \"not-an-email\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("emails[1]")));
    }

    @Test
    void leaseEndBeforeStartReturns400WithEndDateField() throws Exception {
        String propertyId = createProperty(alice, "Birch House");
        String unitId = create(alice, "/api/properties/" + propertyId + "/units", "{\"name\": \"Main\"}");
        String tenantId = create(alice, "/api/tenants", "{\"fullName\": \"Sam\", \"emails\": [\"sam@example.com\"]}");

        mockMvc.perform(post("/api/units/" + unitId + "/leases").with(alice).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\": \"%s\", \"startDate\": \"2026-09-01\", \"endDate\": \"2026-08-01\"}"
                                .formatted(tenantId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"));
    }

    @Test
    void duplicatePropertyNameReturns409() throws Exception {
        createProperty(alice, "Cedar House");

        mockMvc.perform(post("/api/properties").with(alice)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"cedar house\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void deletingPropertyWithUnitsReturns409() throws Exception {
        String propertyId = createProperty(alice, "Elm House");
        create(alice, "/api/properties/" + propertyId + "/units", "{\"name\": \"Main\"}");

        mockMvc.perform(delete("/api/properties/" + propertyId).with(alice))
                .andExpect(status().isConflict());
    }

    @Test
    void percentagesOver100Returns409() throws Exception {
        String propertyId = createProperty(alice, "Oak House");
        String upper = create(alice, "/api/properties/" + propertyId + "/units", "{\"name\": \"Upper\"}");
        String lower = create(alice, "/api/properties/" + propertyId + "/units", "{\"name\": \"Lower\"}");
        String water = create(alice, "/api/properties/" + propertyId + "/utility-accounts",
                "{\"utilityType\": \"WATER\", \"providerName\": \"City Water\"}");

        saveRule(alice, upper, water, "PERCENT", "70").andExpect(status().isOk());
        saveRule(alice, lower, water, "PERCENT", "40").andExpect(status().isConflict());
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/properties/" + UUID.randomUUID()).with(alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    void anotherLandlordsRecordsLookLikeTheyDoNotExist() throws Exception {
        String propertyId = createProperty(alice, "Alice's House");
        String unitId = create(alice, "/api/properties/" + propertyId + "/units", "{\"name\": \"Main\"}");
        String tenantId = create(alice, "/api/tenants", "{\"fullName\": \"Sam\", \"emails\": [\"sam@example.com\"]}");

        mockMvc.perform(get("/api/properties/" + propertyId).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/properties/" + propertyId).with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Taken\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/units/" + unitId).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tenants/" + tenantId).with(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/properties/" + propertyId + "/units").with(bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Sneaky\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/properties").with(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(propertyId)).isEmpty());
        mockMvc.perform(get("/api/tenants").with(bob))
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(tenantId)).isEmpty());
    }

    @Test
    void requestsWithoutATokenReturn401() throws Exception {
        mockMvc.perform(get("/api/properties")).andExpect(status().isUnauthorized());
    }

    private String createProperty(RequestPostProcessor who, String name) throws Exception {
        return create(who, "/api/properties", "{\"name\": \"%s\"}".formatted(name));
    }

    private String create(RequestPostProcessor who, String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).with(who).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions saveRule(RequestPostProcessor who, String unitId,
            String accountId, String type, String value) throws Exception {
        return mockMvc.perform(put("/api/split-rules").with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"unitId\": \"%s\", \"utilityAccountId\": \"%s\", \"shareType\": \"%s\", \"shareValue\": %s}"
                        .formatted(unitId, accountId, type, value)));
    }

    private static RequestPostProcessor landlord(UUID id, String email) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("email", email));
    }
}
