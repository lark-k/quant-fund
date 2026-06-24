package com.lk.quantfund.controller;

import com.lk.quantfund.annotation.OperationLog;
import com.lk.quantfund.annotation.RateLimit;
import com.lk.quantfund.annotation.RepeatSubmit;
import com.lk.quantfund.annotation.RequireLogin;
import com.lk.quantfund.common.ApiResponse;
import com.lk.quantfund.constants.SystemConstants;
import com.lk.quantfund.dto.auth.LoginRequest;
import com.lk.quantfund.dto.auth.PasswordUpdateRequest;
import com.lk.quantfund.dto.auth.ProfileUpdateRequest;
import com.lk.quantfund.dto.auth.RegisterRequest;
import com.lk.quantfund.dto.auth.ResetPasswordRequest;
import com.lk.quantfund.service.AuthService;
import com.lk.quantfund.vo.auth.LoginVO;
import com.lk.quantfund.vo.auth.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(SystemConstants.API_PREFIX + "/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @RateLimit(key = "auth:register", windowSeconds = 60, maxRequests = 10, userScoped = false)
    @RepeatSubmit(intervalSeconds = 3, userScoped = false)
    @OperationLog(module = "auth", action = "register", bizType = "USER_AUTH")
    public ApiResponse<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    @RateLimit(key = "auth:login", windowSeconds = 60, maxRequests = 20, userScoped = false)
    @RepeatSubmit(intervalSeconds = 2, userScoped = false)
    @OperationLog(module = "auth", action = "login", bizType = "USER_AUTH")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    @RequireLogin
    @OperationLog(module = "auth", action = "logout", bizType = "USER_AUTH")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success();
    }

    @GetMapping("/me")
    @RequireLogin
    public ApiResponse<UserProfileVO> me() {
        return ApiResponse.success(authService.currentUser());
    }

    @PutMapping("/profile")
    @RequireLogin
    @RepeatSubmit(intervalSeconds = 3)
    @OperationLog(module = "auth", action = "update_profile", bizType = "USER_AUTH")
    public ApiResponse<UserProfileVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success(authService.updateProfile(request));
    }

    @PutMapping("/password")
    @RequireLogin
    @RepeatSubmit(intervalSeconds = 5)
    @OperationLog(module = "auth", action = "update_password", bizType = "USER_AUTH")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody PasswordUpdateRequest request) {
        authService.updatePassword(request);
        return ApiResponse.success();
    }

    @PostMapping("/reset-password")
    @RateLimit(key = "auth:reset-password", windowSeconds = 60, maxRequests = 5, userScoped = false)
    @RepeatSubmit(intervalSeconds = 10, userScoped = false)
    @OperationLog(module = "auth", action = "reset_password_reserved", bizType = "USER_AUTH")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.reserveResetPassword(request);
        return ApiResponse.success();
    }
}
