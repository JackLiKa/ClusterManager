package com.example.clustermanager.core.model;

import java.util.List;

public record ClusterTopology(
        ClusterRef cluster,
        List<ClusterNode> nodes,
        List<NetworkLink> links,
        String activeVip
) {
}
