package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.ChangePasswordRequest;
import co.za.tveco.bff.dto.ProfileResponse;
import co.za.tveco.bff.dto.UpdateProfileRequest;
import co.za.tveco.bff.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> getMyProfile(Authentication authentication) {
        return ApiResponse.of(profileService.getMyProfile(authentication.getName()));
    }

    @PatchMapping("/me")
    public ApiResponse<ProfileResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest req
    ) {
        return ApiResponse.of(profileService.updateMyProfile(authentication.getName(), req));
    }

    @PostMapping("/change-password")
    public ApiResponse<Map<String, Boolean>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest req
    ) {
        profileService.changePassword(authentication.getName(), req);
        return ApiResponse.of(Map.of("ok", true));
    }
}
