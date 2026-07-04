# Cluster Manager 操作教程

1. 启动系统

- 运行 `mvn -s .mvn/local-settings.xml -gs .mvn/local-settings.xml spring-boot:run`
- 浏览器访问 `http://localhost:8080/`

2. 切换模式

- `本地伪集群`：用于本机模拟 NameServer、Broker、Proxy 和虚拟 IP
- `真实集群`：用于录入真实 NameServer / Broker 信息，并进行 RocketMQ 管理视图模拟

3. 添加服务

- 在右侧 `服务登记` 区域填写服务名称、角色、主机名、地址和端口
- 真实集群支持直接填写 `IP:端口`
- 伪集群请填写虚拟 IP，例如 `10.77.0.40`
- 点击 `添加服务`

4. 拓扑检查

- NameServer、Broker Master、Broker Slave、Proxy 会按列分开展示
- 如果节点状态不是 `RUNNING`，先在运维面板点击 `启动`

5. RocketMQ 消息模拟

- 先确保至少有一个 Broker Master 或 Broker Slave 可用
- 在 `RocketMQ 消息模拟` 区域填写 Topic、Consumer Group 和消息数量
- 选择生产者和消费者节点
- 点击 `发送并验证`

6. 节点维护

- `启动`：拉起节点或手工登记服务
- `重启`：重启节点
- `停止`：停止节点
- `删除`：删除手工登记的服务

7. 常见问题

- `localhost:8080` 打不开：确认后端是否成功启动
- NameServer 添加失败：真实集群下建议直接填写 `IP:端口`
- 拓扑图挤在一起：当前版本已经按角色稳定分列，如果仍异常请刷新页面
- 消息模拟失败：先检查 Broker 是否处于 `RUNNING`
