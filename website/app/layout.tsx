import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'MPad — 把手机变成 Windows 游戏手柄',
  description:
    'MPad 让 Android 手机通过局域网或蓝牙连接 Windows 电脑，成为可自定义布局的游戏手柄。',
  icons: { icon: '/mpad-icon.png' },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
