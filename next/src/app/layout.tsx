import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import './globals.css'
import { SiteHeader } from '@/components/site-header'

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
})

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
})

export const metadata: Metadata = {
  title: 'MQCluster — MQ 集群學習平台',
  description: 'Local-first MQ cluster learning platform — simulate real RocketMQ clusters in your browser on a single machine.',
}

/**
 * 根佈局——所有頁面共享的 HTML 外殼。
 *
 * 包含：
 * - 字體加載（Geist Sans + Geist Mono）
 * - 全局樣式（globals.css，含 Tailwind v4 指令）
 * - 站點頂部導航欄（SiteHeader）
 */
export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html
      lang="zh-CN"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <SiteHeader />
        <main className="flex-1">{children}</main>
      </body>
    </html>
  )
}
