package com.example.clustermanager.api.controller;

import com.example.clustermanager.api.config.ClusterStreamProperties;
import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.infrastructure.pseudo.PseudoClusterProperties;
import com.example.clustermanager.infrastructure.rocketmq.RocketMqClusterProperties;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 定时遥测推送服务，通过 WebSocket STOMP 向前端推送集群指标与日志。
 *
 * <p>API 层角色：作为出站适配器，周期性从 {@link ClusterFacadeService} 拉取指标/日志，
 * 经 {@link SimpMessagingTemplate} 推送到 STOMP 主题
 * {@code /topic/clusters/{clusterId}/{metrics|logs}}，前端通过 useClusterStreams.ts 订阅消费。
 *
 * <p>推送目标：当前固定推送两个集群——PSEUDO（local-lab）与 REAL（rocketmq-demo），
 * 均为 ROCKETMQ 中间件。集群 ID 分别来自 {@link PseudoClusterProperties} 与
 * {@link RocketMqClusterProperties}。
 *
 * <p>Phase 0-3 优化：无 WebSocket 订阅者时跳过推送（见 {@link #publish}），
 * 避免无意义的 provider 调用——尤其 REAL 模式每次拉指标都会打 RocketMQ Admin API。
 * 订阅者检测通过 {@link SimpUserRegistry} 遍历用户会话的订阅目的地实现。
 */
@Service
public class ClusterTelemetryPushService {

    /** STOMP 消息发送模板，用于向主题推送消息。 */
    private final SimpMessagingTemplate messagingTemplate;
    /** STOMP 用户注册表，用于检测某目的地是否有活跃订阅者。 */
    private final SimpUserRegistry userRegistry;
    /** 应用层编排服务，拉取指标与日志的唯一入口。 */
    private final ClusterFacadeService clusterFacadeService;
    /** 伪集群属性，提供 PSEUDO 集群 ID。 */
    private final PseudoClusterProperties pseudoProperties;
    /** 真实 RocketMQ 集群属性，提供 REAL 集群 ID。 */
    private final RocketMqClusterProperties rocketMqClusterProperties;
    /** 遥测推送配置，提供推送周期等参数。 */
    private final ClusterStreamProperties streamProperties;

    /**
     * 构造器注入所有依赖。
     *
     * @param messagingTemplate          STOMP 消息发送模板
     * @param userRegistry               STOMP 用户注册表
     * @param clusterFacadeService       应用层编排服务
     * @param pseudoProperties           伪集群属性
     * @param rocketMqClusterProperties  真实集群属性
     * @param streamProperties           遥测推送配置
     */
    public ClusterTelemetryPushService(
            SimpMessagingTemplate messagingTemplate,
            SimpUserRegistry userRegistry,
            ClusterFacadeService clusterFacadeService,
            PseudoClusterProperties pseudoProperties,
            RocketMqClusterProperties rocketMqClusterProperties,
            ClusterStreamProperties streamProperties
    ) {
        this.messagingTemplate = messagingTemplate;
        this.userRegistry = userRegistry;
        this.clusterFacadeService = clusterFacadeService;
        this.pseudoProperties = pseudoProperties;
        this.rocketMqClusterProperties = rocketMqClusterProperties;
        this.streamProperties = streamProperties;
    }

    /**
     * 定时推送指标快照。周期由 {@code cluster.stream.publish-interval-ms} 控制（默认 5000ms）。
     * 分别推送 PSEUDO 与 REAL 两个集群的 metrics 主题。
     */
    @Scheduled(fixedRateString = "${cluster.stream.publish-interval-ms:5000}")
    public void pushSnapshots() {
        publish(selection(pseudoProperties.clusterId(), ClusterMode.PSEUDO), "metrics");
        publish(selection(rocketMqClusterProperties.clusterId(), ClusterMode.REAL), "metrics");
    }

    /**
     * 定时推送日志。周期同指标推送，但带一个周期的初始延迟，避免与指标推送同时执行。
     * 分别推送 PSEUDO 与 REAL 两个集群的 logs 主题。
     */
    @Scheduled(fixedRateString = "${cluster.stream.publish-interval-ms:5000}", initialDelayString = "${cluster.stream.publish-interval-ms:5000}")
    public void pushLogs() {
        publish(selection(pseudoProperties.clusterId(), ClusterMode.PSEUDO), "logs");
        publish(selection(rocketMqClusterProperties.clusterId(), ClusterMode.REAL), "logs");
    }

    /**
     * 向指定集群通道推送数据。Phase 0-3 优化：若该目的地无 WebSocket 订阅者则直接返回，
     * 跳过无意义的 provider 调用。
     *
     * @param selection 集群选择
     * @param channel   通道名（"metrics" 或 "logs"）
     * @throws IllegalArgumentException 当 channel 不是 metrics 或 logs 时
     */
    private void publish(ClusterSelection selection, String channel) {
        String destination = "/topic/clusters/%s/%s".formatted(selection.clusterId(), channel);
        // Phase 0 优化: 无 WebSocket 订阅者时跳过推送，避免无意义的 provider 调用（尤其 REAL 模式每次都打 Admin API）
        if (!hasSubscribers(destination)) {
            return;
        }
        Object payload = switch (channel) {
            case "metrics" -> clusterFacadeService.loadMetrics(selection);
            case "logs" -> clusterFacadeService.loadLogs(selection, null, 10);
            default -> throw new IllegalArgumentException("Unsupported stream channel: " + channel);
        };
        messagingTemplate.convertAndSend(destination, payload);
    }

    /**
     * 检测指定 STOMP 目的地是否有活跃订阅者。
     * 遍历所有用户的所有会话的所有订阅，匹配目的地字符串。
     *
     * @param destination STOMP 目的地（如 /topic/clusters/local-lab/metrics）
     * @return true 表示至少有一个订阅者
     */
    private boolean hasSubscribers(String destination) {
        return userRegistry.getUsers().stream()
                .anyMatch(user -> user.getSessions().stream()
                        .anyMatch(session -> session.getSubscriptions().stream()
                                .anyMatch(sub -> destination.equals(sub.getDestination()))));
    }

    /**
     * 构建集群选择值对象，middleware 固定为 ROCKETMQ。
     *
     * @param clusterId 集群标识
     * @param mode      集群模式
     * @return 集群选择值对象
     */
    private ClusterSelection selection(String clusterId, ClusterMode mode) {
        return new ClusterSelection(clusterId, mode, MiddlewareType.ROCKETMQ);
    }
}
