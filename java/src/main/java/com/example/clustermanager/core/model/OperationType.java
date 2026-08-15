package com.example.clustermanager.core.model;

/**
 * 节点操作类型枚举。
 *
 * <p>位于六边形架构的领域核心层，定义可对节点执行的生命周期操作。
 * 该枚举由 {@code INodeManager#operateNode} 接收，驱动适配器执行对应操作。
 * 作为 Java 枚举，其实例天然不可变且线程安全。
 */
public enum OperationType {
    /** 启动节点：将处于 {@link NodeStatus#STOPPED} 或 {@link NodeStatus#FAILED} 的节点拉起。 */
    START,
    /** 停止节点：将运行中的节点优雅关闭。 */
    STOP,
    /** 重启节点：先停止再启动，用于恢复异常状态或应用配置变更。 */
    RESTART
}
