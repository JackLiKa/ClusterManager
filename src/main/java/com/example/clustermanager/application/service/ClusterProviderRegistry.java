package com.example.clustermanager.application.service;

import com.example.clustermanager.application.model.ClusterSelection;
import com.example.clustermanager.core.model.ProviderDescriptor;
import com.example.clustermanager.core.port.IClusterProvider;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ClusterProviderRegistry {

    private final List<IClusterProvider> providers;

    public ClusterProviderRegistry(List<IClusterProvider> providers) {
        this.providers = providers;
    }

    public IClusterProvider resolve(ClusterSelection selection) {
        return providers.stream()
                .filter(provider -> provider.descriptor().mode() == selection.mode())
                .filter(provider -> provider.descriptor().middleware() == selection.middleware())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No cluster provider for mode=%s, middleware=%s".formatted(
                                selection.mode(),
                                selection.middleware()
                        )));
    }

    public List<ProviderDescriptor> listProviders() {
        return providers.stream()
                .map(IClusterProvider::descriptor)
                .toList();
    }
}
