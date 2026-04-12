"use client";

import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  subscribeToPostRealtime,
  type PostRealtimeEvent,
} from "@/lib/post-realtime";

const REALTIME_REFRESH_DEBOUNCE_MS = 400;

export function PostRealtimeListener() {
  const queryClient = useQueryClient();
  const pendingSlugsRef = useRef<Set<string>>(new Set());
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const pendingSlugs = pendingSlugsRef.current;

    const flushInvalidations = () => {
      timerRef.current = null;

      queryClient.invalidateQueries({ queryKey: ["posts"] });

      pendingSlugs.forEach((slug) => {
        queryClient.invalidateQueries({ queryKey: ["post", slug] });
      });

      pendingSlugs.clear();
    };

    const scheduleInvalidation = (event: PostRealtimeEvent) => {
      if (event.slug) {
        pendingSlugs.add(event.slug);
      }

      if (timerRef.current) {
        return;
      }

      timerRef.current = setTimeout(
        flushInvalidations,
        REALTIME_REFRESH_DEBOUNCE_MS,
      );
    };

    const unsubscribe = subscribeToPostRealtime(scheduleInvalidation);

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }

      pendingSlugs.clear();
      unsubscribe?.();
    };
  }, [queryClient]);

  return null;
}
