package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.TapNodeAttachment;
import com.example.clustermanager.core.model.VirtualSegment;

public interface IVirtualNetwork {

    VirtualSegment ensureSegment(String segmentId, String tapDeviceName, String cidr);

    TapNodeAttachment attachNode(String segmentId, String nodeId);

    TapNodeAttachment attachNode(String segmentId, String nodeId, String requestedVirtualIp);

    void isolateNode(String segmentId, String nodeId);

    void detachNode(String segmentId, String nodeId);
}
