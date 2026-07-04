package com.example.clustermanager.infrastructure.rocketmq;

import com.example.clustermanager.core.model.NodeStatus;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MockRocketMqAdminClient implements RocketMqAdminClient {

    @Override
    public List<RocketMqNodeSnapshot> fetchNodes(String clusterName) {
        return List.of(
                new RocketMqNodeSnapshot(
                        "demo-ns-01",
                        "NameServer-01",
                        "192.168.50.78",
                        "192.168.50.78:9876",
                        NodeStatus.RUNNING,
                        Map.of("role", "nameserver", "source", "admin-api")
                ),
                new RocketMqNodeSnapshot(
                        "demo-broker-01",
                        "Broker-Master-01",
                        "169.254.11.77",
                        "169.254.11.77:10911",
                        NodeStatus.RUNNING,
                        Map.of("role", "broker-master", "source", "admin-api")
                ),
                new RocketMqNodeSnapshot(
                        "demo-proxy-01",
                        "Proxy-01",
                        "192.168.50.78",
                        "192.168.50.78:8080",
                        NodeStatus.RUNNING,
                        Map.of("role", "proxy", "source", "admin-api")
                )
        );
    }

    @Override
    public List<RocketMqLinkSnapshot> fetchLinks(String clusterName) {
        return List.of(
                new RocketMqLinkSnapshot("demo-ns-01", "demo-broker-01", true, 2.4),
                new RocketMqLinkSnapshot("demo-ns-01", "demo-proxy-01", true, 2.9)
        );
    }

    @Override
    public void invokeBrokerLifecycle(String nodeId, String operation) {
        // Replace this mock with DefaultMQAdminExt or MQAdminExtImpl when wiring the real RocketMQ dependency.
    }

    @Override
    public List<Map<String, Object>> fetchMessages(String topic, int messageCount) {
        return java.util.stream.IntStream.range(0, messageCount)
                .mapToObj(index -> Map.<String, Object>of(
                        "messageKey", topic + "-" + index,
                        "queueId", index % 2,
                        "status", "OK"
                ))
                .toList();
    }
}
