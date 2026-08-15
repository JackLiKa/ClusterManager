package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clustermanager.application.service.MessageTemplateService;
import com.example.clustermanager.application.service.RocketMqConnectionConfigService;
import com.example.clustermanager.application.service.RocketMqConnectionConfigService.ConfigSnapshot;
import com.example.clustermanager.core.model.MessageTemplate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 消息模板與連接配置測試——使用 6 種黑盒測試方法覆蓋新增的消息模板系統和連接配置功能。
 *
 * <p>測試方法分類（每個 {@code @Nested} 類對應一種方法）：
 * <ul>
 *   <li>{@link EquivalencePartitioningTests}——等價類劃分法：覆蓋有效/無效等價類</li>
 *   <li>{@link BoundaryValueAnalysisTests}——邊界值分析法：測試邊界及鄰域值</li>
 *   <li>{@link OrthogonalArrayTests}——正交試驗法：多因素組合高效測試</li>
 *   <li>{@link DecisionTableTests}——判定表法：複雜邏輯條件組合</li>
 *   <li>{@link ErrorGuessingTests}——錯誤猜測法：基於經驗定位潛在缺陷</li>
 *   <li>{@link ScenarioTests}——場景法：模擬真實業務流程</li>
 * </ul>
 *
 * <p>測試層級：API 層（MockMvc）+ 應用層（MessageTemplateService / RocketMqConnectionConfigService）。
 * 手動構建 MockMvc（不使用 {@code @AutoConfigureMockMvc}）。
 * {@code @DirtiesContext} 確保每個測試方法後上下文重建，隔離配置狀態。
 *
 * <p>覆蓋的 API 端點：
 * <ul>
 *   <li>GET /api/clusters/message-templates — 返回預定義模板列表（6 個模板）</li>
 *   <li>GET /api/clusters/settings/rocketmq — 獲取連接配置（ConfigSnapshot record）</li>
 *   <li>PUT /api/clusters/settings/rocketmq — 更新連接配置（立即生效，持久化到文件）</li>
 * </ul>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("消息模板與連接配置測試")
class MessageTemplateAndConfigTest {

    private static final String TEMPLATES_PATH = "/api/clusters/message-templates";
    private static final String CONFIG_PATH = "/api/clusters/settings/rocketmq";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MessageTemplateService messageTemplateService;

    @Autowired
    private RocketMqConnectionConfigService connectionConfigService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ===== 輔助方法 =====

    /** 構造有效的配置 JSON 請求體 */
    private String validConfigJson(List<String> nameServers, int sendTimeout, int consumeTimeout, String prefix) {
        String nameServersJson = nameServers.stream()
                .map(s -> "\"" + s + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return """
                {"nameServers":[%s],"sendMsgTimeoutMs":%d,"consumeTimeoutSeconds":%d,"consumerGroupPrefix":"%s"}
                """.formatted(nameServersJson, sendTimeout, consumeTimeout, prefix);
    }

    /** 構造有效的配置 JSON 請求體（默認值） */
    private String validConfigJson() {
        return validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "mqcluster");
    }

    // =================================================================
    // 1. 等價類劃分法——通過有效/無效等價類覆蓋輸入範圍
    // =================================================================

    @Nested
    @DisplayName("等價類劃分法")
    class EquivalencePartitioningTests {

        // --- 模板 ID 等價類 ---

        @Test
        @DisplayName("有效等價類：有效模板 ID（json-order）→ 模板存在且內容正確")
        void validTemplateId() {
            MessageTemplate template = messageTemplateService.findTemplate("json-order");
            assertThat(template).isNotNull();
            assertThat(template.id()).isEqualTo("json-order");
            assertThat(template.name()).isEqualTo("JSON 訂單事件");
            assertThat(template.template()).contains("{uuid}");
            assertThat(template.template()).contains("{index}");
            assertThat(template.template()).contains("{random}");
            assertThat(template.template()).contains("{timestamp}");
        }

        @Test
        @DisplayName("無效等價類：無效模板 ID（nonexistent）→ 返回 null")
        void invalidTemplateId() {
            MessageTemplate template = messageTemplateService.findTemplate("nonexistent");
            assertThat(template).isNull();
        }

