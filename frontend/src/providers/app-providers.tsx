"use client";

import { ReactNode } from "react";
import { PostRealtimeListener } from "@/providers/post-realtime-listener";
import { QueryProvider } from "@/providers/query-provider";

export function AppProviders({ children }: { children: ReactNode }) {
  return (
    <QueryProvider>
      <PostRealtimeListener />
      {children}
    </QueryProvider>
  );
}
