package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.model.ServiceRegistrationCommand;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.core.model.ServiceRegistration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 门面冒烟测试——验证 {@link ClusterFacadeService} 作为统一编排入口的基本可用性。
 *
 * <p>覆盖的业务场景：
 * <ul>
 *   <li>伪集群（PSEUDO）拓扑加载：通过门面加载种子化默认节点，验证节点非空且 VIP 已分配。</li>
 *   <li>真实集群（REAL）手工节点合并：手工登记一个服务后，拓扑应包含该节点并自动标注来源标签。</li>
 * </ul>
 *
 * <p>测试策略：Spring Boot Test 集成测试——启动完整应用上下文，通过构造器注入
 * {@link ClusterFacadeService}，端到端验证门面编排逻辑。使用 {@code @DirtiesContext}
 * 确保每个测试方法执行后 Spring 上下文被销毁重建，避免内存状态（如审计日志、节点注册表）在测试间互相污染。
 */
@SpringBootTest // 启动完整 Spring Boot 应用上下文，加载所有 Bean
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // 每个测试方法执行后销毁上下文，防止内存状态泄漏
class ClusterFacadeSmokeTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    /**
     * 验证通过统一门面加载伪集群拓扑的基本路径。
     *
     * <p>选择 local-lab 伪集群，调用 {@link ClusterFacadeService#loadTopology} 加载拓扑，
     * 预期：节点列表非空（种子化默认节点已注入），活跃 VIP 非空（CIDR 地址池已分配虚拟 IP）。
     */
    @Test
    void shouldLoadPseudoTopologyThroughUnifiedFacade() {
        var topology = clusterFacadeService.loadTopology(new ClusterSelection(
                "local-lab",
                ClusterMode.PSEUDO,
                MiddlewareType.ROCKETMQ
        ));

        assertThat(topology.nodes()).isNotEmpty(); // 种子化节点应已注入，节点列表不能为空
        assertThat(topology.activeVip()).isNotBlank(); // VIP 应由 CIDR 地址池分配，不能为空白
    }

    /**
     * 验证真实集群手工登记的服务节点能合并进统一拓扑视图。
     *
     * <p>选择 rocketmq-demo 真实集群，手工登记一个 proxy 类型服务，然后加载拓扑。
     * 预期：拓扑中包含该手工节点，且其 labels 中 source 被自动标记为 "manual"
     * （表示该节点为手工登记而非自动发现）。
     */
    @Test
    void shouldIncludeManualRealClusterServiceInUnifiedTopology() {
        ClusterSelection cluster = new ClusterSelection(
                "rocketmq-demo",
                ClusterMode.REAL,
                MiddlewareType.ROCKETMQ
        );

        clusterFacadeService.registerService(new ServiceRegistrationCommand(
                cluster,
                new ServiceRegistration(
                        "manual-rmq-proxy-01",
                        "Manual Proxy 01",
                        "proxy",
                        "proxy-01.local",
                        "10.10.0.80",
                        8081,
                        Map.of("source", "test") // 登记时传入的原始标签
                )
        ));

        var topology = clusterFacadeService.loadTopology(cluster);

        assertThat(topology.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo("manual-rmq-proxy-01"); // 确认手工节点出现在拓扑中
            assertThat(node.labels()).containsEntry("source", "manual"); // source 应被门面覆写为 "manual"，标识手工登记来源
        });
    }
}