        @Test
        @DisplayName("有效等價類：GET /message-templates 返回 6 個預定義模板")
        void validTemplateListEndpoint() throws Exception {
            mockMvc.perform(get(TEMPLATES_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$[?(@.id == 'json-order')]").exists())
                    .andExpect(jsonPath("$[?(@.id == 'plain-text')]").exists())
                    .andExpect(jsonPath("$[?(@.id == 'rocketmq-event')]").exists())
                    .andExpect(jsonPath("$[?(@.id == 'key-value')]").exists())
                    .andExpect(jsonPath("$[?(@.id == 'json-user')]").exists())
                    .andExpect(jsonPath("$[?(@.id == 'empty')]").exists());
        }

        // --- 配置等價類 ---

        @Test
        @DisplayName("有效等價類：正常 NameServer 地址的配置 → 200")
        void validConfig() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers[0]").value("127.0.0.1:9876"))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(10000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(15))
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("mqcluster"));
        }

        @Test
        @DisplayName("無效等價類：空前綴的配置 → 400")
        void invalidConfigBlankPrefix() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("無效等價類：超時超出範圍的配置 → 400")
        void invalidConfigTimeoutOutOfRange() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 999, 15, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("有效等價類：GET /settings/rocketmq 返回當前配置快照")
        void validGetConfig() throws Exception {
            mockMvc.perform(get(CONFIG_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers").isArray())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").isNumber())
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").isNumber())
                    .andExpect(jsonPath("$.consumerGroupPrefix").isString());
        }
    }

    // =================================================================
    // 2. 邊界值分析法——重點測試邊界及鄰域值
    // =================================================================

    @Nested
    @DisplayName("邊界值分析法")
    class BoundaryValueAnalysisTests {

        // --- sendMsgTimeoutMs 邊界值：1000-600000 ---

