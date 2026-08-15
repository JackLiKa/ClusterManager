package com.example.clustermanager.infrastructure.pseudo.runtime;

import com.example.clustermanager.core.model.NodeStatus;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.namesrv.NamesrvController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 嵌入式 RocketMQ 節點——封裝一個 NameServer 或 Broker 控制器的生命週期。
 *
 * <p>每個節點獨立啟動/停止，狀態查詢統一為 {@link NodeStatus}。
 * 內部持有 RocketMQ 原生控制器引用（{@link NamesrvController} 或 {@link BrokerController}），
 * 供 runtime 進行 topic 創建等高級操作。
 *
 * <p><b>線程安全</b>：{@code status} 使用 {@code volatile} 保證可見性。
 * 生命週期方法（start/stop）由 {@link EmbeddedRocketMqRuntime} 的 {@code synchronized} 保護。
 *
 * <p><b>生命週期</b>：由 {@link EmbeddedRocketMqNode#nameserver} 或 {@link #broker}
 * 工廠方法創建（含 initialize），然後調用 {@link #start} 啟動，{@link #stop} 停止。
 * 停止後控制器已 shutdown，不可重用。
 *
 * <p>包級可見，僅由 {@link EmbeddedRocketMqRuntime} 持有和管理。
 */
final class EmbeddedRocketMqNode {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRocketMqNode.class);

    /** 節點規格——包含端口、角色、存儲路徑等信息 */
    private final EmbeddedNodeSpec spec;
    /** RocketMQ 原生控制器——NamesrvController 或 BrokerController，由工廠方法初始化 */
    private final Object controller;
    /** 節點當前狀態——volatile 保證跨線程可見性 */
    private volatile NodeStatus status = NodeStatus.STOPPED;

    /**
     * 私有構造器——通過工廠方法創建實例。
     *
     * @param spec       節點規格
     * @param controller 已 initialize 的 RocketMQ 控制器
     */
    private EmbeddedRocketMqNode(EmbeddedNodeSpec spec, Object controller) {
        this.spec = spec;
        this.controller = controller;
    }

    /**
     * 創建並初始化 NameServer 節點。
     *
     * <p>直接構造 {@link NamesrvController} 繞過 BrokerStartup 的 ROCKETMQ_HOME 依賴（Spike 修復）。
     *
     * @param spec 節點規格（角色必須為 NAMESERVER）
     * @return 已 initialize 的 NameServer 節點
     * @throws IllegalStateException 初始化失敗時拋出
     */
    static EmbeddedRocketMqNode nameserver(EmbeddedNodeSpec spec) {
        try {
            NamesrvController controller = new NamesrvController(
                    NamesrvConfigBuilder.buildNamesrvConfig(),
                    NamesrvConfigBuilder.buildNettyServerConfig(spec)
            );
            controller.initialize();
            return new EmbeddedRocketMqNode(spec, controller);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize embedded NameServer: " + spec.nodeId(), exception);
        }
    }

    /**
     * 創建並初始化 Broker 節點。
     *
     * <p>使用四參構造器構造 {@link BrokerController}（BrokerConfig、NettyServerConfig、
     * NettyClientConfig、MessageStoreConfig），繞過 BrokerStartup（Spike 修復）。
     *
     * @param spec 節點規格（角色必須為 BROKER_MASTER 或 BROKER_SLAVE）
     * @return 已 initialize 的 Broker 節點
     * @throws IllegalStateException 初始化失敗時拋出
     */
    static EmbeddedRocketMqNode broker(EmbeddedNodeSpec spec) {
        try {
            BrokerController controller = new BrokerController(
                    BrokerConfigBuilder.buildBrokerConfig(spec),
                    BrokerConfigBuilder.buildNettyServerConfig(spec),
                    BrokerConfigBuilder.buildNettyClientConfig(),
                    BrokerConfigBuilder.buildMessageStoreConfig(spec)
            );
            controller.initialize();
            return new EmbeddedRocketMqNode(spec, controller);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize embedded Broker: " + spec.nodeId(), exception);
        }
    }

    /**
     * 啟動節點。將狀態設為 STARTING，調用控制器的 start 方法，成功後設為 RUNNING。
     *
     * @throws IllegalStateException 啟動失敗時拋出，狀態設為 FAILED
     */
    void start() {
        try {
            status = NodeStatus.STARTING;
            if (controller instanceof NamesrvController ns) {
                ns.start();
            } else if (controller instanceof BrokerController broker) {
                broker.start();
            }
            status = NodeStatus.RUNNING;
            log.info("Embedded node {} started (role={}, port={})",
                    spec.nodeId(), spec.role(), spec.listenPort());
        } catch (Exception exception) {
            status = NodeStatus.FAILED;
            throw new IllegalStateException("Failed to start embedded node: " + spec.nodeId(), exception);
        }
    }

    /**
     * 停止節點。調用控制器的 shutdown 方法，無論成功失敗都將狀態設為 STOPPED。
     * shutdown 異常僅記錄警告日誌，不拋出（確保停止操作不阻塞）。
     */
    void stop() {
        try {
            if (controller instanceof NamesrvController ns) {
                ns.shutdown();
            } else if (controller instanceof BrokerController broker) {
                broker.shutdown();
            }
            log.info("Embedded node {} stopped", spec.nodeId());
        } catch (Exception exception) {
            log.warn("Embedded node {} shutdown error: {}", spec.nodeId(), exception.getMessage());
        } finally {
            status = NodeStatus.STOPPED;
        }
    }

    /**
     * 返回節點當前狀態。
     *
     * @return 節點狀態（volatile 讀取）
     */
    NodeStatus status() {
        return status;
    }

    /**
     * 返回節點規格。
     *
     * @return 節點規格
     */
    EmbeddedNodeSpec spec() {
        return spec;
    }

    /**
     * 判斷是否為 NameServer 節點。
     *
     * @return 控制器為 NamesrvController 時返回 true
     */
    boolean isNameserver() {
        return controller instanceof NamesrvController;
    }

    /**
     * 判斷是否為 Broker 節點。
     *
     * @return 控制器為 BrokerController 時返回 true
     */
    boolean isBroker() {
        return controller instanceof BrokerController;
    }

    /**
     * 獲取 Broker 控制器引用（僅 broker 節點可用）。
     * 用於 topic 創建、強制註冊等高級操作。
     *
     * @return BrokerController 實例
     * @throws IllegalStateException 節點不是 Broker 時拋出
     */
    BrokerController brokerController() {
        if (controller instanceof BrokerController broker) {
            return broker;
        }
        throw new IllegalStateException("Node " + spec.nodeId() + " is not a broker");
    }

    /**
     * 獲取 NameServer 地址（用於 broker 註冊和客戶端連接）。
     *
     * @return "127.0.0.1:" + listenPort 格式的地址
     */
    String namesrvAddr() {
        return "127.0.0.1:" + spec.listenPort();
    }
}
