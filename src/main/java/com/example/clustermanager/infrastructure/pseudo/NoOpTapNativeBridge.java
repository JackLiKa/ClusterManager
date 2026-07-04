package com.example.clustermanager.infrastructure.pseudo;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoOpTapNativeBridge implements TapNativeBridge {

    private static final Logger log = LoggerFactory.getLogger(NoOpTapNativeBridge.class);

    @Override
    public void createIsolatedSegment(String tapDeviceName, String cidr) {
        log.info("Preparing TAP segment {} on {}", cidr, tapDeviceName);
    }

    @Override
    public void assignNodeIp(String tapDeviceName, String nodeId, String ipAddress) {
        log.info("Binding virtual IP {} to node {} via {}", ipAddress, nodeId, tapDeviceName);
    }

    @Override
    public void applyIsolationRules(String tapDeviceName, String nodeId, Collection<String> allowedPeers) {
        log.info("Applying isolation for node {} on {}, peers={}", nodeId, tapDeviceName, allowedPeers);
    }

    @Override
    public void releaseNode(String tapDeviceName, String nodeId, String ipAddress) {
        log.info("Releasing virtual IP {} from node {} via {}", ipAddress, nodeId, tapDeviceName);
    }
}
