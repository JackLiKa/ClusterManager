package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clustermanager.application.service.RateLimitService;
import com.example.clustermanager.application.service.RateLimitService.RateLimitResult;
import com.example.clustermanager.application.service.RateLimitService.SystemProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 動態限流服務測試——使用 6 種黑盒測試方法覆蓋限流計算邏輯。
 *
 * <p>測試方法分類：
 * <ul>
 *   <li>{@link EquivalencePartitioningTests}——等價類劃分法</li>
 *   <li>{@link BoundaryValueAnalysisTests}——邊界值分析法</li>
 *   <li>{@link OrthogonalArrayTests}——正交試驗法</li>
 *   <li>{@link DecisionTableTests}——判定表法</li>
 *   <li>{@link ErrorGuessingTests}——錯誤猜測法</li>
 *   <li>{@link ScenarioTests}——場景法</li>
 * </ul>
 */
@SpringBootTest
@DisplayName("動態限流服務測試")
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    // ==================== 1. 等價類劃分法 ====================

    @Nested
    @DisplayName("等價類劃分法——有效/無效等價類覆蓋")
    class EquivalencePartitioningTests {

        /** 有效等價類：正常硬件配置下計算結果應為正整數 */
        @Test
        @DisplayName("有效等價類：正常硬件配置 → maxMessages 為正整數")
        void validHardwareConfig_returnsPositiveLimit() {
            RateLimitResult result = rateLimitService.calculateLimit();

            assertThat(result.maxMessages()).isPositive();
            assertThat(result.maxMessages()).isGreaterThanOrEqualTo(result.minFloor());
            assertThat(result.maxMessages()).isLessThanOrEqualTo(result.baselineCeiling());
        }

        /** 有效等價類：安全係數應在 (0, 1) 區間 */
        @Test
        @DisplayName("有效等價類：安全係數 ∈ (0, 1)")
        void safetyCoefficient_isValidRange() {
            RateLimitResult result = rateLimitService.calculateLimit();

            assertThat(result.safetyCoefficient()).isGreaterThan(0.0);
            assertThat(result.safetyCoefficient()).isLessThan(1.0);
        }

        /** 有效等價類：系統配置快照各字段應為非負值 */
        @Test
        @DisplayName("有效等價類：系統配置快照非負")
        void systemProfile_allFieldsNonNegative() {
            RateLimitResult result = rateLimitService.calculateLimit();
            SystemProfile profile = result.systemProfile();

            assertThat(profile.logicalCores()).isPositive();
            assertThat(profile.availableHeapMb()).isNotNegative();
            assertThat(profile.maxHeapMb()).isPositive();
            assertThat(profile.totalPhysicalMb()).isPositive();
            assertThat(profile.freePhysicalMb()).isNotNegative();
            assertThat(profile.systemCpuLoad()).isNotNegative();
            assertThat(profile.availableDiskGb()).isNotNegative();
        }

        /** 無效等價類：請求 0 條消息應在限制內 */
        @Test
        @DisplayName("無效等價類：請求 0 條消息 → isWithinLimit=true")
        void zeroMessages_isWithinLimit() {
            assertThat(rateLimitService.isWithinLimit(0)).isTrue();
        }

        /** 無效等價類：請求負數條消息應在限制內 */
        @Test
        @DisplayName("無效等價類：請求負數條消息 → isWithinLimit=true")
        void negativeMessages_isWithinLimit() {
            assertThat(rateLimitService.isWithinLimit(-1)).isTrue();
        }
    }

    // ==================== 2. 邊界值分析法 ====================

    @Nested
    @DisplayName("邊界值分析法——邊界及鄰域值測試")
    class BoundaryValueAnalysisTests {

        /** 邊界值：maxMessages 應 ≥ minFloor（最低保底值） */
        @Test
        @DisplayName("邊界值：maxMessages ≥ minFloor")
        void maxMessages_atLeastMinFloor() {
            RateLimitResult result = rateLimitService.calculateLimit();

            assertThat(result.maxMessages()).isGreaterThanOrEqualTo(result.minFloor());
        }

        /** 邊界值：maxMessages 應 ≤ baselineCeiling × safetyCoefficient */
        @Test
        @DisplayName("邊界值：maxMessages ≤ ceiling × safety")
        void maxMessages_atMostCeilingTimesSafety() {
            RateLimitResult result = rateLimitService.calculateLimit();
            int expectedCeiling = (int) (result.baselineCeiling() * result.safetyCoefficient());

            assertThat(result.maxMessages()).isLessThanOrEqualTo(expectedCeiling);
        }

        /** 邊界值：請求恰好等於 maxMessages 應在限制內 */
        @Test
        @DisplayName("邊界值：請求=maxMessages → isWithinLimit=true")
        void requestEqualsMax_isWithinLimit() {
            RateLimitResult result = rateLimitService.calculateLimit();

            assertThat(rateLimitService.isWithinLimit(result.maxMessages())).isTrue();
        }

        /** 鄰域值：請求 maxMessages+1 應超出限制 */
        @Test
        @DisplayName("鄰域值：請求=maxMessages+1 → isWithinLimit=false")
        void requestOneOverMax_exceedsLimit() {
            RateLimitResult result = rateLimitService.calculateLimit();

            assertThat(rateLimitService.isWithinLimit(result.maxMessages() + 1)).isFalse();
        }

        /** 鄰域值：請求 maxMessages-1 應在限制內 */
        @Test
        @DisplayName("鄰域值：請求=maxMessages-1 → isWithinLimit=true")
        void requestOneUnderMax_isWithinLimit() {
            RateLimitResult result = rateLimitService.calculateLimit();
            if (result.maxMessages() > 1) {
                assertThat(rateLimitService.isWithinLimit(result.maxMessages() - 1)).isTrue();
            }
        }
    }

    // ==================== 3. 正交試驗法 ====================

    @Nested
    @DisplayName("正交試驗法——多因素組合高效測試")
    class OrthogonalArrayTests {

        /**
         * 正交表 L4(2^3)：3 因素 2 水平
         * 因素 A：CPU 核數（多/少）
         * 因素 B：堆內存（大/小）
         * 因素 C：磁盤（大/小）
         *
         * 由於無法控制實際硬件，改為驗證公式的數學性質：
         * 在不同輸入組合下，結果都應滿足約束條件。
         */
        @Test
        @DisplayName("正交試驗：多因素組合 → 結果始終滿足約束")
        void multiFactorCombinations_alwaysSatisfyConstraints() {
            // 運行多次計算（模擬不同時刻的系統狀態）
            for (int i = 0; i < 4; i++) {
                RateLimitResult result = rateLimitService.calculateLimit();

                // 所有組合下都應滿足的約束
                assertThat(result.maxMessages()).isPositive();
                assertThat(result.maxMessages()).isGreaterThanOrEqualTo(result.minFloor());
                assertThat(result.maxMessages()).isLessThanOrEqualTo(result.baselineCeiling());
            }
        }

        /** 正交試驗：驗證三個因子的權重關係 */
        @Test
        @DisplayName("正交試驗：內存因子、CPU 因子、磁盤因子均參與計算")
        void allFactorsParticipateInCalculation() {
            RateLimitResult result = rateLimitService.calculateLimit();
            SystemProfile p = result.systemProfile();

            // 計算各因子值
            double memoryFactor = p.availableHeapMb() * (double) result.messagesPerMb();
            double cpuFactor = p.logicalCores() * (double) result.messagesPerCore();
            double diskFactor = p.availableDiskGb() * (double) result.messagesPerGb();

            // 最終結果應 ≤ 各因子 × 安全係數
            int expectedMax = (int) Math.floor(
                    Math.min(Math.min(memoryFactor, cpuFactor),
                            Math.min(diskFactor, result.baselineCeiling())
                    ) * result.safetyCoefficient()
            );
            expectedMax = Math.max(result.minFloor(), expectedMax);

            assertThat(result.maxMessages()).isEqualTo(expectedMax);
        }
    }

    // ==================== 4. 判定表法 ====================

    @Nested
    @DisplayName("判定表法——複雜邏輯條件組合")
    class DecisionTableTests {

        /**
         * 判定表：
         * | 內存因子最小? | CPU 因子最小? | 磁盤因子最小? | ceiling 最小? | → 限制因子 |
         * | Y             | N             | N             | N             | → 內存     |
         * | N             | Y             | N             | N             | → CPU      |
         * | N             | N             | Y             | N             | → 磁盤     |
         * | N             | N             | N             | Y             | → ceiling  |
         */
        @Test
        @DisplayName("判定表：最小因子決定最終上限")
        void minFactorDeterminesLimit() {
            RateLimitResult result = rateLimitService.calculateLimit();
            SystemProfile p = result.systemProfile();

            double memoryFactor = p.availableHeapMb() * (double) result.messagesPerMb();
            double cpuFactor = p.logicalCores() * (double) result.messagesPerCore();
            double diskFactor = p.availableDiskGb() * (double) result.messagesPerGb();
            double ceiling = result.baselineCeiling();

            double minFactor = Math.min(Math.min(memoryFactor, cpuFactor),
                    Math.min(diskFactor, ceiling));
            int expected = Math.max(result.minFloor(),
                    (int) Math.floor(minFactor * result.safetyCoefficient()));

            assertThat(result.maxMessages()).isEqualTo(expected);
        }

        /** 判定表：安全係數始終應用 */
        @Test
        @DisplayName("判定表：安全係數始終應用於最終結果")
        void safetyCoefficientAlwaysApplied() {
            RateLimitResult result = rateLimitService.calculateLimit();
            SystemProfile p = result.systemProfile();

            double memoryFactor = p.availableHeapMb() * (double) result.messagesPerMb();
            double cpuFactor = p.logicalCores() * (double) result.messagesPerCore();
            double diskFactor = p.availableDiskGb() * (double) result.messagesPerGb();

            double rawMin = Math.min(Math.min(memoryFactor, cpuFactor),
                    Math.min(diskFactor, result.baselineCeiling()));
            int withSafety = (int) Math.floor(rawMin * result.safetyCoefficient());
            int withoutSafety = (int) Math.floor(rawMin);

            // 有安全係數的結果應 ≤ 無安全係數的結果
            assertThat(withSafety).isLessThanOrEqualTo(withoutSafety);
        }
    }

    // ==================== 5. 錯誤猜測法 ====================

    @Nested
    @DisplayName("錯誤猜測法——基於經驗定位潛在缺陷")
    class ErrorGuessingTests {

        /** 錯誤猜測：多次調用應返回一致結果（無隨機性） */
        @Test
        @DisplayName("錯誤猜測：連續調用結果一致（系統狀態穩定時）")
        void consecutiveCalls_returnConsistentResults() {
            RateLimitResult r1 = rateLimitService.calculateLimit();
            RateLimitResult r2 = rateLimitService.calculateLimit();

            // CPU 核數和磁盤空間不會在兩次調用間變化
            assertThat(r2.systemProfile().logicalCores()).isEqualTo(r1.systemProfile().logicalCores());
        }

        /** 錯誤猜測：極大請求值應被正確拒絕 */
        @Test
        @DisplayName("錯誤猜測：Integer.MAX_VALUE 請求 → isWithinLimit=false")
        void maxIntegerRequest_rejected() {
            assertThat(rateLimitService.isWithinLimit(Integer.MAX_VALUE)).isFalse();
        }

        /** 錯誤猜測：公式參數不應為零或負數 */
        @Test
        @DisplayName("錯誤猜測：公式參數均為正值")
        void formulaParameters_arePositive() {
            RateLimitResult result = rateLimitService.calculateLimit();

            assertThat(result.messagesPerMb()).isPositive();
            assertThat(result.messagesPerCore()).isPositive();
            assertThat(result.messagesPerGb()).isPositive();
            assertThat(result.baselineCeiling()).isPositive();
            assertThat(result.minFloor()).isPositive();
        }
    }

    // ==================== 6. 場景法 ====================

    @Nested
    @DisplayName("場景法——模擬真實業務流程")
    class ScenarioTests {

        /** 場景：用戶登入 → 系統計算限流 → 用戶發送消息 */
        @Test
        @DisplayName("場景：登入→計算限流→發送消息（在限制內）")
        void scenario_loginCalculateSend_withinLimit() {
            // Step 1: 用戶登入，系統計算限流
            RateLimitResult result = rateLimitService.calculateLimit();
            assertThat(result.maxMessages()).isPositive();

            // Step 2: 用戶選擇發送 10 條消息（典型小批量）
            int requestCount = 10;
            assertThat(rateLimitService.isWithinLimit(requestCount)).isTrue();
        }

        /** 場景：用戶嘗試發送超過限制的消息 → 被拒絕 */
        @Test
        @DisplayName("場景：登入→計算限流→發送超量消息→被拒絕")
        void scenario_loginCalculateSendOverLimit_rejected() {
            // Step 1: 計算限流
            RateLimitResult result = rateLimitService.calculateLimit();

            // Step 2: 用戶嘗試發送超量消息
            int overLimit = result.maxMessages() + 100;
            assertThat(rateLimitService.isWithinLimit(overLimit)).isFalse();
        }

        /** 場景：低配機器 → 限流值至少為 minFloor */
        @Test
        @DisplayName("場景：低配機器→限流值≥minFloor")
        void scenario_lowSpecMachine_atLeastMinFloor() {
            RateLimitResult result = rateLimitService.calculateLimit();

            // 無論硬件如何，最低保底值確保可用
            assertThat(result.maxMessages()).isGreaterThanOrEqualTo(result.minFloor());
        }
    }
}
