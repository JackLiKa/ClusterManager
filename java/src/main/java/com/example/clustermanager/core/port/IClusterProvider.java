package com.example.clustermanager.core.port;

import com.example.clustermanager.core.model.ProviderDescriptor;

/**
 * 集群 Provider 聚合端口（六边形架构的出站端口）。
 *
 * <p>这是领域核心层定义的聚合接口，将拓扑读取、节点管理、消息模拟、
 * 监控网关、服务登记五个能力端口聚合为一个统一入口。
 * infrastructure 层的适配器（如 {@code PseudoClusterProvider}、
 * {@code RocketMqClusterProvider}）实现该接口，向领域层提供具体能力。
 *
 * <p>该端口本身不引入任何外部依赖；适配器实现可选择以组合或继承方式
 * 满足这五个子端口契约。{@link #descriptor()} 返回的描述符供
 * {@code ClusterProviderRegistry} 按 {@code (ClusterMode, MiddlewareType)} 注册与解析。
 */
public interface IClusterProvider extends ITopologyReader, INodeManager, IMessageWorkbench, IMonitoringGateway, IServiceRegistry {

    /**
     * 返回该 Provider 的描述符。
     *
     * @return Provider 描述符，包含标识、展示名称、支持的集群模式与中间件类型
     */
    ProviderDescriptor descriptor();
}
