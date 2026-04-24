import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Following Feed | DevScribe",
  description:
    "Discover latest published posts from authors you follow on DevScribe. Your personalized reading experience.",
  robots: "index, follow",
  openGraph: {
    title: "Following Feed | DevScribe",
    description:
      "Latest published posts from authors you follow on DevScribe.",
    type: "website",
    siteName: "DevScribe",
  },
  twitter: {
    card: "summary",
    title: "Following Feed | DevScribe",
    description: "Discover latest posts from authors you follow.",
  },
};

export default function FeedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

