package com.example.clustermanager.application.model;

import com.example.clustermanager.core.model.ClusterMode;
import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.MiddlewareType;

public record ClusterSelection(
        String clusterId,
        ClusterMode mode,
        MiddlewareType middleware
) {

    public ClusterRef toClusterRef() {
        return new ClusterRef(clusterId, mode, middleware);
    }
}
