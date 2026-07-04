package com.example.clustermanager.infrastructure.pseudo;

import java.util.Collection;

public interface TapNativeBridge {

    void createIsolatedSegment(String tapDeviceName, String cidr);

    void assignNodeIp(String tapDeviceName, String nodeId, String ipAddress);

    void applyIsolationRules(String tapDeviceName, String nodeId, Collection<String> allowedPeers);

    void releaseNode(String tapDeviceName, String nodeId, String ipAddress);
}
