package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.OperationResult;
import com.example.clustermanager.core.model.ServiceRegistration;

public interface IServiceRegistry {

    OperationResult registerService(ClusterRef clusterRef, ServiceRegistration registration);

    OperationResult deleteService(ClusterRef clusterRef, String nodeId);
}
