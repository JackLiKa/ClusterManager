package com.example.clustermanager.application.model;

import com.example.clustermanager.core.model.ServiceRegistration;

/**
 * 服務登記命令 record —— 應用層用於編排「服務登記」用例的不可變命令對象。
 *
 * <p>由 api 層控制器從請求體構造，傳入
 * {@link com.example.clustermanager.application.service.ClusterFacadeService#registerService(ServiceRegistrationCommand)}，
 * 門面解析 provider 後委託執行服務登記，將業務服務與集群節點關聯。
 *
 * <p>與各層關係：
 * <ul>
 *   <li>api 層：DTO 校驗後映射為本命令。</li>
 *   <li>application 層：門面路由到對應 provider。</li>
 *   <li>core 層：{@link ServiceRegistration} 為領域模型，描述服務與節點的綁定關係。</li>
 *   <li>infrastructure 層：provider 將登記信息合併進 topology 並記錄審計日誌。</li>
 * </ul>
 *
 * @param cluster 目標集群選擇，用於解析 provider 適配器
 * @param service 服務登記信息（服務名、綁定節點 id 等），見 {@link ServiceRegistration}
 */
public record ServiceRegistrationCommand(
        ClusterSelection cluster,
        ServiceRegistration service
) {
}
