package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.ClusterTopology;

public interface ITopologyReader {

    ClusterTopology loadTopology(ClusterRef clusterRef);
}
