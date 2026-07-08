package co.za.tveco.bff.controller;

import co.za.tveco.bff.dto.ApiResponse;
import co.za.tveco.bff.dto.AuthLoginRequest;
import co.za.tveco.bff.dto.AuthLoginResponse;
import co.za.tveco.bff.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest req) {
        return ApiResponse.of(authService.login(req));
    }
}
