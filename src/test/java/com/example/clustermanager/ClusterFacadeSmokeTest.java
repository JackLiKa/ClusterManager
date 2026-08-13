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

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClusterFacadeSmokeTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    @Test
    void shouldLoadPseudoTopologyThroughUnifiedFacade() {
        var topology = clusterFacadeService.loadTopology(new ClusterSelection(
                "local-lab",
                ClusterMode.PSEUDO,
                MiddlewareType.ROCKETMQ
        ));

        assertThat(topology.nodes()).isNotEmpty();
        assertThat(topology.activeVip()).isNotBlank();
    }

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
                        Map.of("source", "test")
                )
        ));

        var topology = clusterFacadeService.loadTopology(cluster);

        assertThat(topology.nodes()).anySatisfy(node -> {
            assertThat(node.nodeId()).isEqualTo("manual-rmq-proxy-01");
            assertThat(node.labels()).containsEntry("source", "manual");
        });
    }
}
