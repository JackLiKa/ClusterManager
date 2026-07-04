<template>
  <main class="guide-shell">
    <section class="guide-card">
      <div class="guide-eyebrow">Quick Guide</div>
      <h1 class="guide-title">Cluster Manager 操作说明</h1>
      <div class="guide-grid">
        <article class="guide-step">
          <h2>1. 选择模式</h2>
          <p>顶部导航栏先切换到本地伪集群或真实集群，再进行节点录入、消息验证和运维操作。</p>
        </article>
        <article class="guide-step">
          <h2>2. 接入宿主节点</h2>
          <p>在本地伪集群里选择 <strong>Host Node</strong>，录入你电脑上已经启动的 NameServer 或 Broker 的地址与端口。</p>
        </article>
        <article class="guide-step">
          <h2>3. 添加虚拟节点</h2>
          <p>切换到 <strong>Virtual Node</strong> 后录入虚拟 IP，即可把 TAP 伪节点挂到同一个拓扑和消息工作台中。</p>
        </article>
        <article class="guide-step">
          <h2>4. 拓扑联调</h2>
          <p>拓扑图支持拖拽、缩放和悬停查看节点详情。消息工作台可选择 Broker 作为生产者和消费者完成链路验证。</p>
        </article>
      </div>
    </section>
  </main>
</template>
