import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { autosavePost } from "@/lib/post-api";
import type { AutosaveState } from "@/types/post";

const AUTOSAVE_STORAGE_KEY = "devscribe.autosave.draft.v1";
const DEBOUNCE_MS = 5000;
const MAX_RETRY_DELAY_MS = 30000;

export interface DraftSnapshot {
  postId: number | null;
  title: string;
  excerpt: string;
  markdownContent: string;
  scheduledPublishAt: string;
  tagsInput: string;
  revision: number;
  updatedAt: string;
}

interface UseAutosaveDraftParams {
  enabled: boolean;
  title: string;
  excerpt: string;
  markdownContent: string;
  scheduledPublishAt: string;
  tagsInput: string;
  postId: number | null;
  initialRevision?: number;
  onPostIdChange: (postId: number) => void;
}

interface UseAutosaveDraftResult {
  saveState: AutosaveState;
  lastSavedAt: string | null;
  setBaseRevision: (revision: number) => void;
}

export function readDraftSnapshot(): DraftSnapshot | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    const raw = window.localStorage.getItem(AUTOSAVE_STORAGE_KEY);
    if (!raw) {
      return null;
    }

    const parsed = JSON.parse(raw) as DraftSnapshot;
    if (!parsed || typeof parsed !== "object") {
      return null;
    }

    return parsed;
  } catch {
    return null;
  }
}

export function clearDraftSnapshot(): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(AUTOSAVE_STORAGE_KEY);
}

export function useAutosaveDraft({
  enabled,
  title,
  excerpt,
  markdownContent,
  scheduledPublishAt,
  tagsInput,
  postId,
  initialRevision = 0,
  onPostIdChange,
}: UseAutosaveDraftParams): UseAutosaveDraftResult {
  const [saveState, setSaveState] = useState<AutosaveState>("idle");
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null);

  const revisionRef = useRef(initialRevision);
  const retryCountRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const runAutosaveRef = useRef<(() => Promise<void>) | null>(null);

  const normalizedTags = useMemo(
    () =>
      tagsInput
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean),
    [tagsInput],
  );

  const persistSnapshot = useCallback(
    (revision: number) => {
      if (typeof window === "undefined") {
        return;
      }

      const snapshot: DraftSnapshot = {
        postId,
        title,
        excerpt,
        markdownContent,
        scheduledPublishAt,
        tagsInput,
        revision,
        updatedAt: new Date().toISOString(),
      };

      window.localStorage.setItem(
        AUTOSAVE_STORAGE_KEY,
        JSON.stringify(snapshot),
      );
    },
    [postId, title, excerpt, markdownContent, scheduledPublishAt, tagsInput],
  );

  const runAutosave = useCallback(async () => {
    if (!enabled) {
      return;
    }

    const hasContent = Boolean(title.trim() || markdownContent.trim());
    if (!hasContent) {
      setSaveState("idle");
      return;
    }

    const revision = revisionRef.current + 1;
    revisionRef.current = revision;

    const payload = {
      postId: postId ?? undefined,
      clientRevision: revision,
      title,
      excerpt: excerpt || undefined,
      markdownContent,
      scheduledPublishAt: scheduledPublishAt || undefined,
      tags: normalizedTags,
    };

    persistSnapshot(revision);
    setSaveState("saving");

    try {
      const response = await autosavePost(payload);

      if (response.postId && response.postId !== postId) {
        onPostIdChange(response.postId);
      }

      setSaveState("saved");
      setLastSavedAt(response.savedAt);
      retryCountRef.current = 0;
      persistSnapshot(response.autosaveRevision);
    } catch {
      const offline = typeof navigator !== "undefined" && !navigator.onLine;
      if (offline) {
        setSaveState("offline");
        return;
      }

      setSaveState("retrying");
      retryCountRef.current += 1;

      const delay = Math.min(
        MAX_RETRY_DELAY_MS,
        1000 * 2 ** retryCountRef.current,
      );

      if (retryTimerRef.current) {
        clearTimeout(retryTimerRef.current);
      }

      retryTimerRef.current = setTimeout(() => {
        void runAutosaveRef.current?.();
      }, delay);
    }
  }, [
    enabled,
    title,
    excerpt,
    markdownContent,
    scheduledPublishAt,
    normalizedTags,
    postId,
    onPostIdChange,
    persistSnapshot,
  ]);

  useEffect(() => {
    runAutosaveRef.current = runAutosave;
  }, [runAutosave]);

  useEffect(() => {
    if (!enabled) {
      return;
    }

    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }

    timerRef.current = setTimeout(() => {
      void runAutosave();
    }, DEBOUNCE_MS);

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [
    enabled,
    title,
    excerpt,
    markdownContent,
    scheduledPublishAt,
    tagsInput,
    runAutosave,
  ]);

  useEffect(() => {
    const handleOnline = () => {
      if (saveState === "offline" || saveState === "retrying") {
        void runAutosave();
      }
    };

    window.addEventListener("online", handleOnline);
    return () => window.removeEventListener("online", handleOnline);
  }, [saveState, runAutosave]);

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      if (retryTimerRef.current) {
        clearTimeout(retryTimerRef.current);
      }
    };
  }, []);

  return {
    saveState,
    lastSavedAt,
    setBaseRevision: (revision: number) => {
      revisionRef.current = revision;
    },
  };
}
