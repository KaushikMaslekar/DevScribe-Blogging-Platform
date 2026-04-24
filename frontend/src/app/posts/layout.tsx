import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Posts | DevScribe",
  description: "Read and discover technical articles on DevScribe.",
  robots: "index, follow",
};

export default function PostsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

