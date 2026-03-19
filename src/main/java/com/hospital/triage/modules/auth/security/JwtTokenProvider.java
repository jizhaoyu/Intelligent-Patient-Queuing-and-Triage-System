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
        return Jwts.builder()
                .claims(Map.of(
                        SecurityConstants.USER_ID_CLAIM, user.getUserId(),
                        SecurityConstants.USERNAME_CLAIM, user.getUsername(),
                        "nickname", user.getNickname(),
                        "roleCode", user.getRoleCode(),
                        "deptId", user.getDeptId(),
                        "roomId", user.getRoomId(),
                        "permissions", user.getPermissions() == null ? List.of() : user.getPermissions()
                ))
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
