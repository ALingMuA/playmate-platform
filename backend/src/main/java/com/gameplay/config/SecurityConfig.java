package com.gameplay.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplay.auth.security.JwtAuthFilter;
import com.gameplay.common.api.ApiResponse;
import com.gameplay.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security 配置：无状态 JWT 认证 + RBAC。
 *
 * <p>注册、登录接口匿名可访问；其余接口需携带有效令牌。
 * 接口级角色控制使用 {@code @PreAuthorize("hasRole('ADMIN')")}（已启用 @EnableMethodSecurity）。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/error").permitAll()
                        // 目录与公开浏览接口：游戏、服务类型、标签、可预约服务（游客可浏览，概要设计 8.x 分组）
                        .requestMatchers("/api/games/**", "/api/service-types/**", "/api/tags/**",
                                "/api/companion-services/**").permitAll()
                        // 评价展示与文件读取公开（URL 含 UUID 不可枚举；上传仍需登录）
                        .requestMatchers("/api/reviews/companion/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        // 公告查看公开（仅已发布，FR-A08）
                        .requestMatchers(HttpMethod.GET, "/api/announcements/**").permitAll()
                        // WebSocket 客服端点：握手拦截器自行完成 JWT 鉴权（详细设计 7.3）
                        .requestMatchers("/ws/cs").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, ErrorCode.AUTH_TOKEN_MISSING))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, ErrorCode.PERMISSION_DENIED)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 密码哈希：BCrypt（详细设计 1.1 `user.password_hash` 字段说明） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 开发期 CORS：全放开；部署时经 Nginx 同源后应收紧 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage())));
    }
}