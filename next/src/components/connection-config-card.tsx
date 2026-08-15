'use client'

/**
 * RocketMQ 連接配置面板——在網頁中自定義輸入並設置本地 MQ 環境變量。
 *
 * 職責：
 * - 顯示當前 RocketMQ 連接配置（NameServer 地址、超時、消費者組前綴）
 * - 允許用戶修改配置並保存（立即生效，持久化到文件）
 * - 提供常用地址快捷輸入（127.0.0.1:9876 等）
 *
 * 與後端的交互：
 * - GET /api/clusters/settings/rocketmq（獲取配置）
 * - PUT /api/clusters/settings/rocketmq（更新配置）
 */

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { fetchRocketMqConfig, updateRocketMqConfig } from '@/lib/api'
import type { RocketMqConnectionConfig } from '@/types/cluster'

export function ConnectionConfigCard() {
  const [config, setConfig] = useState<RocketMqConnectionConfig | null>(null)
  const [nameServersText, setNameServersText] = useState('')
  const [sendMsgTimeoutMs, setSendMsgTimeoutMs] = useState(10000)
  const [consumeTimeoutSeconds, setConsumeTimeoutSeconds] = useState(15)
  const [consumerGroupPrefix, setConsumerGroupPrefix] = useState('mqcluster')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  // 加載當前配置
  useEffect(() => {
    let cancelled = false
    void fetchRocketMqConfig()
      .then((data) => {
        if (cancelled) return
        setConfig(data)
        setNameServersText(data.nameServers.join(', '))
        setSendMsgTimeoutMs(data.sendMsgTimeoutMs)
        setConsumeTimeoutSeconds(data.consumeTimeoutSeconds)
        setConsumerGroupPrefix(data.consumerGroupPrefix)
      })
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : '加載配置失敗')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  /** 保存配置 */
  async function handleSave() {
    setSaving(true)
    setError(null)
    setSuccess(null)

    try {
      const nameServers = nameServersText
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)

      const updated = await updateRocketMqConfig({
        nameServers,
        sendMsgTimeoutMs,
        consumeTimeoutSeconds,
        consumerGroupPrefix,
      })
      setConfig(updated)
      setNameServersText(updated.nameServers.join(', '))
      setSuccess('配置已保存並立即生效')
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存配置失敗')
    } finally {
      setSaving(false)
    }
  }

  /** 快捷填入常用地址 */
  function fillPreset(addr: string) {
    setNameServersText(addr)
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>RocketMQ 連接配置</CardTitle>
          {config && (
            <Badge variant={config.nameServers.length > 0 ? 'default' : 'secondary'}>
              {config.nameServers.length > 0 ? `已配置 ${config.nameServers.length} 個 NameServer` : '未配置'}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {loading && <div className="text-sm text-muted-foreground">加載配置中...</div>}

        {!loading && (
          <>
            <div className="space-y-2">
              <Label htmlFor="nameServers">
                NameServer 地址
                <span className="ml-2 text-xs text-muted-foreground">多個地址用逗號分隔</span>
              </Label>
              <Input
                id="nameServers"
                value={nameServersText}
                onChange={(e) => setNameServersText(e.target.value)}
                placeholder="例如: 127.0.0.1:9876 或 192.168.1.100:9876,192.168.1.101:9876"
              />
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => fillPreset('127.0.0.1:9876')}
                >
                  127.0.0.1:9876
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => fillPreset('localhost:9876')}
                >
                  localhost:9876
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => fillPreset('')}
                >
                  清空
                </Button>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="sendMsgTimeoutMs">發送超時 (ms)</Label>
                <Input
                  id="sendMsgTimeoutMs"
                  type="number"
                  min={1000}
                  max={600000}
                  value={sendMsgTimeoutMs}
                  onChange={(e) => setSendMsgTimeoutMs(Number(e.target.value) || 10000)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="consumeTimeoutSeconds">消費超時 (秒)</Label>
                <Input
                  id="consumeTimeoutSeconds"
                  type="number"
                  min={1}
                  max={300}
                  value={consumeTimeoutSeconds}
                  onChange={(e) => setConsumeTimeoutSeconds(Number(e.target.value) || 15)}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="consumerGroupPrefix">消費者組前綴</Label>
              <Input
                id="consumerGroupPrefix"
                value={consumerGroupPrefix}
                onChange={(e) => setConsumerGroupPrefix(e.target.value)}
                placeholder="例如: mqcluster"
              />
            </div>

            <Button onClick={handleSave} disabled={saving} className="w-full">
              {saving ? '保存中...' : '保存並生效'}
            </Button>

            {error && (
              <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>
            )}
            {success && (
              <div className="rounded-md bg-green-500/10 p-3 text-sm text-green-600 dark:text-green-400">
                {success}
              </div>
            )}

            <div className="rounded-md bg-muted/50 p-3 text-xs text-muted-foreground">
              <p className="font-semibold mb-1">說明</p>
              <ul className="space-y-1 list-disc list-inside">
                <li>配置保存後立即生效，下次消息收發使用新配置</li>
                <li>配置持久化到後端文件，重啟後保留</li>
                <li>NameServer 地址為空時，PSEUDO 模式使用嵌入式集群</li>
                <li>配置後可在 PSEUDO 模式下與本地真實 RocketMQ 通信</li>
              </ul>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  )
}
