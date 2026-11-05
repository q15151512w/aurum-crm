package com.aurum.crm.module.crm.ai.session;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.aurum.crm.module.crm.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AI 会话在 Redis 里的存储。
 *
 * <h3>为什么用 Redis 而不是 MySQL</h3>
 * 会话是**有寿命的临时数据**：7 天没碰就该消失，用户「清空会话」也是删掉即可。
 * 用 Redis 的 TTL + 原生删除，比在 MySQL 里再维护一套「过期清理」简单得多。
 * 代价是 Redis 重启会丢 —— 对聊天记录来说可以接受。
 *
 * <h3>键设计</h3>
 * <pre>
 *   ai:chat:msg:{userId}:{sessionId}   LIST  每条消息一个 JSON 元素（RPUSH 追加，LTRIM 只留最近 N 条）
 *   ai:chat:index:{userId}             HASH  field=sessionId, value=会话摘要 JSON
 * </pre>
 *
 * <p><b>为什么 index 用 HASH 而不是 ZSET</b>：ZSET 存不了摘要，列会话时要对每个 id
 * 再查一次（N+1）。而 Redis 在你们这儿是**远程**的，单次往返 ~50ms —— 20 个会话就是
 * 1 秒。HASH 一次 {@code HGETALL} 全拿到，排序在 Java 里做。
 *
 * <p><b>userId 放在键里是安全边界</b>：sessionId 由前端传来，但键前缀永远绑死
 * 当前登录用户的 userId —— 就算有人伪造了别人的 sessionId，也只会查到自己名下
 * 那个不存在的键，拿不到别人的对话。这是刻意设计的，不要改成「只按 sessionId 查」。
 *
 * <p>值与项目其它 Redis 用法一致，统一用 {@link StringRedisTemplate}（字符串序列化），
 * 避免自定义序列化器与 Sa-Token 的互相干扰 —— 见 {@code SmsCodeServiceImpl} 的说明。
 */
@Slf4j
@Service
public class AiChatSessionStore {

    private static final String KEY_PREFIX = "ai:chat:";

    /** 标题最长保留多少字（超出加省略号） */
    private static final int TITLE_MAX = 30;

    /** sessionId 允许的最大长度，挡住异常长的键 */
    private static final int SESSION_ID_MAX = 64;

    private final StringRedisTemplate redis;
    private final AiProperties properties;

    public AiChatSessionStore(StringRedisTemplate stringRedisTemplate, AiProperties properties) {
        this.redis = stringRedisTemplate;
        this.properties = properties;
    }

    /**
     * 新建一个会话。
     *
     * @param firstQuestion 首轮提问，用来生成标题（列表里显示的那一行）
     */
    public String createSession(Long userId, String firstQuestion) {
        String sessionId = IdUtil.fastSimpleUUID();
        long now = System.currentTimeMillis();

        AiSessionSummary summary = new AiSessionSummary();
        summary.setSessionId(sessionId);
        summary.setTitle(buildTitle(firstQuestion));
        summary.setLastActive(now);
        summary.setMessageCount(0);

        String indexKey = indexKey(userId);
        redis.opsForHash().put(indexKey, sessionId, JSONUtil.toJsonStr(summary));
        redis.expire(indexKey, ttl());
        return sessionId;
    }

