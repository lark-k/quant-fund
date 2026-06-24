package com.lk.quantfund.service;

import com.lk.quantfund.dto.auth.LoginRequest;
import com.lk.quantfund.dto.auth.PasswordUpdateRequest;
import com.lk.quantfund.dto.auth.ProfileUpdateRequest;
import com.lk.quantfund.dto.auth.RegisterRequest;
import com.lk.quantfund.dto.auth.ResetPasswordRequest;
import com.lk.quantfund.vo.auth.LoginVO;
import com.lk.quantfund.vo.auth.UserProfileVO;

public interface AuthService {

    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    void logout();

    UserProfileVO currentUser();

    UserProfileVO updateProfile(ProfileUpdateRequest request);

    void updatePassword(PasswordUpdateRequest request);

    void reserveResetPassword(ResetPasswordRequest request);
}

