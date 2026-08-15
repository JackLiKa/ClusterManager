package com.example.clustermanager.application.service;

import com.example.clustermanager.core.model.MessageTemplate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

/**
 * 消息模板服務——提供模板列表查詢和占位符替換。
 *
 * <p>支持的占位符：
 * <ul>
 *   <li>{@code {index}} — 消息序號（0-based）</li>
 *   <li>{@code {timestamp}} — ISO-8601 時間戳</li>
 *   <li>{@code {uuid}} — 隨機 UUID</li>
 *   <li>{@code {random}} — 1-1000 隨機整數</li>
 *   <li>{@code {topic}} — 當前 topic 名稱</li>
 * </ul>
 *
 * <p>被以下組件依賴：
 * <ul>
 *   <li>{@link ClusterController} — 暴露模板列表 API</li>
 *   <li>{@link com.example.clustermanager.infrastructure.pseudo.messaging.EmbeddedMessageWorkbench} — 消息發送時渲染模板</li>
 * </ul>
 */
@Service
public class MessageTemplateService {

    /**
     * 返回所有內置消息模板。
     *
     * @return 模板列表
     */
    public List<MessageTemplate> listTemplates() {
        return MessageTemplate.BUILT_IN;
    }

    /**
     * 根據 ID 查找模板，找不到返回 null。
     *
     * @param templateId 模板 ID
     * @return 模板實例，或 null
     */
    public MessageTemplate findTemplate(String templateId) {
        return MessageTemplate.BUILT_IN.stream()
                .filter(t -> t.id().equals(templateId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 渲染消息模板——替換占位符為實際值。
     *
     * <p>若 payloadTemplate 為 null 或空，使用默認模板 {@code {"msg":"hello cluster"}}。
     *
     * @param payloadTemplate 模板字符串（可含占位符）
     * @param topic           當前 topic 名稱
     * @param index           消息序號（0-based）
     * @return 渲染後的消息體字符串
     */
    public String render(String payloadTemplate, String topic, int index) {
        String template = (payloadTemplate == null || payloadTemplate.isBlank())
                ? "{\"msg\":\"hello cluster\"}"
                : payloadTemplate;

        return template
                .replace("{index}", String.valueOf(index))
                .replace("{timestamp}", Instant.now().toString())
                .replace("{uuid}", UUID.randomUUID().toString())
                .replace("{random}", String.valueOf(ThreadLocalRandom.current().nextInt(1, 1001)))
                .replace("{topic}", topic != null ? topic : "unknown");
    }
}
