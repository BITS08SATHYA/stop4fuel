import type { Metadata } from "next";
import { Plus_Jakarta_Sans, JetBrains_Mono } from "next/font/google";
import "./globals.css";

const jakartaSans = Plus_Jakarta_Sans({
  variable: "--font-body",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700", "800"],
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
});

export const metadata: Metadata = {
  title: "StopForFuel",
  description: "Fuel Station Management System",
};

import { ThemeProvider } from "@/components/theme-provider";
import { AuthenticatedLayout } from "@/components/authenticated-layout";

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${jakartaSans.variable} ${jetbrainsMono.variable} antialiased flex h-screen overflow-hidden bg-background text-foreground`}
        style={{ fontFamily: "var(--font-body), system-ui, sans-serif" }}
      >
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          <AuthenticatedLayout>
            {children}
          </AuthenticatedLayout>
        </ThemeProvider>
      </body>
    </html>
  );
}
