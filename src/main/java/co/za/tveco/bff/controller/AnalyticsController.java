package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.AnalyticsDto;
import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/analytics?from=2026-01-01&to=2026-12-31
     *
     * Defaults to last 6 months if not supplied.
     */
    @GetMapping
    public ApiResponse<AnalyticsDto> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate effectiveTo   = to   != null ? to   : LocalDate.now();
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusMonths(6);
        return ApiResponse.of(analyticsService.compute(effectiveFrom, effectiveTo));
    }
}
