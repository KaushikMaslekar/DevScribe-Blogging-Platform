import type { Metadata } from "next";
import Link from "next/link";
import { Fira_Code } from "next/font/google";
import { ThemeToggle } from "@/components/theme-toggle";
import { AppProviders } from "@/providers/app-providers";
import { Button } from "@/components/ui/button";
import "./globals.css";

const firaCode = Fira_Code({
  variable: "--font-fira-code",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "DevScribe - Write. Collaborate. Publish.",
  description:
    "A developer-focused blogging platform with real-time collaboration, autosave, and markdown support. Share your technical knowledge with the community.",
  keywords: [
    "blogging",
    "developer",
    "collaboration",
    "markdown",
    "technical writing",
    "publishing",
  ],
  authors: [{ name: "DevScribe Team" }],
  creator: "DevScribe",
  publisher: "DevScribe",
  robots: "follow, index",
  openGraph: {
    type: "website",
    locale: "en_US",
    url: "https://devscribe.app",
    siteName: "DevScribe",
    title: "DevScribe - Write. Collaborate. Publish.",
    description:
      "A developer-focused blogging platform with real-time collaboration, autosave, and markdown support.",
    images: [
      {
        url: "https://devscribe.app/og-image.png",
        width: 1200,
        height: 630,
        alt: "DevScribe",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    site: "@devscribe",
    creator: "@devscribe",
    title: "DevScribe - Write. Collaborate. Publish.",
    description:
      "A developer-focused blogging platform with real-time collaboration, autosave, and markdown support.",
    images: ["https://devscribe.app/og-image.png"],
  },
  metadataBase: new URL("https://devscribe.app"),
  alternates: {
    canonical: "https://devscribe.app",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${firaCode.variable} h-full antialiased`}>
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="theme-color" content="#000000" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta
          name="apple-mobile-web-app-status-bar-style"
          content="black-translucent"
        />
        <link rel="icon" href="/favicon.ico" />
        <link rel="canonical" href="https://devscribe.app" />
      </head>
      <body className="min-h-full flex flex-col bg-background text-foreground">
        <AppProviders>
          <header className="sticky top-0 z-40 border-b border-border/60 bg-background/80 backdrop-blur-xl">
            <div className="mx-auto flex w-full max-w-7xl items-center justify-between gap-4 px-6 py-4 md:px-10">
              <Link href="/" className="group flex items-center gap-3">
                <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-primary text-primary-foreground shadow-sm transition-transform group-hover:scale-105">
                  D
                </span>
                <span>
                  <span className="block text-sm font-semibold tracking-[0.2em] text-muted-foreground uppercase">
                    DevScribe
                  </span>
                  <span className="block text-xs text-muted-foreground">
                    Write. collaborate. publish.
                  </span>
                </span>
              </Link>

              <span className="hidden items-center gap-2 rounded-full border border-border px-3 py-1 text-xs text-muted-foreground md:inline-flex">
                <span className="h-2 w-2 rounded-full bg-foreground animate-pulse" />
                Live workspace
              </span>

              <nav className="hidden items-center gap-2 md:flex">
                <Button asChild variant="ghost" size="sm">
                  <Link href="/feed">Feed</Link>
                </Button>
                <Button asChild variant="ghost" size="sm">
                  <Link href="/bookmarks">Bookmarks</Link>
                </Button>
                <Button asChild variant="ghost" size="sm">
                  <Link href="/dashboard">Dashboard</Link>
                </Button>
              </nav>

              <div className="flex items-center gap-2">
                <ThemeToggle />
                <Button asChild variant="outline" size="sm">
                  <Link href="/login">Sign in</Link>
                </Button>
                <Button asChild size="sm" className="hidden sm:inline-flex">
                  <Link href="/register">Create account</Link>
                </Button>
              </div>
            </div>
          </header>
          <div className="animate-fade-up">{children}</div>
        </AppProviders>
      </body>
    </html>
  );
}
