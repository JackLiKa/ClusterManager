package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.LogEntry;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * 共享審計日誌組件——從 PseudoClusterProvider 和 RocketMqClusterProvider 提取的公共邏輯。
 *
 * <p>內存存儲，上限 200 條，不持久化。使用 {@link ConcurrentLinkedDeque} 保證線程安全，
 * 多個 Provider 共享同一個 Spring 單例實例。
 *
 * <p>被以下組件依賴：
 * <ul>
 *   <li>{@link PseudoClusterProvider}——記錄節點操作、服務註冊/刪除</li>
 *   <li>{@code RocketMqClusterProvider}——記錄真實集群的操作日誌</li>
 * </ul>
 */
@Component
public class AuditLog {

    /** 最大日誌條數上限，超出後從尾部（最舊）移除 */
    private static final int MAX_ENTRIES = 200;

    /** 日誌存儲——線程安全的雙端隊列，新日誌插入頭部（最新），超出上限時從尾部移除 */
    private final Deque<LogEntry> entries = new ConcurrentLinkedDeque<>();

    /**
     * 追加一條審計日誌到隊列頭部。若隊列超過上限，從尾部移除最舊的條目。
     *
     * @param nodeId  關聯的節點 ID
     * @param level   日誌級別（INFO / WARN / ERROR 等）
     * @param message 日誌內容
     */
    public void append(String nodeId, String level, String message) {
        entries.addFirst(new LogEntry(Instant.now(), nodeId, level, message));
        while (entries.size() > MAX_ENTRIES) {
            entries.pollLast();
        }
    }

    /**
     * 查詢審計日誌，可按 nodeId 過濾。
     *
     * @param nodeId 節點 ID 過濾條件（null 表示返回所有節點的日誌）
     * @param limit  最大返回條數（至少 1）
     * @return 按時間倒序排列的日誌列表
     */
    public java.util.List<LogEntry> query(String nodeId, int limit) {
        return entries.stream()
                .filter(entry -> nodeId == null || entry.nodeId().equals(nodeId))
                .limit(Math.max(1, limit))
                .toList();
    }
}
