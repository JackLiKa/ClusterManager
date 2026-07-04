package com.example.clustermanager.core.model;

import java.util.List;

public record VirtualSegment(
        String segmentId,
        String tapDeviceName,
        String cidr,
        List<String> allocatedIps
) {
}
