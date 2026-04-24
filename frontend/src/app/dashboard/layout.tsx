import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Dashboard | DevScribe - Writing Control Center",
  description:
    "Manage your drafts, published posts, collaborations, and autosave recovery in one place. Your personal writing control center.",
  robots: "noindex, nofollow", // Dashboard is private, should not be indexed
  openGraph: {
    title: "Dashboard | DevScribe",
    description:
      "Manage your drafts, published posts, and collaborations on DevScribe.",
    type: "website",
    siteName: "DevScribe",
    locale: "en_US",
  },
  twitter: {
    card: "summary",
    title: "Dashboard | DevScribe",
    description: "Your personal writing control center on DevScribe.",
  },
};

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

