'use client'

/**
 * 操作面板組件——節點管理、服務登記、活動日誌。
 *
 * 職責：
 * - KPI 條：展示節點總數、運行中、已停止、失敗數量
 * - 節點操作表：列出所有節點，支持啟動/停止/重啟操作
 * - 服務登記表單：手動登記新節點到集群
 * - 活動日誌：展示最近的審計日誌條目
 *
 * 與後端的交互：
 * - POST /api/clusters/{...}/nodes/{nodeId}/operations（啟停節點）
 * - POST /api/clusters/{...}/services（登記服務）
 * - DELETE /api/clusters/{...}/services/{nodeId}（刪除服務）
 * - GET /api/clusters/{...}/logs（拉取日誌，也通過 STOMP 實時推送）
 */

import { useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ScrollArea } from '@/components/ui/scroll-area'
import { operateNode, registerService, deleteService } from '@/lib/api'
import type {
  ClusterSelection,
  ClusterTopology,
  LogEntry,
  NodeStatus,
  OperationType,
} from '@/types/cluster'

interface OperationsPanelProps {
  selection: ClusterSelection
  topology: ClusterTopology | null
  logs: LogEntry[]
  onTopologyRefresh: () => void
}

/** 節點狀態到 Badge variant 的映射 */
function statusBadge(status: NodeStatus) {
  switch (status) {
    case 'RUNNING':
      return <Badge variant="default">RUNNING</Badge>
    case 'STARTING':
      return <Badge variant="secondary">STARTING</Badge>
    case 'STOPPED':
      return <Badge variant="outline">STOPPED</Badge>
    case 'FAILED':
      return <Badge variant="destructive">FAILED</Badge>
  }
}

