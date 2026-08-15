package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clustermanager.core.model.NodeMetrics;
import com.example.clustermanager.core.model.NodeStatus;
import com.example.clustermanager.infrastructure.pseudo.PseudoClusterProperties;
import com.example.clustermanager.infrastructure.pseudo.node.ManagedNode;
import com.example.clustermanager.infrastructure.pseudo.node.NodeKind;
import com.example.clustermanager.infrastructure.pseudo.node.NodeRegistry;
import com.example.clustermanager.infrastructure.pseudo.node.NodeRole;
import com.example.clustermanager.infrastructure.pseudo.runtime.EmbeddedRocketMqRuntime;
import com.example.clustermanager.infrastructure.pseudo.runtime.PortPool;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 嵌入式 RocketMQ 運行時指標測試——驗證 {@link EmbeddedRocketMqRuntime#metrics} 的真實指標採集。
 *
 * <p>覆蓋的業務場景：
 * <ul>
 *   <li><b>常規 CI（預設啟用）</b>：未運行節點返回全零指標。不需要啟動嵌入式 RocketMQ。</li>
 *   <li><b>整合測試（需 -DrunEmbeddedRocketMqMetrics=true）</b>：啟動嵌入式 NameServer，
 *       驗證 metrics() 返回真實 JVM CPU/內存值（非硬編碼 15.0/35.0），且 NameServer 網絡 IO 為 0。</li>
 * </ul>
 *
 * <p>整合測試預設禁用，原因同 {@link EmbeddedRocketMqSpikeTest}——嵌入式 RocketMQ 啟動較重
 * （端口、磁盤存儲、Netty 線程），不應納入常規 CI。
 */
class EmbeddedRocketMqRuntimeMetricsTest {

    /** 測試用工作目錄——每個測試方法獨立的臨時目錄 */
    private Path workDir;
    private PortPool portPool;
    private NodeRegistry nodeRegistry;
    private EmbeddedRocketMqRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        workDir = Files.createTempDirectory("embedded-mq-metrics-test");
        portPool = new PortPool();
        nodeRegistry = new NodeRegistry();
        runtime = new EmbeddedRocketMqRuntime(
                portPool,
                nodeRegistry,
                new PseudoClusterProperties("local-lab", null, null, workDir.toString(), false, true, null)
        );
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.shutdown();
        }
        // 清理臨時目錄（best-effort，不阻塞測試）
        try {
            if (workDir != null && Files.exists(workDir)) {
                try (var paths = Files.walk(workDir)) {
                    paths.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) { }
                            });
                }
            }
        } catch (Exception ignored) { }
    }

    /**
     * 常規 CI 測試：未運行的節點應返回全零指標。
     *
     * <p>驗證 {@link EmbeddedRocketMqRuntime#metrics} 對不在 runningNodes 中的節點 ID
     * 返回 {@code NodeMetrics(nodeId, 0, 0, 0, 0)}。
     */
    @Test
    void shouldReturnZeroMetricsForNonRunningNode() {
        NodeMetrics metrics = runtime.metrics("nonexistent-node");

        assertThat(metrics.nodeId()).isEqualTo("nonexistent-node");
        assertThat(metrics.cpuUsage()).isZero();
        assertThat(metrics.memoryUsage()).isZero();
        assertThat(metrics.networkInBytesPerSecond()).isZero();
        assertThat(metrics.networkOutBytesPerSecond()).isZero();
    }

    /**
     * 常規 CI 測試：已註冊但未啟動的節點也應返回全零指標。
     *
     * <p>節點存在於 NodeRegistry 但未調用 start()，runtime.metrics() 應返回零值。
     */
    @Test
    void shouldReturnZeroMetricsForRegisteredButStoppedNode() {
        ManagedNode node = new ManagedNode(
                "rmq-ns-01", "NameServer-01", "ns01.local",
                "nameserver", 9876, false, NodeKind.VIRTUAL,
                NodeStatus.STOPPED, null
        );
        nodeRegistry.register(node);

        NodeMetrics metrics = runtime.metrics("rmq-ns-01");

        assertThat(metrics.cpuUsage()).isZero();
        assertThat(metrics.memoryUsage()).isZero();
        assertThat(metrics.networkInBytesPerSecond()).isZero();
        assertThat(metrics.networkOutBytesPerSecond()).isZero();
    }

    /**
     * 整合測試（需 -DrunEmbeddedRocketMqMetrics=true）：啟動嵌入式 NameServer 後，
     * metrics() 應返回真實 JVM CPU/內存值，而非硬編碼估算值（15.0/35.0/4096）。
     *
     * <p>驗證目標：
     * <ul>
     *   <li>CPU 使用率不為硬編碼值 15.0——應為真實進程 CPU 占用率。</li>
     *   <li>內存使用率不為硬編碼值 35.0——應為真實 JVM heap 使用率。</li>
     *   <li>NameServer 無消息吞吐，網絡 IO 應為 0。</li>
     * </ul>
     */
    @Test
    @EnabledIfSystemProperty(named = "runEmbeddedRocketMqMetrics", matches = "true")
    void shouldReturnRealJvmMetricsForRunningNameServer() {
        ManagedNode node = new ManagedNode(
                "rmq-ns-01", "NameServer-01", "ns01.local",
                "nameserver", 9876, false, NodeKind.VIRTUAL,
                NodeStatus.STOPPED, null
        );
        nodeRegistry.register(node);
        runtime.start(node);
        nodeRegistry.transition(node.nodeId(), runtime.status(node.nodeId()));

        // 等待節點狀態穩定
        assertThat(runtime.status("rmq-ns-01")).isEqualTo(NodeStatus.RUNNING);

        NodeMetrics metrics = runtime.metrics("rmq-ns-01");

        // 真實 JVM CPU——不應為硬編碼 15.0
        assertThat(metrics.cpuUsage())
                .as("CPU should be real JVM process CPU, not hardcoded 15.0")
                .isNotEqualTo(15.0)
                .isGreaterThanOrEqualTo(0.0)
                .isLessThanOrEqualTo(100.0);

        // 真實 JVM heap 使用率——不應為硬編碼 35.0
        assertThat(metrics.memoryUsage())
                .as("Memory should be real JVM heap usage, not hardcoded 35.0")
                .isNotEqualTo(35.0)
                .isGreaterThan(0.0)  // JVM 已啟動，heap 必有使用
                .isLessThanOrEqualTo(100.0);

        // NameServer 無消息吞吐
        assertThat(metrics.networkInBytesPerSecond())
                .as("NameServer should have zero put throughput")
                .isZero();
        assertThat(metrics.networkOutBytesPerSecond())
                .as("NameServer should have zero get throughput")
                .isZero();
    }
}
