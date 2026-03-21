package com.hospital.triage.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.triage.common.constant.RedisKeyConstants;
import com.hospital.triage.common.enums.ErrorCodeEnum;
import com.hospital.triage.exception.ServiceException;
import com.hospital.triage.modules.auth.dto.LoginRequest;
import com.hospital.triage.modules.auth.security.AppSecurityProperties;
import com.hospital.triage.modules.auth.security.AuthenticatedUser;
import com.hospital.triage.modules.auth.security.JwtTokenProvider;
import com.hospital.triage.modules.auth.service.AuthService;
import com.hospital.triage.modules.auth.vo.LoginVO;
import com.hospital.triage.modules.auth.vo.UserProfileVO;
import com.hospital.triage.modules.system.entity.po.SysUser;
import com.hospital.triage.modules.system.mapper.SysUserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Map<String, List<String>> ROLE_PERMISSION_MAP = Map.of(
            "ADMIN", List.of("patient:manage", "visit:manage", "triage:assess", "triage:rule", "queue:manage", "queue:call", "dashboard:view"),
            "DOCTOR", List.of("queue:call", "triage:assess", "dashboard:view")
    );

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final AppSecurityProperties securityProperties;

    public AuthServiceImpl(SysUserMapper sysUserMapper,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           StringRedisTemplate stringRedisTemplate,
                           AppSecurityProperties securityProperties) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.stringRedisTemplate = stringRedisTemplate;
        this.securityProperties = securityProperties;
    }

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .last("limit 1"));
        if (user == null || user.getEnabled() == 0 || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ServiceException(ErrorCodeEnum.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        AuthenticatedUser authenticatedUser = buildAuthenticatedUser(user);
        String token = jwtTokenProvider.createToken(authenticatedUser);
        stringRedisTemplate.opsForValue().set(String.format(RedisKeyConstants.AUTH_TOKEN, user.getId()), token,
                securityProperties.getJwtExpireSeconds(), TimeUnit.SECONDS);
        return LoginVO.builder()
                .token(token)
                .tokenType(securityProperties.getTokenPrefix())
                .expireSeconds(securityProperties.getJwtExpireSeconds())
                .profile(toProfile(authenticatedUser))
                .build();
    }

    @Override
    public void logout(AuthenticatedUser user) {
        if (user != null && user.getUserId() != null) {
            stringRedisTemplate.delete(String.format(RedisKeyConstants.AUTH_TOKEN, user.getUserId()));
        }
    }

    @Override
    public UserProfileVO me(AuthenticatedUser user) {
        if (user == null) {
            throw new ServiceException(ErrorCodeEnum.UNAUTHORIZED);
        }
        return toProfile(user);
    }

    private AuthenticatedUser buildAuthenticatedUser(SysUser user) {
        String roleCode = StringUtils.hasText(user.getRoleCode()) ? user.getRoleCode() : "";
        return AuthenticatedUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roleCode(roleCode)
                .deptId(user.getDeptId())
                .roomId(user.getRoomId())
                .permissions(ROLE_PERMISSION_MAP.getOrDefault(roleCode, List.of()))
                .build();
    }

    private UserProfileVO toProfile(AuthenticatedUser user) {
        return UserProfileVO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roleCode(user.getRoleCode())
                .deptId(user.getDeptId())
                .roomId(user.getRoomId())
                .permissions(user.getPermissions())
                .build();
    }
}
