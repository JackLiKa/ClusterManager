package com.example.clustermanager.infrastructure.rocketmq;

import java.util.List;
import java.util.Map;

public interface RocketMqAdminClient {

    List<RocketMqNodeSnapshot> fetchNodes(String clusterName);

    List<RocketMqLinkSnapshot> fetchLinks(String clusterName);

    void invokeBrokerLifecycle(String nodeId, String operation);

    List<Map<String, Object>> fetchMessages(String topic, int messageCount);
}
