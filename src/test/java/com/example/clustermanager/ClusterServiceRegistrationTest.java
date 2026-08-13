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
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClusterServiceRegistrationTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    @Test
    void shouldExposeBuiltSpaIndexInClasspath() throws Exception {
        ClassPathResource resource = new ClassPathResource("static/index.html");
        assertThat(resource.exists()).isTrue();
        assertThat(new String(resource.getInputStream().readAllBytes())).contains("<div id=\"app\"></div>");
    }

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
            assertThat(node.nodeId()).isEqualTo("rmq-broker-x-02");
            assertThat(node.virtualIp()).isEqualTo("10.77.0.40");
        });

        clusterFacadeService.deleteService(cluster, "rmq-broker-x-02");

        var topologyAfterDelete = clusterFacadeService.loadTopology(cluster);
        assertThat(topologyAfterDelete.nodes()).noneSatisfy(node -> assertThat(node.nodeId()).isEqualTo("rmq-broker-x-02"));

        var logs = clusterFacadeService.loadLogs(cluster, null, 20);
        assertThat(logs)
                .extracting(LogEntry::message)
                .anyMatch(message -> message.contains("registered"))
                .anyMatch(message -> message.contains("deleted"));
    }

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
                .anyMatch(message -> message.contains("registered"))
                .anyMatch(message -> message.contains("deleted"));
    }

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
                        Map.of("source", "integration-test", "nodeKind", "HOST")
                )
        ));

        var topology = clusterFacadeService.loadTopology(cluster);
        assertThat(topology.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo("host-rmq-broker-01");
            assertThat(node.virtualIp()).isEqualTo("127.0.0.1:10911");
            assertThat(node.labels()).containsEntry("nodeKind", "HOST");
        });
    }
}
