package com.kcalma.food;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kcalma.food.analysis.FoodAnalyzer;
import com.kcalma.security.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web slice test for POST /api/food/analyze's byte-level image validation: the server must not
 * trust the client-supplied Content-Type and must reject bytes that don't match a supported
 * image signature with 415, without ever forwarding them to {@link FoodPhotoAnalyzer}. Same
 * {@code @WebMvcTest} + {@code SecurityConfig} setup as {@link FoodControllerSecurityTest}.
 */
@WebMvcTest(FoodController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.security.supabase-url=https://example.supabase.co",
    "app.security.owner-user-ids=11111111-1111-1111-1111-111111111111",
    "app.security.allowed-origins=http://localhost:5173"
})
class FoodControllerImageValidationTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TOKEN = "valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodAnalyzer foodAnalyzer;

    @MockitoBean
    private FoodEntryService foodEntryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void analyze_spoofedContentTypeButNonImageBytes_returns415AndNeverCallsAnalyzer() throws Exception {
        when(jwtDecoder.decode(TOKEN)).thenReturn(jwtFor(OWNER_ID));
        MockMultipartFile fakeImage = new MockMultipartFile(
                "image",
                "not-a-photo.jpg",
                "image/jpeg", // spoofed Content-Type header — the bytes below are plain text
                "definitely not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/food/analyze")
                        .file(fakeImage)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(foodAnalyzer);
    }

    private static Jwt jwtFor(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "ES256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("aud", "authenticated")
                .build();
    }
}
