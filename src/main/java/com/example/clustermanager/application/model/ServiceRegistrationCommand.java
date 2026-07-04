package com.example.clustermanager.application.model;

import com.example.clustermanager.core.model.ServiceRegistration;

public record ServiceRegistrationCommand(
        ClusterSelection cluster,
        ServiceRegistration service
) {
}