export function OperationsPanel({
  selection,
  topology,
  logs,
  onTopologyRefresh,
}: OperationsPanelProps) {
  // 操作中的節點 ID（用於禁用按鈕）
  const [operatingNodeId, setOperatingNodeId] = useState<string | null>(null)
  // 服務登記表單狀態
  const [showRegForm, setShowRegForm] = useState(false)
  const [regForm, setRegForm] = useState({
    nodeId: '',
    displayName: '',
    role: 'broker-master',
    hostName: '',
    address: '',
    port: 10911,
  })
  const [regError, setRegError] = useState<string | null>(null)

  /** 執行節點操作 */
  async function handleOperate(nodeId: string, operation: OperationType) {
    setOperatingNodeId(nodeId)
    try {
      await operateNode(selection, nodeId, operation)
      onTopologyRefresh()
    } catch (err) {
      alert(err instanceof Error ? err.message : '操作失敗')
    } finally {
      setOperatingNodeId(null)
    }
  }

  /** 提交服務登記 */
  async function handleRegister() {
    setRegError(null)
    if (!regForm.nodeId.trim() || !regForm.displayName.trim() || !regForm.address.trim()) {
      setRegError('節點 ID、顯示名稱、地址不能為空')
      return
    }
    try {
      await registerService(selection, {
        nodeId: regForm.nodeId.trim(),
        displayName: regForm.displayName.trim(),
        role: regForm.role,
        hostName: regForm.hostName.trim() || regForm.nodeId.trim(),
        address: regForm.address.trim(),
        port: regForm.port,
      })
      setRegForm({
        nodeId: '',
        displayName: '',
        role: 'broker-master',
        hostName: '',
        address: '',
        port: 10911,
      })
      setShowRegForm(false)
      onTopologyRefresh()
    } catch (err) {
      setRegError(err instanceof Error ? err.message : '登記失敗')
    }
  }

  /** 刪除服務節點 */
  async function handleDelete(nodeId: string) {
    if (!confirm(`確認刪除節點 ${nodeId}？`)) return
    try {
      await deleteService(selection, nodeId)
      onTopologyRefresh()
    } catch (err) {
      alert(err instanceof Error ? err.message : '刪除失敗')
    }
  }

  // KPI 統計
  const nodes = topology?.nodes ?? []
  const running = nodes.filter((n) => n.status === 'RUNNING').length
  const stopped = nodes.filter((n) => n.status === 'STOPPED').length
  const failed = nodes.filter((n) => n.status === 'FAILED').length

  return (
    <div className="space-y-4">
      {/* KPI 條 */}
      <div className="grid grid-cols-4 gap-3">
        <Card>
          <CardContent className="p-4 text-center">
            <div className="text-2xl font-bold">{nodes.length}</div>
            <div className="text-xs text-muted-foreground">總節點</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <div className="text-2xl font-bold text-green-600">{running}</div>
            <div className="text-xs text-muted-foreground">運行中</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <div className="text-2xl font-bold text-gray-500">{stopped}</div>
            <div className="text-xs text-muted-foreground">已停止</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 text-center">
            <div className="text-2xl font-bold text-red-600">{failed}</div>
            <div className="text-xs text-muted-foreground">失敗</div>
          </CardContent>
        </Card>
      </div>

      {/* 節點操作表 */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>節點管理</CardTitle>
          <Button size="sm" variant="outline" onClick={() => setShowRegForm(!showRegForm)}>
            {showRegForm ? '取消' : '+ 登記節點'}
          </Button>
        </CardHeader>
        <CardContent>
          {/* 服務登記表單 */}
          {showRegForm && (
            <div className="mb-4 rounded-md border p-4 space-y-3">
              <h4 className="text-sm font-semibold">登記新節點</h4>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <Label className="text-xs">節點 ID</Label>
                  <Input
                    value={regForm.nodeId}
                    onChange={(e) => setRegForm({ ...regForm, nodeId: e.target.value })}
                    placeholder="例如: rmq-broker-02"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">顯示名稱</Label>
                  <Input
                    value={regForm.displayName}
                    onChange={(e) => setRegForm({ ...regForm, displayName: e.target.value })}
                    placeholder="例如: Broker-02"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">角色</Label>
                  <Select
                    value={regForm.role}
                    onValueChange={(v) => setRegForm({ ...regForm, role: v ?? 'broker-master' })}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="nameserver">NameServer</SelectItem>
                      <SelectItem value="broker-master">Broker Master</SelectItem>
                      <SelectItem value="broker-slave">Broker Slave</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">主機名</Label>
                  <Input
                    value={regForm.hostName}
                    onChange={(e) => setRegForm({ ...regForm, hostName: e.target.value })}
                    placeholder="留空則使用節點 ID"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">地址</Label>
                  <Input
                    value={regForm.address}
                    onChange={(e) => setRegForm({ ...regForm, address: e.target.value })}
                    placeholder="例如: 127.0.0.1"
                  />
                </div>
                <div className="space-y-1">
                  <Label className="text-xs">端口</Label>
                  <Input
                    type="number"
                    value={regForm.port}
                    onChange={(e) =>
                      setRegForm({ ...regForm, port: Number(e.target.value) || 10911 })
                    }
                  />
                </div>
              </div>
              {regError && (
                <div className="text-sm text-destructive">{regError}</div>
              )}
              <Button size="sm" onClick={handleRegister}>
                確認登記
              </Button>
            </div>
          )}

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>節點</TableHead>
                <TableHead>角色</TableHead>
                <TableHead>狀態</TableHead>
                <TableHead>VIP</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {nodes.map((node) => (
                <TableRow key={node.nodeId}>
                  <TableCell className="font-medium">{node.displayName}</TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {node.labels?.role ?? '-'}
                  </TableCell>
                  <TableCell>{statusBadge(node.status)}</TableCell>
                  <TableCell className="font-mono text-xs">
                    {node.virtualIp ?? '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      {node.status === 'RUNNING' ? (
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={operatingNodeId === node.nodeId}
                          onClick={() => handleOperate(node.nodeId, 'STOP')}
                        >
                          停止
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          variant="default"
                          disabled={operatingNodeId === node.nodeId}
                          onClick={() => handleOperate(node.nodeId, 'START')}
                        >
                          啟動
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="ghost"
                        disabled={operatingNodeId === node.nodeId}
                        onClick={() => handleOperate(node.nodeId, 'RESTART')}
                      >
                        重啟
                      </Button>
                      {node.labels?.managed === 'true' && (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-destructive"
                          onClick={() => handleDelete(node.nodeId)}
                        >
                          刪除
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 活動日誌 */}
      <Card>
        <CardHeader>
          <CardTitle>活動日誌</CardTitle>
        </CardHeader>
        <CardContent>
          <ScrollArea className="h-[200px]">
            <div className="space-y-1">
              {logs.length === 0 ? (
                <div className="text-sm text-muted-foreground">暫無日誌</div>
              ) : (
                logs.map((log, i) => (
                  <div key={i} className="flex items-start gap-2 rounded border p-2 text-xs">
                    <Badge
                      variant={
                        log.level === 'ERROR'
                          ? 'destructive'
                          : log.level === 'WARN'
                            ? 'secondary'
                            : 'outline'
                      }
                    >
                      {log.level}
                    </Badge>
                    <span className="font-mono text-muted-foreground">
                      {new Date(log.timestamp).toLocaleTimeString()}
                    </span>
                    <span className="font-medium">{log.nodeId}</span>
                    <span className="flex-1">{log.message}</span>
                  </div>
                ))
              )}
            </div>
          </ScrollArea>
        </CardContent>
      </Card>
    </div>
  )
}
