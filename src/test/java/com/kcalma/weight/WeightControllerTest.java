package com.kcalma.weight;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kcalma.weight.dto.UpsertWeightRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * Direct unit test (no Spring context) for {@link WeightController}'s future-date guard, with a
 * {@link Clock} fixed to a specific instant/zone so the result never depends on the machine
 * running the test. 23:30 in America/Argentina/Buenos_Aires is already 02:30 UTC the NEXT
 * calendar day — if the controller ever fell back to a UTC (or any non-Argentina) "now", it would
 * treat the Argentina user's actual tomorrow as "today" (wrongly accepting it) or their actual
 * today as already past (wrongly rejecting it).
 */
class WeightControllerTest {

    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";

    private static final Clock ARGENTINA_CLOCK_AT_23_30 = Clock.fixed(
            LocalDateTime.of(2026, 9, 25, 23, 30)
                    .atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                    .toInstant(),
            ZoneId.of("America/Argentina/Buenos_Aires"));

    @Test
    void upsert_dateIsTomorrowInArgentinaTime_rejectedAsFuture() {
        WeightController controller = new WeightController(mock(WeightEntryService.class), ARGENTINA_CLOCK_AT_23_30);
        LocalDate argentinaTomorrow = LocalDate.of(2026, 9, 26);
        UpsertWeightRequest request = new UpsertWeightRequest(new BigDecimal("70.00"));

        assertThatThrownBy(() -> controller.upsert(jwtFor(OWNER_ID), argentinaTomorrow, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("La fecha no puede ser futura."));
    }

    @Test
    void upsert_dateIsTodayInArgentinaTimeEvenThoughUtcAlreadyRolledOver_isAccepted() {
        WeightEntryService serviceMock = mock(WeightEntryService.class);
        WeightController controller = new WeightController(serviceMock, ARGENTINA_CLOCK_AT_23_30);
        LocalDate argentinaToday = LocalDate.of(2026, 9, 25);
        UpsertWeightRequest request = new UpsertWeightRequest(new BigDecimal("70.00"));

        controller.upsert(jwtFor(OWNER_ID), argentinaToday, request);

        verify(serviceMock).upsert(UUID.fromString(OWNER_ID), argentinaToday, request);
    }

    private static Jwt jwtFor(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("aud", "authenticated")
                .build();
    }
}
