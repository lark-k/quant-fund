package com.lk.quantfund.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lk.quantfund.auth.UserContext;
import com.lk.quantfund.dto.auth.LoginRequest;
import com.lk.quantfund.dto.auth.PasswordUpdateRequest;
import com.lk.quantfund.dto.auth.ProfileUpdateRequest;
import com.lk.quantfund.dto.auth.RegisterRequest;
import com.lk.quantfund.dto.auth.ResetPasswordRequest;
import com.lk.quantfund.entity.UserAccount;
import com.lk.quantfund.enums.ErrorCode;
import com.lk.quantfund.enums.RiskLevel;
import com.lk.quantfund.enums.UserRole;
import com.lk.quantfund.enums.UserStatus;
import com.lk.quantfund.exception.BusinessException;
import com.lk.quantfund.mapper.UserAccountMapper;
import com.lk.quantfund.service.AuthService;
import com.lk.quantfund.util.ClientIpUtil;
import com.lk.quantfund.vo.auth.LoginVO;
import com.lk.quantfund.vo.auth.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final HttpServletRequest request;

    public AuthServiceImpl(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder, HttpServletRequest request) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.request = request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterRequest request) {
        ensureUsernameAvailable(request.username());
        ensureContactAvailable(request.phone(), request.email(), null);

        LocalDateTime now = LocalDateTime.now();
        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setPhone(blankToNull(request.phone()));
        user.setEmail(blankToNull(request.email()));
        user.setRole(UserRole.USER.name());
        user.setStatus(UserStatus.ENABLED.name());
        user.setRiskLevel(RiskLevel.MEDIUM.name());
        user.setRegisterTime(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        user.setDeleted(0);
        userAccountMapper.insert(user);

        StpUtil.login(user.getId());
        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request) {
        UserAccount user = findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!UserStatus.ENABLED.name().equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号状态不可用");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginTime(now);
        user.setLastLoginIp(ClientIpUtil.getClientIp(this.request));
        user.setUpdateTime(now);
        userAccountMapper.updateById(user);

        StpUtil.login(user.getId());
        return buildLoginVO(user);
    }

    @Override
    public void logout() {
        StpUtil.checkLogin();
        StpUtil.logout();
    }

    @Override
    public UserProfileVO currentUser() {
        return toProfile(loadCurrentUser());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateProfile(ProfileUpdateRequest request) {
        UserAccount current = loadCurrentUser();
        ensureContactAvailable(request.phone(), request.email(), current.getId());

        current.setNickname(valueOrCurrent(request.nickname(), current.getNickname()));
        current.setPhone(blankToNull(request.phone()));
        current.setEmail(blankToNull(request.email()));
        current.setAvatar(blankToNull(request.avatar()));
        current.setUpdateTime(LocalDateTime.now());
        userAccountMapper.updateById(current);
        return toProfile(current);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(PasswordUpdateRequest request) {
        UserAccount current = loadCurrentUser();
        if (!passwordEncoder.matches(request.oldPassword(), current.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), current.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        current.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        current.setUpdateTime(LocalDateTime.now());
        userAccountMapper.updateById(current);
        StpUtil.logout(current.getId());
    }

    @Override
    public void reserveResetPassword(ResetPasswordRequest request) {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "重置密码能力已预留，当前版本暂未开放");
    }

    private LoginVO buildLoginVO(UserAccount user) {
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return new LoginVO(
                tokenInfo.getTokenName(),
                tokenInfo.getTokenValue(),
                tokenInfo.getTokenTimeout(),
                toProfile(user)
        );
    }

    private UserProfileVO toProfile(UserAccount user) {
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                user.getEmail(),
                user.getAvatar(),
                user.getRole(),
                user.getStatus(),
                user.getRiskLevel(),
                user.getLastLoginTime()
        );
    }

    private UserAccount loadCurrentUser() {
        Long userId = UserContext.getUserId();
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private UserAccount findByUsername(String username) {
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)
                .last("LIMIT 1"));
    }

    private void ensureUsernameAvailable(String username) {
        if (findByUsername(username) != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名已被使用");
        }
    }

    private void ensureContactAvailable(String phone, String email, Long currentUserId) {
        ensureUniqueContact(UserAccount::getPhone, phone, currentUserId, "手机号已被使用");
        ensureUniqueContact(UserAccount::getEmail, email, currentUserId, "邮箱已被使用");
    }

    private void ensureUniqueContact(com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserAccount, ?> column,
                                     String value,
                                     Long currentUserId,
                                     String message) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<UserAccount>().eq(column, value);
        if (currentUserId != null) {
            wrapper.ne(UserAccount::getId, currentUserId);
        }
        if (userAccountMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String valueOrCurrent(String value, String current) {
        return StringUtils.hasText(value) ? value.trim() : current;
    }
}
