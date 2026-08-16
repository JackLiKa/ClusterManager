'use client'

/**
 * 動態限流卡片組件——展示本機安全消息量上限及計算依據。
 *
 * 職責：
 * - 頁面加載時調用 GET /api/clusters/rate-limit 獲取本機限流計算結果
 * - 顯示最大消息量限制（醒目大字）
 * - 展示計算公式各因子：CPU 核數、可用堆內存、可用磁盤
 * - 展示系統配置快照：物理內存、CPU 使用率
 * - 提示用戶：每台機器的數值不同，取決於實際硬件
 */

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { fetchRateLimit } from '@/lib/api'
import type { RateLimitResult } from '@/types/cluster'

export function RateLimitCard() {
  const [limit, setLimit] = useState<RateLimitResult | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    fetchRateLimit()
      .then((data) => {
        if (!cancelled) setLimit(data)
      })
      .catch(() => {
        // 限流信息加載失敗時靜默處理
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>動態限流</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-sm text-muted-foreground">計算中...</div>
        </CardContent>
      </Card>
    )
  }

  if (!limit) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>動態限流</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-sm text-muted-foreground">無法獲取限流信息</div>
        </CardContent>
      </Card>
    )
  }

  const p = limit.systemProfile

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          動態限流
          <Badge variant="outline" className="text-xs">本機配置</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* 最大消息量——醒目展示 */}
        <div className="rounded-md border bg-muted/20 p-4 text-center">
          <div className="text-3xl font-bold text-blue-600">{limit.maxMessages}</div>
          <div className="text-xs text-muted-foreground mt-1">最大安全消息量 / 批次</div>
        </div>

        {/* 計算公式各因子 */}
        <div className="space-y-2">
          <h4 className="text-xs font-semibold text-muted-foreground">計算因子</h4>
          <div className="grid grid-cols-3 gap-2 text-xs">
            <div className="rounded border p-2 text-center">
              <div className="font-bold text-lg">{p.logicalCores}</div>
              <div className="text-muted-foreground">CPU 核數</div>
              <div className="text-muted-foreground">×{limit.messagesPerCore}/核</div>
            </div>
            <div className="rounded border p-2 text-center">
              <div className="font-bold text-lg">{p.availableHeapMb}</div>
              <div className="text-muted-foreground">可用堆 (MB)</div>
              <div className="text-muted-foreground">×{limit.messagesPerMb}/MB</div>
            </div>
            <div className="rounded border p-2 text-center">
              <div className="font-bold text-lg">{p.availableDiskGb.toFixed(1)}</div>
              <div className="text-muted-foreground">可用磁盤 (GB)</div>
              <div className="text-muted-foreground">×{limit.messagesPerGb}/GB</div>
            </div>
          </div>
        </div>

        {/* 系統配置快照 */}
        <div className="space-y-1 text-xs">
          <h4 className="font-semibold text-muted-foreground">系統配置</h4>
          <div className="flex justify-between">
            <span className="text-muted-foreground">物理內存</span>
            <span className="font-mono">
              {(p.freePhysicalMb / 1024).toFixed(1)} / {(p.totalPhysicalMb / 1024).toFixed(1)} GB 可用
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">JVM 最大堆</span>
            <span className="font-mono">{p.maxHeapMb} MB</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">系統 CPU 使用率</span>
            <span className="font-mono">{p.systemCpuLoad.toFixed(1)}%</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">安全係數</span>
            <span className="font-mono">{limit.safetyCoefficient}（留 30% 餘量）</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">絕對上限</span>
            <span className="font-mono">{limit.baselineCeiling}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">最低保底</span>
            <span className="font-mono">{limit.minFloor}</span>
          </div>
        </div>

        {/* 說明 */}
        <p className="text-xs text-muted-foreground italic">
          此數值根據本機硬件動態計算，每台電腦看到的上限可能不同。
          超過此限制的請求將被後端拒絕，防止本地系統卡死崩潰。
        </p>
      </CardContent>
    </Card>
  )
}
