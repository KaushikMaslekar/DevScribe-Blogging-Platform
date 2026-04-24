import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Create Account | DevScribe",
  description:
    "Create a new DevScribe account to start writing and collaborating.",
  robots: "noindex, nofollow", // Auth page, should not be indexed
};

export default function RegisterLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