    /**
     * 会话是否还存在。
     *
     * <p>用于挡住**过期的 sessionId**：前端可能拿着一个 7 天前的 id 继续发消息，
     * 此时应当新建一个会话，而不是把一个已删除的会话"复活"。
     */
    public boolean exists(Long userId, String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return false;
        }
        return Boolean.TRUE.equals(redis.opsForHash().hasKey(indexKey(userId), sessionId));
    }

    /**
     * 追加一条消息。
     *
     * <p>写完后会：① 用 {@code LTRIM} 只保留最近 N 条；② 刷新两个键的 TTL；
     * ③ 更新 index 里的摘要（最后活跃时间 + 条数）。
     */
    public void appendMessage(Long userId, String sessionId, AiStoredMessage message) {
        int max = properties.getSessionMaxMessages();
        String msgKey = msgKey(userId, sessionId);

        redis.opsForList().rightPush(msgKey, JSONUtil.toJsonStr(message));
        // 只保留最近 max 条：-max 表示"从倒数第 max 个到最后一个"
        redis.opsForList().trim(msgKey, -max, -1);
        redis.expire(msgKey, ttl());

        String indexKey = indexKey(userId);
        AiSessionSummary summary = readSummary(indexKey, sessionId);
        if (summary == null) {
            // index 里的摘要丢了（过期），但消息还在：按现状补一条，不让写入失败
            summary = new AiSessionSummary();
            summary.setSessionId(sessionId);
            summary.setTitle(buildTitle(message.getContent()));
            summary.setMessageCount(0);
        }
        summary.setLastActive(System.currentTimeMillis());
        summary.setMessageCount(summary.getMessageCount() == null ? 1 : summary.getMessageCount() + 1);
        redis.opsForHash().put(indexKey, sessionId, JSONUtil.toJsonStr(summary));
        redis.expire(indexKey, ttl());
    }

    /** 读一个会话的全部消息（最多 N 条，按时间正序）。 */
    public List<AiStoredMessage> history(Long userId, String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return List.of();
        }
        List<String> raw = redis.opsForList().range(msgKey(userId, sessionId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<AiStoredMessage> messages = new ArrayList<>(raw.size());
        for (String json : raw) {
            try {
                messages.add(JSONUtil.toBean(json, AiStoredMessage.class));
            } catch (Exception e) {
                // 单条坏了不该让整个会话打不开
                log.warn("[AI] 会话消息解析失败，已跳过。userId={}, sessionId={}", userId, sessionId);
            }
        }
        return messages;
    }

    /** 列会话，按最后活跃时间倒序。 */
    public List<AiSessionSummary> listSessions(Long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(indexKey(userId));
        List<AiSessionSummary> list = new ArrayList<>(entries.size());
        for (Object value : entries.values()) {
            try {
                list.add(JSONUtil.toBean(String.valueOf(value), AiSessionSummary.class));
            } catch (Exception e) {
                log.warn("[AI] 会话摘要解析失败，已跳过。userId={}", userId);
            }
        }
        list.sort(Comparator.comparing(
                AiSessionSummary::getLastActive,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    /** 删会话（消息 + 索引一起）。 */
    public void deleteSession(Long userId, String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return;
        }
        redis.delete(msgKey(userId, sessionId));
        redis.opsForHash().delete(indexKey(userId), sessionId);
    }

    /**
     * 会话标题由首轮提问截断而来。
     *
     * <p>不做「让模型生成标题」：那会多一次调用（多花钱、多一次失败可能），
     * 而绝大多数情况下用户第一句话本身就足够认出这是哪次对话。
     */
    private String buildTitle(String firstQuestion) {
        if (!StringUtils.hasText(firstQuestion)) {
            return "新会话";
        }
        String text = firstQuestion.trim().replaceAll("\\s+", " ");
        return text.length() <= TITLE_MAX ? text : text.substring(0, TITLE_MAX) + "…";
    }

    /**
     * sessionId 来自前端，直接当键的一部分用会开出「超长键」这种坑。
     * 只允许字母数字和连字符 —— 我们自己生成的是 UUID，天然满足。
     */
    private boolean isValidSessionId(String sessionId) {
        return StringUtils.hasText(sessionId)
                && sessionId.length() <= SESSION_ID_MAX
                && sessionId.matches("[A-Za-z0-9-]+");
    }

    private AiSessionSummary readSummary(String indexKey, String sessionId) {
        Object raw = redis.opsForHash().get(indexKey, sessionId);
        if (raw == null) {
            return null;
        }
        try {
            return JSONUtil.toBean(String.valueOf(raw), AiSessionSummary.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Duration ttl() {
        return Duration.ofDays(properties.getSessionTtlDays());
    }

    private String msgKey(Long userId, String sessionId) {
        return KEY_PREFIX + "msg:" + userId + ":" + sessionId;
    }

    private String indexKey(Long userId) {
        return KEY_PREFIX + "index:" + userId;
    }
}
