package com.example.clustermanager.infrastructure.pseudo;

import java.util.HashSet;
import java.util.Set;

/**
 * CIDR IP 地址池——從指定 CIDR 網段中分配和回收 IPv4 地址。
 *
 * <p>被 {@link TapVirtualNetwork} 的 {@code SegmentState} 持有，為每個虛擬網絡段
 * 管理獨立的 IP 地址池。支持自動分配和指定 IP 分配兩種模式。
 *
 * <p><b>線程安全</b>：所有分配/釋放方法均用 {@code synchronized} 保護，
 * 因為 {@code allocated} 集合的「檢查-添加」操作需要原子性。
 *
 * <p><b>生命週期</b>：隨 {@code SegmentState} 創建而創建，隨段銷毀而丟棄。
 * 包級可見，不對外暴露。
 */
final class CidrAddressPool {

    /** 網絡地址（子網起始地址的整數表示），分配從 networkAddress + 10 開始以避開網絡地址和低段保留地址 */
    private final int networkAddress;
    /** 廣播地址（子網結束地址的整數表示），分配候選不超過此值 */
    private final int broadcastAddress;
    /** 已分配 IP 的整數表示集合，用於去重 */
    private final Set<Integer> allocated = new HashSet<>();

    /**
     * 從 CIDR 字串構建地址池。
     *
     * @param cidr CIDR 表示法（如 "10.77.0.0/24"）
     * @throws IllegalArgumentException CIDR 格式非法時拋出
     */
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

    /**
     * 自動分配一個空閒 IP。從 networkAddress + 10 開始順序查找第一個未分配的地址。
     *
     * @return 分配到的 IPv4 地址字串
     * @throws IllegalStateException 地址池耗盡時拋出
     */
    synchronized String allocate() {
        for (int candidate = networkAddress + 10; candidate < broadcastAddress; candidate++) {
            if (allocated.add(candidate)) {
                return intToIp(candidate);
            }
        }
        throw new IllegalStateException("CIDR address pool exhausted");
    }

    /**
     * 分配指定的 IP 地址。校驗地址在 CIDR 範圍內且未被佔用。
     *
     * @param requestedIpAddress 請求的 IPv4 地址
     * @return 分配成功的 IP 地址（與請求一致）
     * @throws IllegalArgumentException 地址超出範圍或已被分配時拋出
     */
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

    /**
     * 釋放一個已分配的 IP 地址回池。
     *
     * @param ipAddress 待釋放的 IPv4 地址
     */
    synchronized void release(String ipAddress) {
        allocated.remove(ipToInt(ipAddress));
    }

    /**
     * 將 IPv4 點分字串轉換為整數表示。
     *
     * @param ipAddress IPv4 地址字串（如 "10.77.0.1"）
     * @return 32 位無符號整數表示
     * @throws IllegalArgumentException 格式非法時拋出
     */
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

    /**
     * 將整數表示轉換回 IPv4 點分字串。
     *
     * @param value 32 位整數表示
     * @return IPv4 地址字串
     */
    private static String intToIp(int value) {
        return "%d.%d.%d.%d".formatted(
                (value >>> 24) & 0xFF,
                (value >>> 16) & 0xFF,
                (value >>> 8) & 0xFF,
                value & 0xFF
        );
    }
}
