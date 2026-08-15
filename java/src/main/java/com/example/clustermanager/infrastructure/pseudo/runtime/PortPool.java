package com.example.clustermanager.infrastructure.pseudo.runtime;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 端口分配池——從操作系統動態分配空閒端口，節點停止時釋放。
 *
 * <p>使用 {@code ServerSocket(0)} 讓 OS 自動分配可用端口，確保不衝突。
 * 作為 Spring 單例 {@code @Component}，被 {@link EmbeddedRocketMqRuntime} 持有。
 *
 * <p><b>線程安全</b>：allocate/release 方法使用 {@code synchronized} 保護，
 * {@code allocated} 使用 {@code ConcurrentHashMap.newKeySet()} 支持並發讀。
 *
 * <p><b>生命週期</b>：隨應用啟動創建，端口在節點啟動時分配、停止時釋放。
 */
@Component
public class PortPool {

    /** 已分配端口集合——線程安全，用於追蹤和釋放 */
    private final Set<Integer> allocated = ConcurrentHashMap.newKeySet();

    /**
     * 分配一個空閒端口。優先嘗試 OS 自動分配（port=0），確保不衝突。
     *
     * @return 分配到的可用端口號
     * @throws IllegalStateException 無法分配端口時拋出（如系統資源耗盡）
     */
    public synchronized int allocate() {
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            allocated.add(port);
            return port;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to allocate free port", exception);
        }
    }

    /**
     * 釋放端口回池。節點停止時調用，允許端口被後續節點重用。
     *
     * @param port 待釋放的端口號
     */
    public synchronized void release(int port) {
        allocated.remove(port);
    }

    /**
     * 當前已分配端口數。
     *
     * @return 已分配端口數量
     */
    public int allocatedCount() {
        return allocated.size();
    }
}
