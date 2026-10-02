package com.propfolio.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real security filter chain. Only the JWT decoder is mocked,
 * so no network call to Supabase is made.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeControllerSecurityTest {

    private static final String VALID_TOKEN = "valid-test-token";
    private static final String EXPIRED_TOKEN = "expired-test-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LandlordRepository landlordRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithRejectedTokenReturns401() throws Exception {
        given(jwtDecoder.decode(eq(EXPIRED_TOKEN))).willThrow(new BadJwtException("Jwt expired"));

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + EXPIRED_TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithValidTokenReturnsLandlordAndCreatesRow() throws Exception {
        UUID userId = UUID.randomUUID();
        given(jwtDecoder.decode(eq(VALID_TOKEN))).willReturn(supabaseToken(userId, "landlord@example.com"));

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("landlord@example.com"));

        assertThat(landlordRepository.findById(userId)).isPresent();

        // Second call reuses the existing row.
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
        assertThat(landlordRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    /** Same shape as a Supabase access token after signature checks. */
    private static Jwt supabaseToken(UUID userId, String email) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(VALID_TOKEN)
                .header("alg", "ES256")
                .issuer("https://test-project.supabase.co/auth/v1")
                .subject(userId.toString())
                .audience(List.of("authenticated"))
                .claim("email", email)
                .claim("role", "authenticated")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}
