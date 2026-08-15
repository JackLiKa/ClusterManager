package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.MiddlewareType;
import com.example.clustermanager.core.model.NodeStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 偽集群拓撲測試——驗證種子化節點結構與狀態。
 *
 * <p>覆蓋的業務場景：
 * <ul>
 *   <li>種子化默認節點：偽集群啟動後應自動注入 3 個默認節點（1 個 NameServer + 1 個 Master Broker + 1 個 Slave Broker），
 *       初始狀態均為 STOPPED（因 auto-start=false）。</li>
 *   <li>節點間鏈路：拓撲應包含節點間的連接關係，按節點順序構建（Master→Slave）。</li>
 * </ul>
 *
 * <p>原 PseudoClusterRuntimeTest 依賴進程式假運行時（PseudoNodeAgent）做消息模擬，
 * 已隨嵌入式 RocketMQ 運行時重構而移除。消息 produce/consume 的真實驗證由
 * {@link EmbeddedRocketMqSpikeTest}（-DrunEmbeddedRocketMqSpike=true）承擔。
 *
 * <p>測試策略：Spring Boot Test 集成測試——啟動完整應用上下文，通過門面服務加載拓撲，
 * 驗證種子化節點的結構、狀態和鏈路。使用 {@code @DirtiesContext} 確保每個測試方法後上下文重建。
 */
@SpringBootTest // 啟動完整 Spring Boot 應用上下文
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // 每個測試方法後銷毀上下文，隔離內存狀態
class PseudoClusterRuntimeTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    /**
     * 驗證偽集群種子化後應包含 3 個默認節點，且初始狀態均為 STOPPED。
     *
     * <p>選擇 local-lab 偽集群加載拓撲，預期：
     * <ul>
     *   <li>節點數量為 3（rmq-ns-01、rmq-broker-m-01、rmq-broker-s-01）。</li>
     *   <li>節點 ID 按固定順序排列：rmq-broker-m-01、rmq-broker-s-01、rmq-ns-01。</li>
     *   <li>所有節點初始狀態為 STOPPED——因配置 cluster.pseudo.auto-start=false，
     *       種子節點不會自動啟動（P2 修復）。</li>
     * </ul>
     */
    @Test
    void shouldSeedThreeDefaultNodesInStoppedState() {
        ClusterSelection cluster = new ClusterSelection("local-lab", ClusterMode.PSEUDO, MiddlewareType.ROCKETMQ);

        var topology = clusterFacadeService.loadTopology(cluster);

        assertThat(topology.nodes()).hasSize(3); // 種子化應注入恰好 3 個默認節點
        assertThat(topology.nodes()).extracting("nodeId")
                .containsExactly("rmq-broker-m-01", "rmq-broker-s-01", "rmq-ns-01"); // 節點 ID 和順序應固定，確保拓撲穩定
        // P2 修復: 種子節點初始狀態為 STOPPED（auto-start=false）
        assertThat(topology.nodes()).allSatisfy(node -> {
            assertThat(node.status()).isEqualTo(NodeStatus.STOPPED); // auto-start=false 時種子節點不應自動啟動
        });
    }

    /**
     * 驗證偽集群拓撲中節點間的鏈路按節點順序正確構建。
     *
     * <p>選擇 local-lab 偽集群加載拓撲，預期：
     * <ul>
     *   <li>鏈路數量為 2（3 個節點形成 2 條連接）。</li>
     *   <li>第一條鏈路的源節點為 rmq-broker-m-01（Master Broker），目標節點為 rmq-broker-s-01（Slave Broker），
     *       表示主從複製關係。</li>
     * </ul>
     */
    @Test
    void shouldBuildLinksBetweenOrderedNodes() {
        ClusterSelection cluster = new ClusterSelection("local-lab", ClusterMode.PSEUDO, MiddlewareType.ROCKETMQ);

        var topology = clusterFacadeService.loadTopology(cluster);

        assertThat(topology.links()).hasSize(2); // 3 個節點應形成 2 條鏈路
        assertThat(topology.links().get(0).sourceNodeId()).isEqualTo("rmq-broker-m-01"); // 第一條鏈路源為 Master Broker
        assertThat(topology.links().get(0).targetNodeId()).isEqualTo("rmq-broker-s-01"); // 第一條鏈路目標為 Slave Broker，表示主從關係
    }
}
