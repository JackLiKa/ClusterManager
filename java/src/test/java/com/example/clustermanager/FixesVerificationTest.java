package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.model.MessageSimulationCommand;
import com.example.clustermanager.application.model.ServiceRegistrationCommand;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.core.model.ServiceRegistration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 针对本次修复行为的验证测试：
 * - P0: 无 HOST NameServer 时消息模拟返回失败结果而非抛 IllegalStateException
 * - P1-B: 真实集群手工节点带 nodeKind=HOST 标签
 * - P3-H: 真实集群 port 非法时抛 IllegalArgumentException
 *
 * <p>覆盖的业务场景与修复标记：
 * <ul>
 *   <li><b>P0 修复</b>：伪集群中仅注册 HOST Broker 而无 HOST NameServer 时，
 *       消息模拟应返回包含失败详情的 DeliveryResult，而非抛出 IllegalStateException 导致 500 错误。
 *       失败结果按 consumer 数量生成（每个 consumer 一条提示），而非按 messageCount 生成。</li>
 *   <li><b>P1-B 修复</b>：真实集群手工登记的节点应自动附加 nodeKind=HOST 标签，
 *       以区分手工登记节点与自动发现节点。</li>
 *   <li><b>P3-H 修复</b>：真实集群服务登记时，port=0（或超出 1-65535 范围）应抛出
 *       IllegalArgumentException，被 ApiExceptionHandler 映射为 400 Bad Request，
 *       而非静默接受非法端口。</li>
 * </ul>
 *
 * <p>测试策略：Spring Boot Test 集成测试——启动完整应用上下文，通过门面服务验证修复行为。
 * 使用 {@code @DirtiesContext} 确保每个测试方法后上下文重建，避免内存状态在测试间互相干扰。
 */
