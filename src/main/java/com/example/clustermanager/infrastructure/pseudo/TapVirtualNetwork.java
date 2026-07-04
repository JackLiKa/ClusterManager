package com.example.clustermanager.infrastructure.pseudo;

import com.example.clustermanager.core.model.TapNodeAttachment;
import com.example.clustermanager.core.model.VirtualSegment;
import com.example.clustermanager.core.port.IVirtualNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class TapVirtualNetwork implements IVirtualNetwork {

    private final TapNativeBridge nativeBridge;
    private final Map<String, SegmentState> segments = new ConcurrentHashMap<>();

    public TapVirtualNetwork(TapNativeBridge nativeBridge) {
        this.nativeBridge = nativeBridge;
    }

    @Override
    public VirtualSegment ensureSegment(String segmentId, String tapDeviceName, String cidr) {
        SegmentState state = segments.computeIfAbsent(segmentId, key -> {
            nativeBridge.createIsolatedSegment(tapDeviceName, cidr);
            return new SegmentState(segmentId, tapDeviceName, cidr, new CidrAddressPool(cidr));
        });
        return state.snapshot();
    }

    @Override
    public TapNodeAttachment attachNode(String segmentId, String nodeId) {
        return attachNode(segmentId, nodeId, null);
    }

    @Override
    public TapNodeAttachment attachNode(String segmentId, String nodeId, String requestedVirtualIp) {
        SegmentState state = requireState(segmentId);
        String virtualIp = state.allocate(nodeId, requestedVirtualIp);
        nativeBridge.assignNodeIp(state.tapDeviceName, nodeId, virtualIp);
        nativeBridge.applyIsolationRules(state.tapDeviceName, nodeId, state.attachedNodeIds());
        return new TapNodeAttachment(segmentId, nodeId, virtualIp);
    }

    @Override
    public void isolateNode(String segmentId, String nodeId) {
        SegmentState state = requireState(segmentId);
        nativeBridge.applyIsolationRules(state.tapDeviceName, nodeId, state.attachedNodeIds());
    }

    @Override
    public void detachNode(String segmentId, String nodeId) {
        SegmentState state = requireState(segmentId);
        String ipAddress = state.release(nodeId);
        if (ipAddress != null) {
            nativeBridge.releaseNode(state.tapDeviceName, nodeId, ipAddress);
        }
    }

    private SegmentState requireState(String segmentId) {
        SegmentState state = segments.get(segmentId);
        if (state == null) {
            throw new IllegalArgumentException("Segment not initialized: " + segmentId);
        }
        return state;
    }

    private static final class SegmentState {
        private final String segmentId;
        private final String tapDeviceName;
        private final String cidr;
        private final CidrAddressPool addressPool;
        private final Map<String, String> attachments = new ConcurrentHashMap<>();

        private SegmentState(String segmentId, String tapDeviceName, String cidr, CidrAddressPool addressPool) {
            this.segmentId = segmentId;
            this.tapDeviceName = tapDeviceName;
            this.cidr = cidr;
            this.addressPool = addressPool;
        }

        private synchronized String allocate(String nodeId, String requestedVirtualIp) {
            return attachments.computeIfAbsent(nodeId, ignored ->
                    requestedVirtualIp == null || requestedVirtualIp.isBlank()
                            ? addressPool.allocate()
                            : addressPool.allocate(requestedVirtualIp));
        }

        private synchronized String release(String nodeId) {
            String ipAddress = attachments.remove(nodeId);
            if (ipAddress != null) {
                addressPool.release(ipAddress);
            }
            return ipAddress;
        }

        private Collection<String> attachedNodeIds() {
            return attachments.keySet();
        }

        private VirtualSegment snapshot() {
            return new VirtualSegment(segmentId, tapDeviceName, cidr, new ArrayList<>(attachments.values()));
        }
    }
}
