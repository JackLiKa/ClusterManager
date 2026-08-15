package com.example.clustermanager.infrastructure.rocketmq;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实 RocketMQ 集群配置属性 —— 绑定 {@code cluster.rocketmq.*} 前缀的配置项。
 *
 * <p>本 record 属于 infrastructure/rocketmq 层，为 REAL 模式适配器
 * {@link RocketMqClusterProvider} 提供连接真实 RocketMQ 集群所需的配置参数。
 * 通过 Spring Boot 的 {@link ConfigurationProperties} 机制自动绑定
 * {@code application.properties} 中 {@code cluster.rocketmq.*} 前缀的属性。
 *
 * <p><b>环境变量外部化</b>：以下配置项支持通过环境变量覆盖，避免将环境特定的
 * 私网 IP 和个人标识硬编码入仓：
 * <ul>
 *   <li>{@code CLUSTER_ROCKETMQ_NAME_SERVERS} —— 覆盖 {@code cluster.rocketmq.name-servers}，
 *       指定真实 NameServer 地址列表（如 {@code 192.168.50.78:9876}）</li>
 *   <li>{@code CLUSTER_ROCKETMQ_DASHBOARD_NAME} —— 覆盖 {@code cluster.rocketmq.dashboard-name}，
 *       指定 RocketMQ Dashboard 中的集群标识名称</li>
 * </ul>
 *
 * <p><b>当前状态</b>：REAL 模式暂时搁置，专注 PSEUDO 模式。此配置类保留以备
 * 恢复 REAL 模式时使用。
 *
 * @param clusterId    真实集群的逻辑 ID（默认 {@code rocketmq-demo}）
 * @param dashboardName RocketMQ Dashboard 中的集群标识名称，可通过环境变量外部化
 * @param nameServers  NameServer 地址列表（host:port 格式），可通过环境变量外部化
 */
@ConfigurationProperties(prefix = "cluster.rocketmq")
public record RocketMqClusterProperties(
        String clusterId,
        String dashboardName,
        List<String> nameServers
) {

    /**
     * 解析 Dashboard 集群名称，为空时回退到默认值 {@code "default"}。
     *
     * @return 有效的 Dashboard 集群名称
     */
    public String resolvedDashboardName() {
        return dashboardName == null || dashboardName.isBlank() ? "default" : dashboardName;
    }

    /**
     * 解析 NameServer 地址列表，为 null 时返回空列表以避免 NPE。
     *
     * @return NameServer 地址列表（可能为空，表示未配置真实集群地址）
     */
    public List<String> resolvedNameServers() {
        return nameServers == null ? List.of() : nameServers;
    }
}
