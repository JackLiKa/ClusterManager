package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.ClusterNode;
import com.example.clustermanager.core.model.NodeStatus;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

final class ManagedPseudoNode {

    private final String nodeId;
    private final String displayName;
    private final String hostName;
    private final String role;
    private final int port;
    private final boolean managed;
    private final String nodeKind;
    private final AtomicReference<NodeStatus> status;
    private volatile String address;

    ManagedPseudoNode(
            String nodeId,
            String displayName,
            String hostName,
            String role,
            int port,
            boolean managed,
            String nodeKind,
            NodeStatus status
    ) {
        this.nodeId = nodeId;
        this.displayName = displayName;
        this.hostName = hostName;
        this.role = role;
        this.port = port;
        this.managed = managed;
        this.nodeKind = nodeKind;
        this.status = new AtomicReference<>(status);
    }

    String nodeId() {
        return nodeId;
    }

    String role() {
        return role;
    }

    int port() {
        return port;
    }

    boolean managed() {
        return managed;
    }

    boolean hostBound() {
        return "HOST".equals(nodeKind);
    }

    PseudoNodeSpec spec() {
        return new PseudoNodeSpec(nodeId, displayName, hostName, role, port);
    }

    NodeStatus status() {
        return status.get();
    }

    void transition(NodeStatus nodeStatus) {
        status.set(nodeStatus);
    }

    String address() {
        return address;
    }

    void bindAddress(String address) {
        this.address = address;
    }

    ClusterNode toNode() {
        return new ClusterNode(
                nodeId,
                displayName,
                hostName,
                address,
                status.get(),
                Map.of(
                        "role", role,
                        "port", String.valueOf(port),
                        "networkIsolation", hostBound() ? "host" : "tap",
                        "managed", String.valueOf(managed),
                        "nodeKind", nodeKind
                )
        );
    }
}
