package com.gameplay.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.entity.Role;
import com.gameplay.auth.mapper.RoleMapper;
import com.gameplay.auth.service.AccountAuthService;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 *
 * <p>从 {@code Authorization: Bearer <JWT>} 提取令牌，校验签名与有效期，
 * 再到数据库复核账号状态与令牌版本（详细设计 2.1 / 7.3），
 * 通过后将 {@link JwtPrincipal} 写入 SecurityContext。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final AccountAuthService accountAuthService;
    private final RoleMapper roleMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // 未携带令牌：放行，由 Security 授权规则决定是否允许匿名访问
            filterChain.doFilter(request, response);
            return;
        }

        JwtPrincipal principal;
        try {
            principal = jwtTokenService.parse(header.substring(BEARER_PREFIX.length()));
            accountAuthService.assertEnabledAndTokenVersion(principal.userId(), principal.tokenVersion());
        } catch (BusinessException e) {
            writeError(response, e.getErrorCode());
            return;
        }

        // 从数据库实时读取已启用角色，确保后台授权/回收角色后权限立即生效
        List<String> dbRoles = roleMapper.selectRolesByUserId(principal.userId()).stream()
                .map(Role::getRoleCode)
                .toList();
        JwtPrincipal livePrincipal = new JwtPrincipal(
                principal.userId(),
                principal.username(),
                principal.tokenVersion(),
                dbRoles);
        List<SimpleGrantedAuthority> authorities = dbRoles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(livePrincipal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    /** 过滤器位于 Spring MVC 之前，需直接写 JSON 响应 */
    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage())));
    }
}
