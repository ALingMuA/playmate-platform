package com.gameplay.infrastructure.websocket;

import com.gameplay.auth.event.SessionInvalidatedEvent;
import com.gameplay.auth.security.JwtPrincipal;
import com.gameplay.common.exception.BusinessException;
import com.gameplay.common.exception.ErrorCode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 一次性握手票据存储（替代 URL 查询参数传递长效 JWT）。
 *
 * <p>票据采用 256 位随机不透明串、30 秒有效期、单次消费即删，并限制总容量与单用户上限。</p>
 */
@Service
public class WsTicketService {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final int MAX_CAPACITY = 10_000;
    private static final int MAX_PER_USER = 20;
    private static final int PRUNE_THRESHOLD = 512;

    public record TicketEntry(Long userId, String username, int tokenVersion, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, TicketEntry> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** 为已认证主体签发一次性票据 */
    public String issue(JwtPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.tokenVersion() == null
                || !StringUtils.hasText(principal.username())) {
            throw new BusinessException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        Instant now = Instant.now();
        if (tickets.size() >= PRUNE_THRESHOLD) {
            pruneExpired(now);
        }
        enforcePerUserCap(principal.userId());
        if (tickets.size() >= MAX_CAPACITY) {
            pruneExpired(now);
            if (tickets.size() >= MAX_CAPACITY) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "连接票据服务繁忙，请稍后重试");
            }
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new TicketEntry(
                principal.userId(), principal.username(), principal.tokenVersion(), now.plus(TTL)));
        return ticket;
    }

    /** 原子消费票据：不存在或过期均返回 empty */
    public Optional<TicketEntry> consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return Optional.empty();
        }
        TicketEntry entry = tickets.remove(ticket);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /** 账号会话失效时清理未消费的旧票据 */
    @EventListener
    public void onSessionInvalidated(SessionInvalidatedEvent event) {
        if (event != null && event.userId() != null) {
            tickets.entrySet().removeIf(e -> event.userId().equals(e.getValue().userId()));
        }
    }

    private void pruneExpired(Instant now) {
        tickets.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private void enforcePerUserCap(Long userId) {
        var userEntries = tickets.entrySet().stream()
                .filter(e -> userId.equals(e.getValue().userId()))
                .toList();
        if (userEntries.size() < MAX_PER_USER) {
            return;
        }
        int toRemove = userEntries.size() - MAX_PER_USER + 1;
        userEntries.stream()
                .sorted(Comparator.comparing(e -> e.getValue().expiresAt()))
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .forEach(tickets::remove);
    }
}
