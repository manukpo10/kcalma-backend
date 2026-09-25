package com.kcalma.day;

import com.kcalma.day.dto.DayResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DayController {

    private final DayService dayService;

    public DayController(DayService dayService) {
        this.dayService = dayService;
    }

    @GetMapping("/day")
    public ResponseEntity<DayResponse> getDay(@AuthenticationPrincipal Jwt jwt, @RequestParam LocalDate date) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return dayService.getDay(userId, date).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
