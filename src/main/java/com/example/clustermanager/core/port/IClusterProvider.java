package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ProviderDescriptor;

public interface IClusterProvider extends ITopologyReader, INodeManager, IMessageWorkbench, IMonitoringGateway, IServiceRegistry {

    ProviderDescriptor descriptor();
}
