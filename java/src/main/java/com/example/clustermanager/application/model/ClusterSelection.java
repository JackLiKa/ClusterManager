package com.example.clustermanager.application.model;

import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.MiddlewareType;

/**
 * 集群選擇 record —— 應用層用來定位目標集群的不可變值對象。
 *
 * <p>在六邊形架構中，application 層通過此對象攜帶 {@code clusterId}、{@code mode}、
 * {@code middleware} 三元信息，交由 {@link com.example.clustermanager.application.service.ClusterProviderRegistry}
 * 按 {@code (mode, middleware)} 二元組解析出對應的 {@code IClusterProvider} 適配器，
 * 再以 {@link #toClusterRef()} 轉換為 core 層的 {@link ClusterRef} 傳入 provider 執行具體操作。
 *
 * <p>與各層關係：
 * <ul>
 *   <li>api 層控制器從請求參數構造 {@code ClusterSelection}，傳入 application 層門面。</li>
 *   <li>application 層用它解析 provider 並轉換為 core 層 {@code ClusterRef}。</li>
 *   <li>core 層 {@code ClusterRef} 是 provider 執行操作時的集群引用。</li>
 *   <li>infrastructure 層 provider 不直接接收此對象，而是接收轉換後的 {@code ClusterRef}。</li>
 * </ul>
 *
 * @param clusterId  集群唯一標識（如 {@code "local-lab"}、{@code "rocketmq-demo"}），用於路由與日誌關聯
 * @param mode       集群模式（{@link ClusterMode#PSEUDO} 偽集群 / {@link ClusterMode#REAL} 真實集群），
 *                   與 {@code middleware} 共同決定選用哪個 provider 適配器
 * @param middleware 中間件類型（當前固定為 {@link MiddlewareType#ROCKETMQ}），
 *                   與 {@code mode} 共同決定選用哪個 provider 適配器
 */
public record ClusterSelection(
        String clusterId,
        ClusterMode mode,
        MiddlewareType middleware
) {

    /**
     * 將應用層的集群選擇轉換為 core 層的集群引用。
     *
     * <p>application 層在調用 provider 前調用此方法，把 {@code clusterId/mode/middleware}
     * 封裝為 core 層 {@link ClusterRef}，使 infrastructure 層適配器只依賴 core 模型，
     * 保持依賴方向 {@code application → core ← infrastructure}。
     *
     * @return 包含相同 {@code clusterId}、{@code mode}、{@code middleware} 的 {@link ClusterRef} 實例
     */
    public ClusterRef toClusterRef() {
        return new ClusterRef(clusterId, mode, middleware);
    }
}
