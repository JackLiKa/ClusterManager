package com.example.clustermanager.core.model;

/**
 * 节点状态枚举。
 *
 * <p>位于六边形架构的领域核心层，定义节点生命周期的所有可能状态。
 * 该枚举由 {@link ClusterNode#status()} 引用，供前端状态指示灯渲染与
 * 节点操作（{@code INodeManager}）的前置校验使用。
 * 作为 Java 枚举，其实例天然不可变且线程安全。
 */
public enum NodeStatus {
    /** 启动中：节点正在初始化，尚未就绪对外提供服务。 */
    STARTING,
    /** 运行中：节点正常工作，可接受请求。 */
    RUNNING,
    /** 已停止：节点已被主动停止，不占用资源。 */
    STOPPED,
    /** 降级：节点仍在运行但部分功能异常，如主从复制延迟过高。 */
    DEGRADED,
    /** 故障：节点运行失败或不可达，需要人工介入或重启。 */
    FAILED
}
