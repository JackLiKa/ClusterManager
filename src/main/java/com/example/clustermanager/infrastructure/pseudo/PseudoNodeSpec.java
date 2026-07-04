package com.example.clustermanager.infrastructure.pseudo;

record PseudoNodeSpec(
        String nodeId,
        String displayName,
        String hostName,
        String role,
        int port
) {
}
