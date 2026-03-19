package com.hospital.triage.common.constant;

import java.util.List;

public final class SecurityConstants {

    public static final String AUTHORIZATION = "Authorization";
    public static final String USER_ID_CLAIM = "userId";
    public static final String USERNAME_CLAIM = "username";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final List<String> WHITE_LIST = List.of(
            "/api/auth/login",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/doc.html",
            "/actuator/health"
    );

    private SecurityConstants() {
    }
}
