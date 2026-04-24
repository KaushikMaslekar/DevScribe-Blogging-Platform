import { Metadata } from "next";

export const metadata: Metadata = {
  title: "Sign In | DevScribe",
  description: "Sign in to your DevScribe account to write and collaborate.",
  robots: "noindex, nofollow", // Auth page, should not be indexed
};

export default function LoginLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return children;
}

