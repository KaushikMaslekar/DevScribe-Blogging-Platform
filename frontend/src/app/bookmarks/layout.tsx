import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Bookmarks | DevScribe",
  description: "Your saved bookmarked articles on DevScribe.",
  robots: "noindex, nofollow", // Private page
};

export default function BookmarksLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

