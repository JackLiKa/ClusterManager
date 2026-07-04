package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.LogEntry;
import com.example.clustermanager.core.model.NodeMetrics;
import com.example.clustermanager.core.model.NodeStatus;
import java.util.List;

interface PseudoNodeRuntime {

    void ensurePrepared(List<PseudoNodeSpec> specs);

    void start(PseudoNodeSpec spec);

    void stop(String nodeId);

    void restart(PseudoNodeSpec spec);

    NodeStatus status(PseudoNodeSpec spec);

    NodeMetrics metrics(PseudoNodeSpec spec);

    List<LogEntry> logs(PseudoNodeSpec spec, int limit);

    boolean deliverMessage(PseudoNodeSpec brokerSpec, String topic, String messageKey, String payload);

    boolean consumeMessage(PseudoNodeSpec brokerSpec, String topic, String consumerGroup);
}
