package com.lk.quantfund.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.dto.auth.LoginRequest;
import com.lk.quantfund.dto.auth.RegisterRequest;
import com.lk.quantfund.entity.UserAccount;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.UserAccountMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(userAccountMapper, passwordEncoder, request);

    @Test
    void loginShouldReturnGenericErrorWhenUserDoesNotExist() {
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "Wrong1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void loginShouldReturnGenericErrorWhenPasswordIsWrong() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("quant-user");
        user.setPasswordHash(passwordEncoder.encode("Correct1234"));
        user.setStatus("ENABLED");
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login(new LoginRequest("quant-user", "Wrong1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("message")
                .isEqualTo("用户名或密码错误");
    }

    @Test
    void registerShouldRejectDuplicateUsername() {
        UserAccount existing = new UserAccount();
        existing.setId(1L);
        existing.setUsername("quant-user");
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        RegisterRequest request = new RegisterRequest("quant-user", "QuantFund2026", "Quant User", null, null);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting("message")
                .isEqualTo("用户名已被使用");
    }
}

