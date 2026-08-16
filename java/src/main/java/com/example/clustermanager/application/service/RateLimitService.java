package com.example.clustermanager.application.service;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 動態限流服務——根據本機硬件配置計算最大安全消息量限制。
 *
 * <p><b>設計動機</b>：本地優先的教育平台運行在用戶個人電腦上，硬件配置差異巨大。
 * 固定上限會在低配機器上導致系統卡死崩潰，在高配機器上又過於保守。
 * 因此每次登入時探測本機核心配置，用安全公式動態計算上限，確保系統平穩運行。
 *
 * <p><b>多因素綜合公式</b>：
 * <pre>{@code
 *   maxMessages = floor(
 *       min(
 *         memoryFactor,      // 可用內存因子
 *         cpuFactor,         // CPU 核數因子
 *         diskFactor,        // 可用磁盤因子
 *         baselineCeiling    // 絕對上限（防止高配機器設過高值）
 *       )
 *       * safetyCoefficient  // 安全係數 0.7
 *   )
 * }</pre>
 *
 * <p>各因子計算：
 * <ul>
 *   <li><b>memoryFactor</b> = availableHeapMB × messagesPerMB（50 條/MB）</li>
 *   <li><b>cpuFactor</b> = logicalCores × messagesPerCore（200 條/核）</li>
 *   <li><b>diskFactor</b> = availableDiskGB × messagesPerGB（100 條/GB）</li>
 *   <li><b>baselineCeiling</b> = 5000（絕對上限）</li>
 * </ul>
 *
 * <p>最終結果乘以安全係數 0.7 並向下取整，確保留有餘量。
 * 每台機器看到的數值不同——取決於實際 CPU 核數、JVM 堆、磁盤空間。
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    /** 每 MB 可用堆內存可支撐的消息數 */
    static final int MESSAGES_PER_MB = 50;
    /** 每個 CPU 邏輯核可支撐的消息數 */
    static final int MESSAGES_PER_CORE = 200;
    /** 每 GB 可用磁盤可支撐的消息數 */
    static final int MESSAGES_PER_GB = 100;
    /** 絕對上限——防止高配機器設過高值 */
    static final int BASELINE_CEILING = 5000;
    /** 安全係數——留 30% 餘量 */
    static final double SAFETY_COEFFICIENT = 0.7;
    /** 最低保底值——確保低配機器也能發送少量消息 */
    static final int MIN_FLOOR = 50;

    /**
     * 計算當前本機的最大安全消息量限制。
     *
     * <p>每次調用都實時探測本機配置，因此結果會隨系統負載變化。
     * 前端在頁面加載時調用此接口獲取上限，並在消息工作台顯示。
     *
     * @return 限流結果（含各因子明細，供前端展示計算過程）
     */
    public RateLimitResult calculateLimit() {
        SystemProfile profile = probeSystem();

        double memoryFactor = profile.availableHeapMb() * (double) MESSAGES_PER_MB;
        double cpuFactor = profile.logicalCores() * (double) MESSAGES_PER_CORE;
        double diskFactor = profile.availableDiskGb() * (double) MESSAGES_PER_GB;

        // 取各因子最小值，再乘安全係數
        double rawLimit = Math.min(
                Math.min(memoryFactor, cpuFactor),
                Math.min(diskFactor, BASELINE_CEILING)
        ) * SAFETY_COEFFICIENT;

        int maxMessages = Math.max(MIN_FLOOR, (int) Math.floor(rawLimit));

        log.info("Rate limit calculated: max={} (memory={}, cpu={}, disk={}, ceiling={}, safety={})",
                maxMessages, (long) memoryFactor, (long) cpuFactor, (long) diskFactor,
                BASELINE_CEILING, SAFETY_COEFFICIENT);

        return new RateLimitResult(
                maxMessages,
                MIN_FLOOR,
                BASELINE_CEILING,
                SAFETY_COEFFICIENT,
                MESSAGES_PER_MB,
                MESSAGES_PER_CORE,
                MESSAGES_PER_GB,
                profile
        );
    }

    /**
     * 檢查請求的消息數量是否在安全限制內。
     *
     * @param requestedCount 請求的消息數量
     * @return true 如果在限制內
     */
    public boolean isWithinLimit(int requestedCount) {
        return requestedCount <= calculateLimit().maxMessages();
    }

    /**
     * 探測本機系統配置——CPU 核數、JVM 堆、磁盤空間。
     *
     * @return 系統配置快照
     */
    private SystemProfile probeSystem() {
        // CPU 邏輯核數
        int logicalCores = Runtime.getRuntime().availableProcessors();

        // JVM 堆內存
        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        long usedHeapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long availableHeapBytes = Math.max(0, maxHeapBytes - usedHeapBytes);
        long availableHeapMb = availableHeapBytes / (1024 * 1024);

        // 系統物理內存（用於展示，不直接參與公式）
        long totalPhysicalBytes = 0;
        long freePhysicalBytes = 0;
        try {
            var osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            totalPhysicalBytes = osBean.getTotalMemorySize();
            freePhysicalBytes = osBean.getFreeMemorySize();
        } catch (Exception e) {
            log.warn("Failed to read physical memory: {}", e.getMessage());
        }

        // 系統 CPU 使用率（用於展示）
        double systemCpuLoad = 0;
        try {
            var osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            double load = osBean.getSystemCpuLoad();
            if (load >= 0) systemCpuLoad = load * 100;
        } catch (Exception e) {
            log.warn("Failed to read system CPU load: {}", e.getMessage());
        }

        // 磁盤可用空間（項目所在分區）
        double availableDiskGb = 0;
        try {
            Path currentDir = Path.of(".").toAbsolutePath();
            FileStore store = Files.getFileStore(currentDir);
            long usableBytes = store.getUsableSpace();
            availableDiskGb = usableBytes / (1024.0 * 1024 * 1024);
        } catch (Exception e) {
            log.warn("Failed to read disk space: {}", e.getMessage());
            // 後備：假設 10GB 可用
            availableDiskGb = 10.0;
        }

        return new SystemProfile(
                logicalCores,
                (int) availableHeapMb,
                maxHeapBytes / (1024 * 1024),
                totalPhysicalBytes / (1024 * 1024),
                freePhysicalBytes / (1024 * 1024),
                systemCpuLoad,
                availableDiskGb
        );
    }

    /**
     * 限流計算結果——含最終上限和各因子明細。
     *
     * @param maxMessages        最終最大消息量限制
     * @param minFloor           最低保底值
     * @param baselineCeiling    絕對上限
     * @param safetyCoefficient  安全係數
     * @param messagesPerMb      每MB消息數
     * @param messagesPerCore    每核消息數
     * @param messagesPerGb      每GB消息數
     * @param systemProfile      系統配置快照
     */
    public record RateLimitResult(
            int maxMessages,
            int minFloor,
            int baselineCeiling,
            double safetyCoefficient,
            int messagesPerMb,
            int messagesPerCore,
            int messagesPerGb,
            SystemProfile systemProfile
    ) {}

    /**
     * 系統配置快照——記錄探測時的本機硬件狀態。
     *
     * @param logicalCores       CPU 邏輯核數
     * @param availableHeapMb    JVM 可用堆內存（MB）
     * @param maxHeapMb          JVM 最大堆內存（MB）
     * @param totalPhysicalMb    物理內存總量（MB）
     * @param freePhysicalMb     物理內存可用（MB）
     * @param systemCpuLoad      系統 CPU 使用率（%）
     * @param availableDiskGb    可用磁盤空間（GB）
     */
    public record SystemProfile(
            int logicalCores,
            int availableHeapMb,
            long maxHeapMb,
            long totalPhysicalMb,
            long freePhysicalMb,
            double systemCpuLoad,
            double availableDiskGb
    ) {}
}
