# Cluster Manager

统一管理本地伪集群、宿主 RocketMQ 节点与真实 RocketMQ 集群的 Spring Boot + Vue 3 控制台。

## 当前能力

- 统一抽象 `PSEUDO` 与 `REAL` 两种集群模式。
- 支持 RocketMQ 拓扑展示、节点启停、监控、日志和消息模拟。
- 支持在伪集群与真实集群模式下手工添加、删除服务。
- 支持录入虚拟 IP、真实 IP、NameServer、角色、主机名、端口等服务信息。
- 伪集群模式支持同时接入宿主 `HOST` 节点和 TAP `VIRTUAL` 节点。
- 宿主节点可通过真实 RocketMQ 参与消息收发验证。
- 开发模式和打包模式下都可以直接访问 `http://localhost:8080`。

## 运行方式

开发模式：

```powershell
cd frontend
npm install
cd ..
mvn -s .mvn/local-settings.xml -gs .mvn/local-settings.xml spring-boot:run
```

说明：

- `spring-boot:run` 现在会先构建前端并复制到 `target/classes/static`。
- 因此后端启动后，直接访问 `http://localhost:8080/` 就能打开完整页面。
- 如果需要前端热更新，仍可额外执行 `cd frontend && npm run dev`，访问 `http://localhost:5173`。

打包运行：

```powershell
mvn -s .mvn/local-settings.xml -gs .mvn/local-settings.xml package
java -jar target\cluster-manager-0.0.1-SNAPSHOT.jar
```

## 手工服务管理

运维面板已支持：

- 添加 NameServer、Broker Master、Broker Slave、Proxy 等服务
- 删除手工登记的服务
- 在伪集群下指定宿主地址或虚拟 IP
- 在真实集群下指定真实 IP / NameServer 地址
- 新增服务后自动纳入拓扑图、监控表、日志流与 RocketMQ 消息模拟

说明：

- 伪集群默认内置的种子节点不能删除。
- 若要让伪集群通过真实 RocketMQ 收发消息，需至少登记一个 `HOST` 类型的 NameServer。
- 真实集群当前仍基于 `MockRocketMqAdminClient`，手工添加的服务会以“可模拟、可展示、可操作”的方式参与控制台流程。

## 关键文件

- `src/main/java/com/example/clustermanager/api/controller/ClusterController.java`
- `src/main/java/com/example/clustermanager/core/port/IClusterProvider.java`
- `src/main/java/com/example/clustermanager/infrastructure/pseudo/PseudoClusterProvider.java`
- `src/main/java/com/example/clustermanager/infrastructure/rocketmq/RocketMqClusterProvider.java`
- `frontend/src/components/ClusterTopologyCard.vue`
- `frontend/src/components/OperationsPanel.vue`
