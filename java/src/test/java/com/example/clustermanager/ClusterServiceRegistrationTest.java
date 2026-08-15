package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.model.ServiceRegistrationCommand;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.core.model.ServiceRegistration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 服务登记集成测试——验证通过 {@link ClusterFacadeService} 进行服务登记、删除、
 * 审计日志记录。
 *
 * <p>覆盖的业务场景：
 * <ul>
 *   <li>伪集群（PSEUDO）服务的登记与删除：登记后拓扑包含新节点，删除后节点消失，审计日志记录两条操作。</li>
 *   <li>真实集群（REAL）手工服务的审计日志保留：登记再删除后，审计日志中仍可查到 registered 和 deleted 记录。</li>
 *   <li>HOST 绑定伪集群服务：以 localhost/127.0.0.1 登记的节点不分配 TAP 虚拟 IP，VIP 直接使用 host:port 形式。</li>
 * </ul>
 *
 * <p>测试策略：Spring Boot Test 集成测试——启动完整应用上下文，通过门面服务端到端验证
 * 服务登记全链路。使用 {@code @DirtiesContext} 确保每个测试方法后上下文重建，
 * 避免内存中的节点注册表和审计日志在测试间互相干扰。
 *
 * <p>注意：原 {@code shouldExposeBuiltSpaIndexInClasspath} 测试已移除——
 * 前後端分離重構後，後端不再服務 SPA 靜態文件，前端由獨立的 Next.js 應用提供。
 */
@SpringBootTest // 启动完整 Spring Boot 应用上下文
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // 每个测试方法后销毁上下文，隔离内存状态（节点注册表、审计日志）
class ClusterServiceRegistrationTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    /**
     * 验证伪集群服务的登记与删除全流程，包括审计日志记录。
     *
     * <p>在 local-lab 伪集群中登记一个 broker-master 节点，验证拓扑中出现该节点且 VIP 正确；
     * 然后删除该节点，验证拓扑中不再包含它；最后检查审计日志中同时存在
     * "registered" 和 "deleted" 两条记录。
     */
    @Test
    void shouldRegisterAndDeletePseudoService() {
        ClusterSelection cluster = new ClusterSelection("local-lab", ClusterMode.PSEUDO, MiddlewareType.ROCKETMQ);

        clusterFacadeService.registerService(new ServiceRegistrationCommand(
                cluster,
                new ServiceRegistration(
                        "rmq-broker-x-02",
                        "Broker-X-02",
                        "broker-master",
                        "broker-x-02.local",
                        "10.77.0.40",
                        19941,
                        Map.of("source", "integration-test")
                )
        ));

        var topologyAfterAdd = clusterFacadeService.loadTopology(cluster);
        assertThat(topologyAfterAdd.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo("rmq-broker-x-02"); // 新登记的节点应出现在拓扑中
            assertThat(node.virtualIp()).isEqualTo("10.77.0.40"); // VIP 应为登记时指定的虚拟 IP
        });

        clusterFacadeService.deleteService(cluster, "rmq-broker-x-02");

        var topologyAfterDelete = clusterFacadeService.loadTopology(cluster);
        assertThat(topologyAfterDelete.nodes()).noneSatisfy(node -> assertThat(node.nodeId()).isEqualTo("rmq-broker-x-02")); // 删除后拓扑中不应再有该节点

        var logs = clusterFacadeService.loadLogs(cluster, null, 20);
        assertThat(logs)
                .extracting(LogEntry::message)
                .anyMatch(message -> message.contains("registered")) // 审计日志应记录登记操作
                .anyMatch(message -> message.contains("deleted"));   // 审计日志应记录删除操作
    }

    /**
     * 验证真实集群手工服务的审计日志在删除后仍然保留。
     *
     * <p>在 rocketmq-demo 真实集群中登记再删除一个手工节点，
     * 验证审计日志中同时存在 "registered" 和 "deleted" 记录。
     * 这确保真实集群的 AuditLog 在删除操作后不会丢失历史记录。
     */
    @Test
    void shouldRetainAuditLogsForManualRocketMqServices() {
        ClusterSelection cluster = new ClusterSelection("rocketmq-demo", ClusterMode.REAL, MiddlewareType.ROCKETMQ);

        clusterFacadeService.registerService(new ServiceRegistrationCommand(
                cluster,
                new ServiceRegistration(
                        "rmq-real-manual-01",
                        "RocketMQ Manual 01",
                        "nameserver",
                        "mq-real-01.local",
                        "192.168.50.78",
                        9876,
                        Map.of("source", "integration-test")
                )
        ));

        clusterFacadeService.deleteService(cluster, "rmq-real-manual-01");

        var logs = clusterFacadeService.loadLogs(cluster, null, 20);
        assertThat(logs)
                .extracting(LogEntry::message)
                .anyMatch(message -> message.contains("registered")) // 登记操作应被审计
                .anyMatch(message -> message.contains("deleted"));   // 删除操作应被审计
    }

    /**
     * 验证以 HOST 模式登记的伪集群服务不分配 TAP 虚拟 IP，而是直接使用 host:port 作为 VIP。
     *
     * <p>当节点 hostname 为 localhost、IP 为 127.0.0.1 且 labels 中 nodeKind=HOST 时，
     * 系统识别为宿主机绑定节点，跳过 TAP 虚拟网络分配。
     * VIP 应格式化为 "127.0.0.1:10911"（IP:port 拼接），而非从 CIDR 池分配的虚拟 IP。
     */
    @Test
    void shouldRegisterHostBoundPseudoServiceWithoutTapRuntime() {
        ClusterSelection cluster = new ClusterSelection("local-lab", ClusterMode.PSEUDO, MiddlewareType.ROCKETMQ);

        clusterFacadeService.registerService(new ServiceRegistrationCommand(
                cluster,
                new ServiceRegistration(
                        "host-rmq-broker-01",
                        "Host Broker 01",
                        "broker-master",
                        "localhost",
                        "127.0.0.1",
                        10911,
                        Map.of("source", "integration-test", "nodeKind", "HOST") // nodeKind=HOST 标识宿主机绑定节点
                )
        ));

        var topology = clusterFacadeService.loadTopology(cluster);
        assertThat(topology.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo("host-rmq-broker-01"); // HOST 节点应出现在拓扑中
            assertThat(node.virtualIp()).isEqualTo("127.0.0.1:10911"); // HOST 节点 VIP 为 IP:port 格式，不走 TAP 分配
            assertThat(node.labels()).containsEntry("nodeKind", "HOST"); // nodeKind 标签应保留
        });
    }
}
