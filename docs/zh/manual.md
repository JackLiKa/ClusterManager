# Cluster Manager 操作手册

## 1. 启动系统

```powershell
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

然后打开浏览器访问 `http://localhost:8080/`。

如果需要前端热更新，可单独执行：

```powershell
cd frontend
npm run dev
```

访问 `http://localhost:5173`，`/api`、`/ws`、`/guide` 会自动代理到后端。

## 2. 切换模式

顶部导航栏支持切换：

- **Local Pseudo（本地伪集群）**：本机模拟 NameServer、Broker、Proxy 和虚拟 IP。
- **Real Cluster（真实集群）**：录入真实 NameServer / Broker 信息，进行管理视图模拟。

建议操作顺序：

1. 先选择模式。
2. 再录入节点。
3. 最后检查拓扑、消息工作台和运维面板。

## 3. 添加服务

在右侧 **服务登记** 区域填写：

- 服务名称
- 角色（nameserver / broker-master / broker-slave / proxy）
- 主机名
- 地址
- 端口

### 3.1 伪集群模式

伪集群支持两类节点：

#### Host Node

用于接入本机或局域网内已经启动的 RocketMQ 节点，例如：

- NameServer：`127.0.0.1:9876`
- Broker：`127.0.0.1:10911`

特点：

- 使用真实地址和端口。
- 不启动本地伪进程。
- 进入拓扑图、运维表、日志流。
- 可参与真实 RocketMQ 的消息收发验证。

#### Virtual Node

用于创建 TAP 虚拟节点，例如：

- `10.77.0.40`
- `10.77.0.41`

特点：

- 使用虚拟 IP。
- 加入伪集群虚拟网络。
- 由本地伪节点运行时承载。
- 可与宿主节点出现在同一拓扑中。

示例 JSON：

```json
{
  "nodeId": "host-rmq-ns-01",
  "displayName": "Host NameServer 01",
  "role": "nameserver",
  "hostName": "localhost",
  "address": "127.0.0.1",
  "port": 9876,
  "labels": {
    "source": "console",
    "nodeKind": "HOST"
  }
}
```

```json
{
  "nodeId": "virt-rmq-broker-01",
  "displayName": "Virtual Broker 01",
  "role": "broker-master",
  "hostName": "virt-rmq-broker-01.local",
  "address": "10.77.0.40",
  "port": 19931,
  "labels": {
    "source": "console",
    "nodeKind": "VIRTUAL"
  }
}
```

### 3.2 真实集群模式

真实集群下直接填写 `IP:端口`，例如：

```json
{
  "nodeId": "real-ns-01",
  "displayName": "Real NameServer 01",
  "role": "nameserver",
  "hostName": "mq-real-01.local",
  "address": "192.168.50.78",
  "port": 9876
}
```

## 4. 拓扑检查

- NameServer、Broker Master、Broker Slave、Proxy 按角色分列展示。
- 如果节点状态不是 `RUNNING`，先在运维面板点击 **启动**。
- 拓扑图支持滚轮缩放、拖动画布、拖动单个节点、悬停查看详情。

## 5. RocketMQ 消息模拟

1. 确保至少有一个 Broker Master 或 Broker Slave 可用。
2. 在 **RocketMQ 消息工作台** 区域填写 Topic、Consumer Group 和消息数量。
3. 选择生产者和消费者节点。
4. 点击 **发送并验证**。

### 消息工作台规则

- 如果生产者或消费者中包含 `Host Node`，系统优先通过真实 RocketMQ NameServer 发消息并拉取确认。
- 如果全部都是 `Virtual Node`，系统走本地伪节点运行时完成模拟。

前提条件：

- 伪集群内至少要有一个 `Host Node` 类型的 NameServer。
- 否则真实 RocketMQ 消息桥接无法启动，系统会返回失败结果并提示原因。

## 6. 节点维护

运维面板支持：

- **启动**：拉起节点或标记手工登记服务为运行。
- **重启**：重启节点。
- **停止**：停止节点。
- **删除**：删除手工登记的服务（默认种子节点不可删除）。

## 7. 推荐验证流程

1. 启动本机的 RocketMQ NameServer 和 Broker。
2. 执行 `mvn spring-boot:run`。
3. 打开 `http://localhost:8080/`。
4. 切换到 **Local Pseudo** 模式。
5. 添加一个 `Host Node` 类型 NameServer。
6. 添加一个 `Host Node` 类型 Broker。
7. 如有需要再添加一个 `Virtual Node`。
8. 在拓扑图确认节点都出现，并能拖动、缩放、悬停查看详情。
9. 到消息工作台选择 Broker 发起验证。
10. 在日志面板确认新增、删除和消息验证结果。

## 8. 常见问题

- `localhost:8080` 打不开：确认后端是否成功启动。
- NameServer 添加失败：真实集群下建议直接填写 `IP:端口`。
- 拓扑图挤在一起：刷新页面或缩放画布。
- 消息模拟失败：先检查 Broker 是否处于 `RUNNING`，以及是否存在 `Host Node` 类型 NameServer。

## 9. 当前限制

- 默认种子节点不能删除。
- 没有 `Host Node` 类型 NameServer 时，宿主 RocketMQ 消息桥接不会工作。
- `Virtual Node` 的地址必须是合法且不冲突的虚拟 IP。
- 当前宿主节点的监控指标是占位值，不是来自 RocketMQ 的真实 runtime 指标。
- 手工登记的服务目前存储在内存中，重启后会丢失。

## 10. 后续建议

若要进一步生产化，建议继续推进：

1. 为手工服务登记增加持久化存储。
2. 为宿主 RocketMQ 桥接补充更精细的消费确认与超时重试。
3. 为拓扑图加入按角色分层布局和保存拖拽位置能力。
4. 为宿主节点补充更真实的运行指标采集。