@SpringBootTest // 启动完整 Spring Boot 应用上下文
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // 每个测试方法后销毁上下文，隔离内存状态
class FixesVerificationTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    @Autowired
    private com.example.clustermanager.infrastructure.rocketmq.RocketMqConnectionConfig connectionConfig;

    /**
     * P0 修复验证：伪集群中无 HOST NameServer 时，消息模拟应返回失败结果而非抛异常。
     *
     * <p>场景：在 local-lab 伪集群中仅注册一个 HOST Broker（nodeKind=HOST），
     * 不注册任何 HOST NameServer。然后调用消息模拟，指定该 Broker 作为发送目标。
     *
     * <p>修复前行为：抛出 IllegalStateException（"no HOST nameserver registered"），
     * 被 ApiExceptionHandler 映射为 409 Conflict，前端无法获得有意义的失败信息。
     *
     * <p>修复后预期：
     * <ul>
     *   <li>返回的 deliveries 数量为 1（按 consumer 数量生成，而非按 messageCount=2 生成）。</li>
     *   <li>每条 delivery 的 success=false。</li>
     *   <li>每条 delivery 的 detail 包含 "no HOST nameserver registered" 提示信息。</li>
     * </ul>
     */
    @Test
    void shouldReturnFailedDeliveriesWhenNoHostNameServerInPseudoMode() {
        // 重置運行時連接配置，確保無後備 NameServer 地址干擾 P0 測試
        connectionConfig.setNameServers(java.util.List.of());

        // P0 修复验证: 仅注册一个 HOST Broker（无 HOST NameServer），消息模拟应返回失败结果而非抛异常
        ClusterSelection cluster = new ClusterSelection("local-lab", ClusterMode.PSEUDO, MiddlewareType.ROCKETMQ);

        clusterFacadeService.registerService(new ServiceRegistrationCommand(
                cluster,
                new ServiceRegistration(
                        "fix-host-broker-no-ns",
                        "Host Broker Without NS",
                        "broker-master",
                        "localhost",
                        "127.0.0.1",
                        10912,
                        Map.of("source", "fix-verification", "nodeKind", "HOST") // nodeKind=HOST 标识宿主机绑定节点
                )
        ));

        var result = clusterFacadeService.simulateMessages(new MessageSimulationCommand(
                cluster,
                "TopicFixP0",
                "fix-p0-group",
                2, // messageCount=2，但失败时不应按此数量生成 deliveries
                "{\"hello\":\"fix\"}",
                "fix-host-broker-no-ns",
                List.of("fix-host-broker-no-ns"), // 1 个 consumer
                Map.of("source", "fix-verification")
        ));

        // P0 修复: 失败结果按 consumer 数量生成（每个 consumer 一条提示），而非按 messageCount
        assertThat(result.deliveries()).hasSize(1); // deliveries 数量应为 1（consumer 数量），而非 2（messageCount）
        assertThat(result.deliveries()).allSatisfy(delivery -> {
            assertThat(delivery.success()).isFalse(); // 所有 delivery 都应为失败状态
            assertThat(delivery.detail()).contains("no HOST nameserver registered"); // 失败原因应明确说明缺少 HOST NameServer
        });

        // 清理
        clusterFacadeService.deleteService(cluster, "fix-host-broker-no-ns");
    }

    /**
     * P1-B 修复验证：真实集群手工登记的节点应自动附加 nodeKind=HOST 标签。
     *
     * <p>场景：在 rocketmq-demo 真实集群中手工登记一个 nameserver 节点，
     * 登记时未显式传入 nodeKind 标签。
     *
     * <p>修复前行为：手工节点不带 nodeKind 标签，前端无法区分手工节点与自动发现节点。
     *
     * <p>修复后预期：拓扑中该节点的 labels 应自动包含 nodeKind=HOST，
     * 表示该节点为宿主机上的手工登记节点。
     */
    @Test
    void shouldTagRealClusterManualServiceWithHostNodeKind() {
        // P1-B 修复验证: 真实集群手工登记的节点应自动带 nodeKind=HOST 标签
        ClusterSelection cluster = new ClusterSelection("rocketmq-demo", ClusterMode.REAL, MiddlewareType.ROCKETMQ);

        clusterFacadeService.registerService(new ServiceRegistrationCommand(
                cluster,
                new ServiceRegistration(
                        "fix-real-nodekind-01",
                        "Real NodeKind 01",
                        "nameserver",
                        "mq-real-fix.local",
                        "192.168.50.79",
                        9876,
                        Map.of("source", "fix-verification") // 登记时未传入 nodeKind，应由系统自动附加
                )
        ));

        var topology = clusterFacadeService.loadTopology(cluster);
        assertThat(topology.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo("fix-real-nodekind-01"); // 确认手工节点出现在拓扑中
            assertThat(node.labels()).containsEntry("nodeKind", "HOST"); // P1-B 修复: nodeKind=HOST 应由系统自动附加
        });

        // 清理
        clusterFacadeService.deleteService(cluster, "fix-real-nodekind-01");
    }

    /**
     * P3-H 修复验证：真实集群服务登记时 port=0 应抛 IllegalArgumentException。
     *
     * <p>场景：在 rocketmq-demo 真实集群中尝试登记一个 port=0 的节点。
     *
     * <p>修复前行为：port=0 被静默接受，可能导致后续连接异常或不可预期的行为。
     *
     * <p>修复后预期：抛出 IllegalArgumentException，消息包含 "Port must be between 1 and 65535"。
     * 该异常被 ApiExceptionHandler 映射为 400 Bad Request，前端可获得明确的参数校验错误。
     */
    @Test
    void shouldRejectInvalidPortForRealClusterService() {
        // P3-H 修复验证: 真实集群 port=0 应抛 IllegalArgumentException
        ClusterSelection cluster = new ClusterSelection("rocketmq-demo", ClusterMode.REAL, MiddlewareType.ROCKETMQ);

        assertThatThrownBy(() -> clusterFacadeService.registerService(new ServiceRegistrationCommand(
                cluster,
                new ServiceRegistration(
                        "fix-bad-port-01",
                        "Bad Port 01",
                        "nameserver",
                        "mq-bad-port.local",
                        "192.168.50.80",
                        0, // port=0 为非法值，应被校验拦截
                        Map.of("source", "fix-verification")
                )
        ))).isInstanceOf(IllegalArgumentException.class) // 应抛出 IllegalArgumentException，被 ApiExceptionHandler 映射为 400
                .hasMessageContaining("Port must be between 1 and 65535"); // 错误消息应明确说明合法端口范围
    }
}
