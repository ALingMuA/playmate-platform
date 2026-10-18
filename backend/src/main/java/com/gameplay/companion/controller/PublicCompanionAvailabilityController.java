package com.gameplay.companion.controller;

import com.gameplay.common.api.ApiResponse;
import com.gameplay.companion.dto.PublicAvailabilityView;
import com.gameplay.companion.service.PublicCompanionAvailabilityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 游客可读的陪玩师可约档期摘要。 */
@RestController
@Validated
@RequestMapping("/api/companion-profiles")
@RequiredArgsConstructor
public class PublicCompanionAvailabilityController {

    private final PublicCompanionAvailabilityService availabilityService;

    @GetMapping("/{userId}/availabilities")
    public ApiResponse<List<PublicAvailabilityView>> listAvailable(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "7") @Min(1) @Max(7) int days) {
        return ApiResponse.ok(availabilityService.listAvailable(userId, days));
    }
}
