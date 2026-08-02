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

/**
 * 针对本次修复行为的验证测试：
 * - P0: 无 HOST NameServer 时消息模拟返回失败结果而非抛 IllegalStateException
 * - P1-B: 真实集群手工节点带 nodeKind=HOST 标签
 * - P3-H: 真实集群 port 非法时抛 IllegalArgumentException
 */
@SpringBootTest
class FixesVerificationTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    @Test
    void shouldReturnFailedDeliveriesWhenNoHostNameServerInPseudoMode() {
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
                        Map.of("source", "fix-verification", "nodeKind", "HOST")
                )
        ));

        var result = clusterFacadeService.simulateMessages(new MessageSimulationCommand(
                cluster,
                "TopicFixP0",
                "fix-p0-group",
                2,
                "{\"hello\":\"fix\"}",
                "fix-host-broker-no-ns",
                List.of("fix-host-broker-no-ns"),
                Map.of("source", "fix-verification")
        ));

        // P0 修复: 失败结果按 consumer 数量生成（每个 consumer 一条提示），而非按 messageCount
        assertThat(result.deliveries()).hasSize(1);
        assertThat(result.deliveries()).allSatisfy(delivery -> {
            assertThat(delivery.success()).isFalse();
            assertThat(delivery.detail()).contains("no HOST nameserver registered");
        });

        // 清理
        clusterFacadeService.deleteService(cluster, "fix-host-broker-no-ns");
    }

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
                        Map.of("source", "fix-verification")
                )
        ));

        var topology = clusterFacadeService.loadTopology(cluster);
        assertThat(topology.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo("fix-real-nodekind-01");
            assertThat(node.labels()).containsEntry("nodeKind", "HOST");
        });

        // 清理
        clusterFacadeService.deleteService(cluster, "fix-real-nodekind-01");
    }

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
                        0,
                        Map.of("source", "fix-verification")
                )
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Port must be between 1 and 65535");
    }
}
