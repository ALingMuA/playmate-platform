package com.gameplay.auth.security;

import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与解析服务。
 *
 * <p>令牌声明：uid（账号ID）、username、roles（角色编码集合）、tv（令牌版本）、
 * iat / exp。签发与解析使用 HMAC-SHA256，密钥来自配置 {@code jwt.secret}。</p>
 */
@Service
public class JwtTokenService {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtTokenService(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.expire-seconds:86400}") long expireSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    /** 签发令牌 */
    public String createToken(Long userId, String username, List<String> roles, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .claim("uid", userId)
                .claim("username", username)
                .claim("roles", roles)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /** 解析并校验签名/有效期；失败抛出 {@link ErrorCode#AUTH_TOKEN_INVALID} */
    @SuppressWarnings("unchecked")
    public JwtPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object rolesObj = claims.get("roles", Object.class);
            List<String> roles = rolesObj instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            return new JwtPrincipal(
                    claims.get("uid", Long.class),
                    claims.get("username", String.class),
                    claims.get("tv", Integer.class),
                    roles);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }
}
