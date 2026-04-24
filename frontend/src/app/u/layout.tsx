import { Metadata } from "next";

export const metadata: Metadata = {
  title: "User Profile | DevScribe",
  description: "User profile and published posts on DevScribe.",
  robots: "index, follow",
};

export default function UsersLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

