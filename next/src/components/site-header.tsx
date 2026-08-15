'use client'

/**
 * 站點頂部導航欄。
 *
 * 職責：
 * - 展示應用標題和 logo
 * - 提供主頁和學習指南的導航鏈接
 * - 使用 Next.js usePathname 高亮當前頁面
 */

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export function SiteHeader() {
  const pathname = usePathname()

  return (
    <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container mx-auto flex h-14 items-center px-4">
        <Link href="/" className="mr-8 flex items-center gap-2 font-bold">
          <span className="text-lg">MQCluster</span>
          <span className="text-xs text-muted-foreground">MQ 集群學習平台</span>
        </Link>
        <nav className="flex items-center gap-2">
          <Link
            href="/"
            className={cn(
              buttonVariants({ variant: pathname === '/' ? 'default' : 'ghost', size: 'sm' }),
            )}
          >
            儀表盤
          </Link>
          <Link
            href="/guide"
            className={cn(
              buttonVariants({
                variant: pathname === '/guide' ? 'default' : 'ghost',
                size: 'sm',
              }),
            )}
          >
            學習指南
          </Link>
        </nav>
      </div>
    </header>
  )
}