        @Test
        @DisplayName("邊界值：sendMsgTimeoutMs=999（無效，低於最小值）→ 400")
        void sendTimeoutBelowMin() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 999, 15, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("邊界值：sendMsgTimeoutMs=1000（邊界，最小有效值）→ 200")
        void sendTimeoutMinBoundary() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 1000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(1000));
        }

        @Test
        @DisplayName("邊界值：sendMsgTimeoutMs=1001（有效，略高於最小值）→ 200")
        void sendTimeoutJustAboveMin() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 1001, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(1001));
        }

        @Test
        @DisplayName("邊界值：sendMsgTimeoutMs=599999（有效，略低於最大值）→ 200")
        void sendTimeoutJustBelowMax() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 599999, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(599999));
        }

        @Test
        @DisplayName("邊界值：sendMsgTimeoutMs=600000（邊界，最大有效值）→ 200")
        void sendTimeoutMaxBoundary() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 600000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(600000));
        }

        @Test
        @DisplayName("邊界值：sendMsgTimeoutMs=600001（無效，超過最大值）→ 400")
        void sendTimeoutAboveMax() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 600001, 15, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        // --- consumeTimeoutSeconds 邊界值：1-300 ---

        @Test
        @DisplayName("邊界值：consumeTimeoutSeconds=0（無效，低於最小值）→ 400")
        void consumeTimeoutBelowMin() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 0, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("邊界值：consumeTimeoutSeconds=1（邊界，最小有效值）→ 200")
        void consumeTimeoutMinBoundary() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 1, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(1));
        }

        @Test
        @DisplayName("邊界值：consumeTimeoutSeconds=2（有效，略高於最小值）→ 200")
        void consumeTimeoutJustAboveMin() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 2, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(2));
        }

        @Test
        @DisplayName("邊界值：consumeTimeoutSeconds=299（有效，略低於最大值）→ 200")
        void consumeTimeoutJustBelowMax() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 299, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(299));
        }

        @Test
        @DisplayName("邊界值：consumeTimeoutSeconds=300（邊界，最大有效值）→ 200")
        void consumeTimeoutMaxBoundary() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 300, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(300));
        }

        @Test
        @DisplayName("邊界值：consumeTimeoutSeconds=301（無效，超過最大值）→ 400")
        void consumeTimeoutAboveMax() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 301, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        // --- consumerGroupPrefix 邊界值 ---

        @Test
        @DisplayName("邊界值：consumerGroupPrefix 為空字符串（無效）→ 400")
        void prefixEmptyString() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("邊界值：consumerGroupPrefix 為單字符（有效）→ 200")
        void prefixSingleChar() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "x")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("x"));
        }

        @Test
        @DisplayName("邊界值：consumerGroupPrefix 為正常字符串（有效）→ 200")
        void prefixNormalString() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("mqcluster"));
        }

        // --- nameServers 邊界值 ---

        @Test
        @DisplayName("邊界值：nameServers 為空列表（有效）→ 200")
        void nameServersEmptyList() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of(), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers").isArray())
                    .andExpect(jsonPath("$.nameServers.length()").value(0));
        }

        @Test
        @DisplayName("邊界值：nameServers 為單地址（有效）→ 200")
        void nameServersSingleAddress() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(1))
                    .andExpect(jsonPath("$.nameServers[0]").value("127.0.0.1:9876"));
        }

        @Test
        @DisplayName("邊界值：nameServers 為多地址（有效）→ 200")
        void nameServersMultipleAddresses() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(
                                    List.of("127.0.0.1:9876", "127.0.0.2:9876", "127.0.0.3:9876"),
                                    10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(3))
                    .andExpect(jsonPath("$.nameServers[0]").value("127.0.0.1:9876"))
                    .andExpect(jsonPath("$.nameServers[1]").value("127.0.0.2:9876"))
                    .andExpect(jsonPath("$.nameServers[2]").value("127.0.0.3:9876"));
        }
    }

    // =================================================================
    // 3. 正交試驗法——利用正交表高效測試多因素組合
    // =================================================================

    @Nested
    @DisplayName("正交試驗法")
    class OrthogonalArrayTests {

        /**
         * 因素：nameServers（空/單地址/多地址）× sendMsgTimeoutMs（最小/正常/最大）× consumeTimeoutSeconds（最小/正常/最大）
         * 正交表 L9(3^3)：9 個測試用例覆蓋所有因素組合
         */

        @Test
        @DisplayName("正交組合 1：空 NameServer + 最小超時 + 最小消費超時 → 200")
        void orthogonalCombo1() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of(), 1000, 1, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(0))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(1000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(1));
        }

        @Test
        @DisplayName("正交組合 2：空 NameServer + 正常超時 + 正常消費超時 → 200")
        void orthogonalCombo2() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of(), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(10000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(15));
        }

        @Test
        @DisplayName("正交組合 3：空 NameServer + 最大超時 + 最大消費超時 → 200")
        void orthogonalCombo3() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of(), 600000, 300, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(600000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(300));
        }

        @Test
        @DisplayName("正交組合 4：單地址 NameServer + 最小超時 + 正常消費超時 → 200")
        void orthogonalCombo4() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 1000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(1))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(1000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(15));
        }

        @Test
        @DisplayName("正交組合 5：單地址 NameServer + 正常超時 + 最大消費超時 → 200")
        void orthogonalCombo5() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 300, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(1))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(10000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(300));
        }

        @Test
        @DisplayName("正交組合 6：單地址 NameServer + 最大超時 + 最小消費超時 → 200")
        void orthogonalCombo6() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 600000, 1, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(600000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(1));
        }

        @Test
        @DisplayName("正交組合 7：多地址 NameServer + 最小超時 + 最大消費超時 → 200")
        void orthogonalCombo7() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(
                                    List.of("127.0.0.1:9876", "127.0.0.2:9876"),
                                    1000, 300, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(2))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(1000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(300));
        }

        @Test
        @DisplayName("正交組合 8：多地址 NameServer + 正常超時 + 最小消費超時 → 200")
        void orthogonalCombo8() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(
                                    List.of("127.0.0.1:9876", "127.0.0.2:9876"),
                                    10000, 1, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(2))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(10000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(1));
        }

        @Test
        @DisplayName("正交組合 9：多地址 NameServer + 最大超時 + 正常消費超時 → 200")
        void orthogonalCombo9() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(
                                    List.of("127.0.0.1:9876", "127.0.0.2:9876"),
                                    600000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(2))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(600000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(15));
        }
    }

    // =================================================================
    // 4. 判定表法——處理複雜邏輯條件組合
    // =================================================================

    @Nested
    @DisplayName("判定表法")
    class DecisionTableTests {

        /**
         * 判定表：配置更新結果依賴於 (NameServer 有效, 超時有效, 前綴有效)
         *
         * | NameServer | 超時  | 前綴  | 預期結果 |
         * |-----------|-------|------|---------|
         * | 有效       | 有效   | 有效  | 200     |
         * | 有效       | 有效   | 無效  | 400     |
         * | 有效       | 無效   | 有效  | 400     |
         * | 有效       | 無效   | 無效  | 400     |
         */

        @Test
        @DisplayName("判定表：有效 NameServer + 有效超時 + 有效前綴 → 200")
        void decisionAllValid() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("判定表：有效 NameServer + 有效超時 + 無效前綴（空）→ 400")
        void decisionInvalidPrefix() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("判定表：有效 NameServer + 無效超時（過小）+ 有效前綴 → 400")
        void decisionInvalidTimeoutLow() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 999, 15, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("判定表：有效 NameServer + 無效超時（過大）+ 有效前綴 → 400")
        void decisionInvalidTimeoutHigh() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 600001, 15, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("判定表：有效 NameServer + 無效消費超時 + 有效前綴 → 400")
        void decisionInvalidConsumeTimeout() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 0, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("判定表：全部無效（無效超時 + 無效前綴）→ 400")
        void decisionAllInvalid() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 999, 0, "")))
                    .andExpect(status().isBadRequest());
        }
    }

    // =================================================================
    // 5. 錯誤猜測法——基於經驗直覺定位潛在缺陷
    // =================================================================

    @Nested
    @DisplayName("錯誤猜測法")
    class ErrorGuessingTests {

        @Test
        @DisplayName("錯誤猜測：PUT 請求體為空 → 4xx 或 5xx")
        void emptyPutBody() throws Exception {
            int status = mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("錯誤猜測：PUT 請求體為非法 JSON → 4xx 或 5xx")
        void malformedJsonPutBody() throws Exception {
            int status = mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{invalid json"))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("錯誤猜測：PUT 請求缺少 Content-Type → 4xx 或 5xx")
        void missingContentTypePut() throws Exception {
            int status = mockMvc.perform(put(CONFIG_PATH)
                            .content(validConfigJson()))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("錯誤猜測：nameServers 包含非法地址格式（not-a-host:port）→ 應該接受（後端不校驗格式）")
        void nameServersInvalidFormatAccepted() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("not-a-host:port"), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers[0]").value("not-a-host:port"));
        }

        @Test
        @DisplayName("錯誤猜測：consumerGroupPrefix 包含特殊字符 → 應該接受")
        void prefixWithSpecialChars() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "mq-cluster_test@123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("mq-cluster_test@123"));
        }

        @Test
        @DisplayName("錯誤猜測：sendMsgTimeoutMs 為負數 → 400")
        void sendTimeoutNegative() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), -1, 15, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("錯誤猜測：sendMsgTimeoutMs 為字符串 → 4xx 或 5xx（類型不匹配）")
        void sendTimeoutAsString() throws Exception {
            int status = mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nameServers":["127.0.0.1:9876"],"sendMsgTimeoutMs":"abc","consumeTimeoutSeconds":15,"consumerGroupPrefix":"mqcluster"}
                                    """))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isGreaterThanOrEqualTo(400);
        }

        @Test
        @DisplayName("錯誤猜測：模板列表 GET 請求帶 body → 忽略 body，返回 200")
        void getTemplatesWithBody() throws Exception {
            mockMvc.perform(get(TEMPLATES_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ignored\":\"body\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(6));
        }

        @Test
        @DisplayName("錯誤猜測：consumeTimeoutSeconds 為負數 → 400")
        void consumeTimeoutNegative() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, -1, "mqcluster")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("錯誤猜測：consumerGroupPrefix 為 null（JSON 中缺失字段）→ 400")
        void prefixNullInJson() throws Exception {
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nameServers":["127.0.0.1:9876"],"sendMsgTimeoutMs":10000,"consumeTimeoutSeconds":15}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    // =================================================================
    // 6. 場景法——模擬真實業務流程測試
    // =================================================================

    @Nested
    @DisplayName("場景法")
    class ScenarioTests {

        @Test
        @DisplayName("場景 1：首次加載配置 → 獲取默認配置 → 修改 NameServer → 保存 → 再次獲取確認已更新")
        void scenarioConfigLoadAndUpdate() throws Exception {
            // Step 1: 首次獲取配置（默認配置）
            MvcResult initialResult = mockMvc.perform(get(CONFIG_PATH))
                    .andExpect(status().isOk())
                    .andReturn();
            String initialJson = initialResult.getResponse().getContentAsString();
            assertThat(initialJson).contains("nameServers");
            assertThat(initialJson).contains("sendMsgTimeoutMs");
            assertThat(initialJson).contains("consumeTimeoutSeconds");
            assertThat(initialJson).contains("consumerGroupPrefix");

            // Step 2: 修改 NameServer 並保存
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(
                                    List.of("192.168.1.100:9876", "192.168.1.101:9876"),
                                    20000, 30, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(2))
                    .andExpect(jsonPath("$.nameServers[0]").value("192.168.1.100:9876"))
                    .andExpect(jsonPath("$.nameServers[1]").value("192.168.1.101:9876"));

            // Step 3: 再次獲取確認已更新
            mockMvc.perform(get(CONFIG_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(2))
                    .andExpect(jsonPath("$.nameServers[0]").value("192.168.1.100:9876"))
                    .andExpect(jsonPath("$.nameServers[1]").value("192.168.1.101:9876"))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(20000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(30))
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("mqcluster"));
        }

        @Test
        @DisplayName("場景 2：獲取模板列表 → 選擇模板 → 驗證模板內容包含占位符")
        void scenarioTemplateSelection() throws Exception {
            // Step 1: 獲取模板列表
            MvcResult listResult = mockMvc.perform(get(TEMPLATES_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(6))
                    .andReturn();
            String listJson = listResult.getResponse().getContentAsString();
            assertThat(listJson).contains("json-order");
            assertThat(listJson).contains("plain-text");
            assertThat(listJson).contains("rocketmq-event");
            assertThat(listJson).contains("key-value");
            assertThat(listJson).contains("json-user");
            assertThat(listJson).contains("empty");

            // Step 2: 選擇 json-order 模板
            MessageTemplate template = messageTemplateService.findTemplate("json-order");
            assertThat(template).isNotNull();

            // Step 3: 驗證模板內容包含占位符
            assertThat(template.template()).contains("{uuid}");
            assertThat(template.template()).contains("{index}");
            assertThat(template.template()).contains("{random}");
            assertThat(template.template()).contains("{timestamp}");

            // Step 4: 渲染模板驗證占位符被替換（json-order 不含 {topic}，驗證其他占位符被替換）
            String rendered = messageTemplateService.render(template.template(), "test-topic", 0);
            assertThat(rendered).doesNotContain("{uuid}");
            assertThat(rendered).doesNotContain("{index}");
            assertThat(rendered).doesNotContain("{random}");
            assertThat(rendered).doesNotContain("{timestamp}");
            assertThat(rendered).contains("orderId");

            // Step 5: 驗證含 {topic} 的模板（plain-text）渲染後包含 topic 名稱
            MessageTemplate plainTextTemplate = messageTemplateService.findTemplate("plain-text");
            assertThat(plainTextTemplate.template()).contains("{topic}");
            String plainRendered = messageTemplateService.render(plainTextTemplate.template(), "test-topic", 0);
            assertThat(plainRendered).contains("test-topic");
        }

        @Test
        @DisplayName("場景 3：完整配置流程——獲取 → 修改全部參數 → 保存 → 驗證 → 恢復默認 → 保存 → 驗證")
        void scenarioFullConfigFlow() throws Exception {
            // Step 1: 獲取當前配置
            MvcResult initialResult = mockMvc.perform(get(CONFIG_PATH))
                    .andExpect(status().isOk())
                    .andReturn();
            String initialJson = initialResult.getResponse().getContentAsString();
            assertThat(initialJson).isNotEmpty();

            // Step 2: 修改全部參數並保存
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(
                                    List.of("10.0.0.1:9876", "10.0.0.2:9876", "10.0.0.3:9876"),
                                    30000, 60, "custom-prefix")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(3))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(30000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(60))
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("custom-prefix"));

            // Step 3: 驗證已保存
            mockMvc.perform(get(CONFIG_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(3))
                    .andExpect(jsonPath("$.nameServers[0]").value("10.0.0.1:9876"))
                    .andExpect(jsonPath("$.nameServers[1]").value("10.0.0.2:9876"))
                    .andExpect(jsonPath("$.nameServers[2]").value("10.0.0.3:9876"))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(30000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(60))
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("custom-prefix"));

            // Step 4: 恢復默認配置並保存
            mockMvc.perform(put(CONFIG_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validConfigJson(List.of("127.0.0.1:9876"), 10000, 15, "mqcluster")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(1))
                    .andExpect(jsonPath("$.nameServers[0]").value("127.0.0.1:9876"))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(10000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(15))
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("mqcluster"));

            // Step 5: 驗證已恢復
            mockMvc.perform(get(CONFIG_PATH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nameServers.length()").value(1))
                    .andExpect(jsonPath("$.nameServers[0]").value("127.0.0.1:9876"))
                    .andExpect(jsonPath("$.sendMsgTimeoutMs").value(10000))
                    .andExpect(jsonPath("$.consumeTimeoutSeconds").value(15))
                    .andExpect(jsonPath("$.consumerGroupPrefix").value("mqcluster"));
        }

        @Test
        @DisplayName("場景 4：模板渲染——驗證所有占位符替換正確")
        void scenarioTemplateRendering() {
            // 渲染包含所有占位符的模板
            String template = "index={index},timestamp={timestamp},uuid={uuid},random={random},topic={topic}";
            String rendered = messageTemplateService.render(template, "my-topic", 5);

            assertThat(rendered).contains("index=5");
            assertThat(rendered).contains("topic=my-topic");
            assertThat(rendered).doesNotContain("{index}");
            assertThat(rendered).doesNotContain("{timestamp}");
            assertThat(rendered).doesNotContain("{uuid}");
            assertThat(rendered).doesNotContain("{random}");
            assertThat(rendered).doesNotContain("{topic}");
        }

        @Test
        @DisplayName("場景 5：空模板渲染——使用默認模板")
        void scenarioEmptyTemplateRendering() {
            // 空模板應使用默認模板
            String rendered = messageTemplateService.render(null, "my-topic", 0);
            assertThat(rendered).contains("hello cluster");

            rendered = messageTemplateService.render("", "my-topic", 0);
            assertThat(rendered).contains("hello cluster");

            rendered = messageTemplateService.render("   ", "my-topic", 0);
            assertThat(rendered).contains("hello cluster");
        }

        @Test
        @DisplayName("場景 6：應用層配置更新——直接調用服務驗證立即生效")
        void scenarioServiceLayerConfigUpdate() {
            // 直接調用服務層更新配置
            ConfigSnapshot newConfig = new ConfigSnapshot(
                    List.of("10.0.0.1:9876"), 5000, 30, "test-prefix");
            ConfigSnapshot result = connectionConfigService.update(newConfig);

            // 驗證返回的快照與輸入一致
            assertThat(result.nameServers()).containsExactly("10.0.0.1:9876");
            assertThat(result.sendMsgTimeoutMs()).isEqualTo(5000);
            assertThat(result.consumeTimeoutSeconds()).isEqualTo(30);
            assertThat(result.consumerGroupPrefix()).isEqualTo("test-prefix");

            // 驗證再次獲取確認已更新
            ConfigSnapshot current = connectionConfigService.getSnapshot();
            assertThat(current.nameServers()).containsExactly("10.0.0.1:9876");
            assertThat(current.sendMsgTimeoutMs()).isEqualTo(5000);
            assertThat(current.consumeTimeoutSeconds()).isEqualTo(30);
            assertThat(current.consumerGroupPrefix()).isEqualTo("test-prefix");
        }
    }
}
