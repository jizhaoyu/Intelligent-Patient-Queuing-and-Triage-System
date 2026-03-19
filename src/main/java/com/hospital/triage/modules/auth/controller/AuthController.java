package com.hospital.triage.modules.auth.controller;

import com.hospital.triage.common.api.Result;
import com.hospital.triage.modules.auth.dto.LoginRequest;
import com.hospital.triage.modules.auth.security.AuthenticatedUser;
import com.hospital.triage.modules.auth.service.AuthService;
import com.hospital.triage.modules.auth.vo.LoginVO;
import com.hospital.triage.modules.auth.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.logout(user);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserProfileVO> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.success(authService.me(user));
    }
}
