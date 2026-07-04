package com.example.clustermanager.infrastructure.pseudo;

import java.util.HashSet;
import java.util.Set;

final class CidrAddressPool {

    private final int networkAddress;
    private final int broadcastAddress;
    private final Set<Integer> allocated = new HashSet<>();

    CidrAddressPool(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR: " + cidr);
        }
        int prefixLength = Integer.parseInt(parts[1]);
        int baseAddress = ipToInt(parts[0]);
        int mask = prefixLength == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - prefixLength));
        this.networkAddress = baseAddress & mask;
        this.broadcastAddress = networkAddress | ~mask;
    }

    synchronized String allocate() {
        for (int candidate = networkAddress + 10; candidate < broadcastAddress; candidate++) {
            if (allocated.add(candidate)) {
                return intToIp(candidate);
            }
        }
        throw new IllegalStateException("CIDR address pool exhausted");
    }

    synchronized String allocate(String requestedIpAddress) {
        int candidate = ipToInt(requestedIpAddress);
        if (candidate <= networkAddress || candidate >= broadcastAddress) {
            throw new IllegalArgumentException("Requested IP is outside CIDR range: " + requestedIpAddress);
        }
        if (!allocated.add(candidate)) {
            throw new IllegalArgumentException("Requested IP is already allocated: " + requestedIpAddress);
        }
        return requestedIpAddress;
    }

    synchronized void release(String ipAddress) {
        allocated.remove(ipToInt(ipAddress));
    }

    private static int ipToInt(String ipAddress) {
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ipAddress);
        }
        int value = 0;
        for (String octet : octets) {
            value = (value << 8) | Integer.parseInt(octet);
        }
        return value;
    }

    private static String intToIp(int value) {
        return "%d.%d.%d.%d".formatted(
                (value >>> 24) & 0xFF,
                (value >>> 16) & 0xFF,
                (value >>> 8) & 0xFF,
                value & 0xFF
        );
    }
}
