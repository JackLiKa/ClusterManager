package com.example.clustermanager.core.model;

import java.util.List;

/**
 * 消息模板——預定義的消息負載模板，支持占位符替換。
 *
 * <p>用於消息模擬時生成多樣化的消息體。占位符在發送時被替換為實際值：
 * <ul>
 *   <li>{@code {index}} — 消息序號（0-based）</li>
 *   <li>{@code {timestamp}} — ISO-8601 時間戳</li>
 *   <li>{@code {uuid}} — 隨機 UUID</li>
 *   <li>{@code {random}} — 1-1000 隨機整數</li>
 *   <li>{@code {topic}} — 當前 topic 名稱</li>
 * </ul>
 *
 * @param id        模板唯一標識（如 "json-order"）
 * @param name      模板顯示名稱（如 "JSON 訂單事件"）
 * @param template  模板內容（含占位符）
 * @param description 模板描述
 */
public record MessageTemplate(
        String id,
        String name,
        String template,
        String description
) {
    /** 內置模板列表 */
    public static final List<MessageTemplate> BUILT_IN = List.of(
            new MessageTemplate(
                    "json-order",
                    "JSON 訂單事件",
                    """
                    {"orderId":"{uuid}","productId":"P{index}","quantity":{random},"status":"CREATED","timestamp":"{timestamp}"}""",
                    "模擬電商訂單創建事件，含訂單 ID、商品 ID、數量"
            ),
            new MessageTemplate(
                    "plain-text",
                    "純文本消息",
                    "Message #{index} sent to topic {topic} at {timestamp}",
                    "簡單的純文本消息，適合基礎測試"
            ),
            new MessageTemplate(
                    "rocketmq-event",
                    "RocketMQ 系統事件",
                    """
                    {"event":"MESSAGE_SENT","eventId":"{uuid}","topic":"{topic}","msgIndex":{index},"createdAt":"{timestamp}","payload":"payload-{random}"}""",
                    "RocketMQ 系統級事件格式，含事件 ID 和 topic 信息"
            ),
            new MessageTemplate(
                    "key-value",
                    "鍵值對格式",
                    "key_{index}=value_{random}&topic={topic}&ts={timestamp}",
                    "URL 編碼風格的鍵值對，適合日誌類消息"
            ),
            new MessageTemplate(
                    "json-user",
                    "JSON 用戶行為",
                    """
                    {"userId":"U{random}","action":"click","page":"/product/{index}","sessionId":"{uuid}","timestamp":"{timestamp}"}""",
                    "模擬用戶行為埋點數據，含用戶 ID、動作、頁面"
            ),
            new MessageTemplate(
                    "empty",
                    "空模板（自定義）",
                    "",
                    "不使用預定義模板，用戶自行輸入消息內容"
            )
    );
}
