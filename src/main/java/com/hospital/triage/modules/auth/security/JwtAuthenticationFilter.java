package com.hospital.triage.modules.auth.security;

import com.hospital.triage.common.constant.SecurityConstants;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, StringRedisTemplate stringRedisTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return SecurityConstants.WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(SecurityConstants.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(jwtTokenProvider.getTokenPrefix())) {
            String token = authorization.substring(jwtTokenProvider.getTokenPrefix().length()).trim();
            try {
                Claims claims = jwtTokenProvider.parseToken(token);
                Long userId = claims.get(SecurityConstants.USER_ID_CLAIM, Long.class);
                String cacheToken = stringRedisTemplate.opsForValue().get(String.format("auth:token:%s", userId));
                if (Objects.equals(token, cacheToken)) {
                    AuthenticatedUser user = AuthenticatedUser.builder()
                            .userId(userId)
                            .username(claims.get(SecurityConstants.USERNAME_CLAIM, String.class))
                            .nickname(claims.get("nickname", String.class))
                            .roleCode(claims.get("roleCode", String.class))
                            .deptId(claims.get("deptId", Long.class))
                            .roomId(claims.get("roomId", Long.class))
                            .permissions(castPermissions(claims.get("permissions")))
                            .build();
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            user, null, buildAuthorities(user));
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private Collection<SimpleGrantedAuthority> buildAuthorities(AuthenticatedUser user) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (StringUtils.hasText(user.getRoleCode())) {
            authorities.add(new SimpleGrantedAuthority(SecurityConstants.ROLE_PREFIX + user.getRoleCode()));
        }
        if (user.getPermissions() != null) {
            user.getPermissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return authorities;
    }

    @SuppressWarnings("unchecked")
    private List<String> castPermissions(Object permissions) {
        if (permissions instanceof List<?> permissionList) {
            return permissionList.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
