package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ClusterRef;
import com.example.clustermanager.core.model.MessageScenario;
import com.example.clustermanager.core.model.MessageSimulationResult;

public interface IMessageWorkbench {

    MessageSimulationResult simulate(ClusterRef clusterRef, MessageScenario scenario);
}
