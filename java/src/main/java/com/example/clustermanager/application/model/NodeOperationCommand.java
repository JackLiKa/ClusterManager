package com.example.clustermanager.application.model;

import com.example.clustermanager.core.model.OperationType;

/**
 * 節點操作命令 record —— 應用層用於編排「節點生命周期操作」用例的不可變命令對象。
 *
 * <p>由 api 層控制器從請求參數構造，傳入
 * {@link com.example.clustermanager.application.service.ClusterFacadeService#operateNode(NodeOperationCommand)}，
 * 門面解析 provider 後委託執行節點啟停等操作。
 *
 * <p>與各層關係：
 * <ul>
 *   <li>api 層：從 path/query 構造本命令。</li>
 *   <li>application 層：門面路由到對應 provider。</li>
 *   <li>core 層：{@link OperationType} 為領域枚舉，定義可執行的操作類型。</li>
 *   <li>infrastructure 層：PSEUDO provider 調用嵌入式 runtime 啟停節點；REAL provider 記錄審計日誌。</li>
 * </ul>
 *
 * @param cluster       目標集群選擇，用於解析 provider 適配器
 * @param nodeId        待操作節點的唯一標識
 * @param operationType 操作類型（如 START、STOP），見 {@link OperationType}
 */
public record NodeOperationCommand(
        ClusterSelection cluster,
        String nodeId,
        OperationType operationType
) {
}
