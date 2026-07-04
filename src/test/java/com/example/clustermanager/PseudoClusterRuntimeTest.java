package com.example.clustermanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.application.model.MessageSimulationCommand;
import com.example.clustermanager.application.service.ClusterFacadeService;
import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.MiddlewareType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PseudoClusterRuntimeTest {

    @Autowired
    private ClusterFacadeService clusterFacadeService;

    @Test
    void shouldSendAndConsumeMessagesThroughLocalPseudoBroker() {
        ClusterSelection cluster = new ClusterSelection("local-lab", ClusterMode.PSEUDO, MiddlewareType.ROCKETMQ);

        var result = clusterFacadeService.simulateMessages(new MessageSimulationCommand(
                cluster,
                "TopicRuntime",
                "runtime-test-group",
                3,
                "{\"payload\":\"runtime\"}",
                "rmq-broker-m-01",
                List.of("rmq-broker-m-01"),
                Map.of("source", "test")
        ));

        assertThat(result.deliveries()).hasSize(3);
        assertThat(result.deliveries()).allSatisfy(delivery -> {
            assertThat(delivery.success()).isTrue();
            assertThat(delivery.detail()).contains("local pseudo node");
        });
    }
}
