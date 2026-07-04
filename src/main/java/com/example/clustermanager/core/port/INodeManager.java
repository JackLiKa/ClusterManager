package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.OperationResult;
import com.example.clustermanager.core.model.OperationType;

public interface INodeManager {

    OperationResult operateNode(ClusterRef clusterRef, String nodeId, OperationType operationType);
}
