package com.hospital.triage.modules.auth.security;

import com.hospital.triage.common.constant.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final AppSecurityProperties properties;

    public JwtTokenProvider(AppSecurityProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getJwtExpireSeconds());
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(SecurityConstants.USER_ID_CLAIM, user.getUserId());
        claims.put(SecurityConstants.USERNAME_CLAIM, user.getUsername());
        claims.put("nickname", user.getNickname());
        claims.put("roleCode", user.getRoleCode());
        claims.put("permissions", user.getPermissions() == null ? List.of() : user.getPermissions());
        if (user.getDeptId() != null) {
            claims.put("deptId", user.getDeptId());
        }
        if (user.getRoomId() != null) {
            claims.put("roomId", user.getRoomId());
        }
        return Jwts.builder()
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getTokenPrefix() {
        return properties.getTokenPrefix();
    }
}
