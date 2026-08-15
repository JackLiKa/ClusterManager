package com.example.clustermanager.infrastructure.pseudo;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 命令行 TAP 原生橋接——{@link TapNativeBridge} 的真實實現。
 *
 * <p>通過執行外部命令行工具完成真實的 TAP 設備操作（創建段、綁定 IP、隔離、釋放）。
 * 命令模板從 {@link PseudoTapProperties} 配置讀取，支持佔位符替換：
 * <ul>
 *   <li>{@code {tapDeviceName}}——TAP 設備名稱</li>
 *   <li>{@code {cidr}}——CIDR 網段</li>
 *   <li>{@code {nodeId}}——節點 ID</li>
 *   <li>{@code {ipAddress}}——虛擬 IP 地址</li>
 *   <li>{@code {allowedPeers}}——允許通信的節點列表（逗號分隔）</li>
 * </ul>
 *
 * <p>標記為 {@code @Primary}，當 {@code cluster.pseudo.tap.enabled=true} 時覆蓋
 * {@link NoOpTapNativeBridge} 成為默認裝配。每條命令執行有超時保護（默認 10 秒）。
 *
 * <p><b>線程安全</b>：無實例可變狀態，{@code properties} 為不可變 record。
 */
@Primary
@Component
@ConditionalOnProperty(prefix = "cluster.pseudo.tap", name = "enabled", havingValue = "true")
public class CommandTapNativeBridge implements TapNativeBridge {

    private static final Logger log = LoggerFactory.getLogger(CommandTapNativeBridge.class);

    /** TAP 配置屬性——命令模板與超時設置 */
    private final PseudoTapProperties properties;

    /**
     * 構造器注入 TAP 配置屬性。
     *
     * @param properties TAP 配置屬性
     */
    public CommandTapNativeBridge(PseudoTapProperties properties) {
        this.properties = properties;
    }

    /**
     * 執行創建隔離段的命令。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param cidr          CIDR 網段
     */
    @Override
    public void createIsolatedSegment(String tapDeviceName, String cidr) {
        run(template(properties.createSegmentCommand(), tapDeviceName, cidr, null, null));
    }

    /**
     * 執行綁定虛擬 IP 的命令。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        節點 ID
     * @param ipAddress     虛擬 IP
     */
    @Override
    public void assignNodeIp(String tapDeviceName, String nodeId, String ipAddress) {
        run(template(properties.assignIpCommand(), tapDeviceName, null, nodeId, ipAddress));
    }

    /**
     * 執行應用隔離規則的命令。若未配置隔離命令則跳過。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        目標節點 ID
     * @param allowedPeers  允許通信的節點 ID 集合
     */
    @Override
    public void applyIsolationRules(String tapDeviceName, String nodeId, Collection<String> allowedPeers) {
        List<String> command = properties.isolationCommand();
        if (command == null || command.isEmpty()) {
            return;
        }
        run(template(command.stream()
                .map(token -> token.replace("{allowedPeers}", String.join(",", allowedPeers)))
                .toList(), tapDeviceName, null, nodeId, null));
    }

    /**
     * 執行釋放虛擬 IP 的命令。
     *
     * @param tapDeviceName TAP 設備名稱
     * @param nodeId        節點 ID
     * @param ipAddress     待釋放的虛擬 IP
     */
    @Override
    public void releaseNode(String tapDeviceName, String nodeId, String ipAddress) {
        run(template(properties.releaseIpCommand(), tapDeviceName, null, nodeId, ipAddress));
    }

    /**
     * 執行命令行，帶超時保護。命令為空時直接返回。
     *
     * @param command 待執行的命令（已替換佔位符）
     * @throws IllegalStateException 命令超時、退出碼非零或執行失敗時拋出
     */
    private void run(List<String> command) {
        if (command.isEmpty()) {
            return;
        }
        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(properties.resolvedTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("TAP command timed out: " + command);
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("TAP command failed with exit " + process.exitValue() + ": " + command);
            }
            log.info("TAP command succeeded: {}", command);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to execute TAP command: " + command, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing TAP command: " + command, exception);
        }
    }

    /**
     * 將命令模板中的佔位符替換為實際值。
     *
     * @param raw           原始命令模板列表
     * @param tapDeviceName TAP 設備名稱（替換 {@code {tapDeviceName}}）
     * @param cidr          CIDR 網段（替換 {@code {cidr}}）
     * @param nodeId        節點 ID（替換 {@code {nodeId}}）
     * @param ipAddress     IP 地址（替換 {@code {ipAddress}}）
     * @return 替換後的命令列表，原始模板為空時返回空列表
     */
    private List<String> template(List<String> raw, String tapDeviceName, String cidr, String nodeId, String ipAddress) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> rendered = new ArrayList<>();
        for (String token : raw) {
            rendered.add(token
                    .replace("{tapDeviceName}", value(tapDeviceName))
                    .replace("{cidr}", value(cidr))
                    .replace("{nodeId}", value(nodeId))
                    .replace("{ipAddress}", value(ipAddress)));
        }
        return rendered;
    }

    /**
     * 將 null 轉為空字串，避免佔位符替換時出現 "null" 字面值。
     *
     * @param value 原始值
     * @return 非空字串
     */
    private static String value(String value) {
        return value == null ? "" : value;
    }
}
