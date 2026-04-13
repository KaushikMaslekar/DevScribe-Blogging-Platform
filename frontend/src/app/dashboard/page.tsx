"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme-toggle";
import { me } from "@/lib/auth-api";
import {
  addCollaborator,
  getCollaborationSession,
  listCollaborators,
  removeCollaborator,
} from "@/lib/collaboration-api";
import {
  clearDraftSnapshot,
  readDraftSnapshot,
  useAutosaveDraft,
} from "@/lib/autosave";
import { clearAccessToken } from "@/lib/auth-storage";
import {
  createPost,
  deletePost,
  listPosts,
  listAutosaveSnapshots,
  publishPost,
  restoreAutosaveSnapshot,
  updatePost,
} from "@/lib/post-api";
import { updateMyProfile } from "@/lib/user-api";
import type { AutosaveSnapshot, PostStatus } from "@/types/post";

const CURRENT_DRAFT_COMPARE_ID = "CURRENT_DRAFT";

function normalizeCompareValue(value: string | null | undefined): string {
  return (value ?? "").trim();
}

function normalizeTagsForCompare(tags: string[]): string {
  return [...tags].sort((a, b) => a.localeCompare(b)).join(", ");
}

function toDateTimeLocalValue(value: string | null | undefined): string {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const offsetDate = new Date(
    date.getTime() - date.getTimezoneOffset() * 60000,
  );
  return offsetDate.toISOString().slice(0, 16);
}

function toISOStringOrUndefined(value: string): string | undefined {
  if (!value) {
    return undefined;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }

  return date.toISOString();
}

const RichMarkdownEditor = dynamic(
  () =>
    import("@/components/editor/rich-markdown-editor").then(
      (module) => module.RichMarkdownEditor,
    ),
  {
    ssr: false,
    loading: () => (
      <div className="min-h-96 rounded-lg border bg-muted/20 p-4 text-sm text-muted-foreground">
        Loading editor...
      </div>
    ),
  },
);

export default function DashboardPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const initialSnapshot = useMemo(() => readDraftSnapshot(), []);
  const [statusFilter, setStatusFilter] = useState<PostStatus | "ALL">("ALL");
  const [title, setTitle] = useState(() => initialSnapshot?.title ?? "");
  const [excerpt, setExcerpt] = useState(() => initialSnapshot?.excerpt ?? "");
  const [markdownContent, setMarkdownContent] = useState(
    () => initialSnapshot?.markdownContent ?? "",
  );
  const [scheduledPublishAt, setScheduledPublishAt] = useState(
    () => initialSnapshot?.scheduledPublishAt ?? "",
  );
  const [tagsInput, setTagsInput] = useState(
    () => initialSnapshot?.tagsInput ?? "",
  );
  const [draftPostId, setDraftPostId] = useState<number | null>(
    () => initialSnapshot?.postId ?? null,
  );
  const [editorResetKey, setEditorResetKey] = useState(0);
  const [collaboratorIdentifier, setCollaboratorIdentifier] = useState("");
  const [baseSnapshotId, setBaseSnapshotId] = useState<number | null>(null);
  const [compareTargetId, setCompareTargetId] = useState<string>(
    CURRENT_DRAFT_COMPARE_ID,
  );

  const normalizedTags = useMemo(
    () =>
      tagsInput
        .split(",")
        .map((tag) => tag.trim())
        .filter(Boolean),
    [tagsInput],
  );

  const profileQuery = useQuery({
    queryKey: ["auth", "me"],
    queryFn: me,
  });

  const postsQuery = useQuery({
    queryKey: ["posts", "mine", statusFilter],
    queryFn: () =>
      listPosts({
        mine: true,
        status: statusFilter === "ALL" ? undefined : statusFilter,
        page: 0,
        size: 20,
      }),
  });

  const collaborationSessionQuery = useQuery({
    queryKey: ["collaboration", "session", draftPostId],
    queryFn: () => getCollaborationSession(draftPostId as number),
    enabled: Boolean(draftPostId) && profileQuery.isSuccess,
    retry: false,
  });

  const collaboratorsQuery = useQuery({
    queryKey: ["collaboration", "collaborators", draftPostId],
    queryFn: () => listCollaborators(draftPostId as number),
    enabled: Boolean(draftPostId) && collaborationSessionQuery.isSuccess,
    retry: false,
  });

  const autosaveTimelineQuery = useQuery({
    queryKey: ["posts", "autosave", "timeline", draftPostId],
    queryFn: () => listAutosaveSnapshots(draftPostId as number),
    enabled: Boolean(draftPostId),
    retry: false,
  });

  const currentDraftCompareSnapshot = useMemo<AutosaveSnapshot>(
    () => ({
      id: -1,
      revision: -1,
      title,
      excerpt,
      markdownContent,
      scheduledPublishAt,
      tags: normalizedTags,
      savedAt: new Date().toISOString(),
    }),
    [title, excerpt, markdownContent, scheduledPublishAt, normalizedTags],
  );

  const effectiveBaseSnapshotId = useMemo(() => {
    const snapshots = autosaveTimelineQuery.data;
    if (!snapshots || snapshots.length === 0) {
      return null;
    }

    if (
      baseSnapshotId != null &&
      snapshots.some((snapshot) => snapshot.id === baseSnapshotId)
    ) {
      return baseSnapshotId;
    }

    return snapshots[0].id;
  }, [autosaveTimelineQuery.data, baseSnapshotId]);

  const effectiveCompareTargetId = useMemo(() => {
    const snapshots = autosaveTimelineQuery.data;
    if (!snapshots || snapshots.length === 0) {
      return CURRENT_DRAFT_COMPARE_ID;
    }

    if (compareTargetId === CURRENT_DRAFT_COMPARE_ID) {
      return CURRENT_DRAFT_COMPARE_ID;
    }

    return snapshots.some((snapshot) => String(snapshot.id) === compareTargetId)
      ? compareTargetId
      : CURRENT_DRAFT_COMPARE_ID;
  }, [autosaveTimelineQuery.data, compareTargetId]);

  const selectedBaseSnapshot = useMemo(() => {
    if (!autosaveTimelineQuery.data || effectiveBaseSnapshotId == null) {
      return null;
    }
    return (
      autosaveTimelineQuery.data.find(
        (snapshot) => snapshot.id === effectiveBaseSnapshotId,
      ) ?? null
    );
  }, [autosaveTimelineQuery.data, effectiveBaseSnapshotId]);

  const selectedCompareTarget = useMemo(() => {
    if (effectiveCompareTargetId === CURRENT_DRAFT_COMPARE_ID) {
      return currentDraftCompareSnapshot;
    }

    return (
      autosaveTimelineQuery.data?.find(
        (snapshot) => String(snapshot.id) === effectiveCompareTargetId,
      ) ?? null
    );
  }, [
    autosaveTimelineQuery.data,
    effectiveCompareTargetId,
    currentDraftCompareSnapshot,
  ]);

  const compareMeta = useMemo(() => {
    if (!selectedBaseSnapshot || !selectedCompareTarget) {
      return null;
    }

    const tagsBase = normalizeTagsForCompare(selectedBaseSnapshot.tags);
    const tagsTarget = normalizeTagsForCompare(selectedCompareTarget.tags);

    return {
      titleChanged:
        normalizeCompareValue(selectedBaseSnapshot.title) !==
        normalizeCompareValue(selectedCompareTarget.title),
      excerptChanged:
        normalizeCompareValue(selectedBaseSnapshot.excerpt) !==
        normalizeCompareValue(selectedCompareTarget.excerpt),
      markdownChanged:
        normalizeCompareValue(selectedBaseSnapshot.markdownContent) !==
        normalizeCompareValue(selectedCompareTarget.markdownContent),
      scheduleChanged:
        normalizeCompareValue(selectedBaseSnapshot.scheduledPublishAt) !==
        normalizeCompareValue(selectedCompareTarget.scheduledPublishAt),
      tagsChanged: tagsBase !== tagsTarget,
      baseTags: tagsBase,
      targetTags: tagsTarget,
      baseMarkdownLength: selectedBaseSnapshot.markdownContent.length,
      targetMarkdownLength: selectedCompareTarget.markdownContent.length,
    };
  }, [selectedBaseSnapshot, selectedCompareTarget]);

  const createPostMutation = useMutation({
    mutationFn: createPost,
    onSuccess: (post) => {
      setDraftPostId(post.id);
      queryClient.invalidateQueries({ queryKey: ["posts", "mine"] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
    },
  });

  const updatePostMutation = useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: number;
      payload: Parameters<typeof updatePost>[1];
    }) => updatePost(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["posts", "mine"] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
    },
  });

  const publishPostMutation = useMutation({
    mutationFn: publishPost,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["posts", "mine"] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
    },
  });

  const deletePostMutation = useMutation({
    mutationFn: deletePost,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["posts", "mine"] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
    },
  });

  const addCollaboratorMutation = useMutation({
    mutationFn: (identifier: string) =>
      addCollaborator(draftPostId as number, { identifier }),
    onSuccess: () => {
      setCollaboratorIdentifier("");
      queryClient.invalidateQueries({
        queryKey: ["collaboration", "collaborators", draftPostId],
      });
    },
  });

  const removeCollaboratorMutation = useMutation({
    mutationFn: (userId: number) =>
      removeCollaborator(draftPostId as number, userId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["collaboration", "collaborators", draftPostId],
      });
    },
  });

  const updateProfileMutation = useMutation({
    mutationFn: updateMyProfile,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
    },
  });

  const autosave = useAutosaveDraft({
    enabled: profileQuery.isSuccess,
    postId: draftPostId,
    onPostIdChange: setDraftPostId,
    initialRevision: initialSnapshot?.revision ?? 0,
    title,
    excerpt,
    markdownContent,
    scheduledPublishAt,
    tagsInput,
  });

  const restoreSnapshotMutation = useMutation({
    mutationFn: (snapshotId: number) =>
      restoreAutosaveSnapshot(draftPostId as number, snapshotId),
    onSuccess: (restored) => {
      setDraftPostId(restored.postId);
      setTitle(restored.title);
      setExcerpt(restored.excerpt ?? "");
      setMarkdownContent(restored.markdownContent);
      setScheduledPublishAt(toDateTimeLocalValue(restored.scheduledPublishAt));
      setTagsInput(restored.tags.join(", "));
      setEditorResetKey((value) => value + 1);
      autosave.setBaseRevision(restored.autosaveRevision);

      queryClient.invalidateQueries({
        queryKey: ["posts", "autosave", "timeline", restored.postId],
      });
      queryClient.invalidateQueries({ queryKey: ["posts", "mine"] });
    },
  });

  useEffect(() => {
    if (!draftPostId || !autosave.lastSavedAt) {
      return;
    }

    queryClient.invalidateQueries({
      queryKey: ["posts", "autosave", "timeline", draftPostId],
    });
  }, [autosave.lastSavedAt, draftPostId, queryClient]);

  function handleCreatePost(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const payload = {
      title,
      excerpt: excerpt || undefined,
      markdownContent,
      scheduledPublishAt: toISOStringOrUndefined(scheduledPublishAt),
      tags: normalizedTags,
    };

    if (draftPostId) {
      updatePostMutation.mutate({ id: draftPostId, payload });
      return;
    }

    createPostMutation.mutate(payload);
  }

  function handleStartNewDraft() {
    setDraftPostId(null);
    setTitle("");
    setExcerpt("");
    setMarkdownContent("");
    setScheduledPublishAt("");
    setTagsInput("");
    setCollaboratorIdentifier("");
    setBaseSnapshotId(null);
    setCompareTargetId(CURRENT_DRAFT_COMPARE_ID);
    setEditorResetKey((value) => value + 1);
    clearDraftSnapshot();
  }

  function handleLogout() {
    clearAccessToken();
    router.replace("/login");
  }

  function handleProfileUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    updateProfileMutation.mutate({
      displayName: String(formData.get("displayName") ?? ""),
      bio: String(formData.get("bio") ?? ""),
      avatarUrl: String(formData.get("avatarUrl") ?? ""),
    });
  }

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-6 py-14 md:px-10 text-white bg-black animate-fade-up">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
          <p className="mt-2 text-white/70">
            Welcome to your writing control center.
          </p>
        </div>
        <ThemeToggle />
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-white/20 bg-black p-6 text-white animate-pulse-border">
          {profileQuery.isLoading ? <p>Loading profile...</p> : null}
          {profileQuery.isError ? (
            <div className="rounded-md border border-white/20 bg-black px-3 py-2 text-sm text-white">
              Session expired. Please sign in again.
            </div>
          ) : null}
          {profileQuery.data ? (
            <div className="space-y-1 text-sm text-white/85">
              <p>
                <span className="font-medium text-white">User:</span>{" "}
                {profileQuery.data.username}
              </p>
              <p>
                <span className="font-medium text-white">Email:</span>{" "}
                {profileQuery.data.email}
              </p>
              <p>
                <span className="font-medium text-white">Role:</span>{" "}
                {profileQuery.data.role}
              </p>
            </div>
          ) : null}

          <Button
            variant="outline"
            className="mt-5 border-white/30 text-white hover:bg-white hover:text-black"
            onClick={handleLogout}
          >
            Logout
          </Button>
        </div>

        <form
          onSubmit={handleProfileUpdate}
          className="rounded-xl border border-white/20 bg-black p-6 text-white"
        >
          <h2 className="text-xl font-semibold">Profile Settings</h2>
          <p className="mt-2 text-sm text-white/70">
            Live profile updates are reflected across author cards.
          </p>

          <label className="mt-4 mb-2 block text-sm">Display Name</label>
          <input
            name="displayName"
            className="w-full rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
            defaultValue={profileQuery.data?.displayName ?? ""}
          />

          <label className="mt-4 mb-2 block text-sm">Bio</label>
          <textarea
            name="bio"
            className="w-full rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
            rows={3}
            defaultValue={profileQuery.data?.bio ?? ""}
          />

          <label className="mt-4 mb-2 block text-sm">Avatar URL</label>
          <input
            name="avatarUrl"
            className="w-full rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
            defaultValue={profileQuery.data?.avatarUrl ?? ""}
            placeholder="https://example.com/avatar.png"
          />

          <Button
            className="mt-4 bg-white text-black hover:bg-white/90"
            type="submit"
            disabled={updateProfileMutation.isPending}
          >
            {updateProfileMutation.isPending ? "Updating..." : "Update Profile"}
          </Button>
        </form>
      </div>

      <p className="mt-4 text-xs text-white/60">
        Live autosave and collaboration status update in real time while you
        type.
      </p>

      <p className="sr-only">Welcome to your writing control center.</p>

      <form
        onSubmit={handleCreatePost}
        className="mt-8 rounded-xl border border-white/20 bg-black p-6 text-white"
      >
        <h2 className="text-xl font-semibold">Create New Post</h2>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-white/70">
          <span>
            Autosave status: {autosave.saveState}
            {autosave.lastSavedAt
              ? ` · ${new Date(autosave.lastSavedAt).toLocaleTimeString()}`
              : ""}
          </span>
          <span>{draftPostId ? `Draft ID: ${draftPostId}` : "New draft"}</span>
        </div>

        {draftPostId ? (
          <div className="mt-4 rounded-lg border border-white/20 bg-black p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-medium">Autosave Recovery Timeline</p>
              <p className="text-xs text-white/60">
                Restore previous autosave checkpoints
              </p>
            </div>

            {autosaveTimelineQuery.isLoading ? (
              <p className="mt-3 text-xs text-white/70">Loading timeline...</p>
            ) : null}

            {autosaveTimelineQuery.isError ? (
              <p className="mt-3 text-xs text-white/70">
                Unable to load autosave timeline right now.
              </p>
            ) : null}

            {autosaveTimelineQuery.data ? (
              <div className="mt-3 max-h-52 space-y-2 overflow-y-auto pr-1">
                {autosaveTimelineQuery.data.length === 0 ? (
                  <p className="text-xs text-white/60">
                    No checkpoints yet. Keep writing and autosave will create
                    them.
                  </p>
                ) : (
                  autosaveTimelineQuery.data.map((snapshot) => (
                    <div
                      key={snapshot.id}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-white/15 px-3 py-2"
                    >
                      <div>
                        <p className="text-xs text-white/90">
                          r{snapshot.revision} ·{" "}
                          {snapshot.title || "Untitled draft"}
                        </p>
                        <p className="text-[11px] text-white/60">
                          {new Date(snapshot.savedAt).toLocaleString()}
                        </p>
                      </div>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() =>
                          restoreSnapshotMutation.mutate(snapshot.id)
                        }
                        disabled={restoreSnapshotMutation.isPending}
                      >
                        Restore
                      </Button>
                    </div>
                  ))
                )}
              </div>
            ) : null}

            {autosaveTimelineQuery.data &&
            autosaveTimelineQuery.data.length > 0 ? (
              <div className="mt-4 rounded-md border border-white/15 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs font-medium text-white/90">
                    Revision Compare
                  </p>
                  <p className="text-[11px] text-white/60">
                    Compare any checkpoint against current draft or another
                    checkpoint.
                  </p>
                </div>

                <div className="mt-3 grid gap-2 md:grid-cols-2">
                  <label className="text-[11px] text-white/70">
                    Base revision
                    <select
                      className="mt-1 w-full rounded-md border border-white/20 bg-black px-2 py-1.5 text-xs text-white"
                      value={effectiveBaseSnapshotId ?? ""}
                      onChange={(event) =>
                        setBaseSnapshotId(Number(event.target.value))
                      }
                    >
                      {autosaveTimelineQuery.data.map((snapshot) => (
                        <option key={`base-${snapshot.id}`} value={snapshot.id}>
                          r{snapshot.revision} · {snapshot.title || "Untitled"}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="text-[11px] text-white/70">
                    Compare against
                    <select
                      className="mt-1 w-full rounded-md border border-white/20 bg-black px-2 py-1.5 text-xs text-white"
                      value={effectiveCompareTargetId}
                      onChange={(event) =>
                        setCompareTargetId(event.target.value)
                      }
                    >
                      <option value={CURRENT_DRAFT_COMPARE_ID}>
                        Current draft (live)
                      </option>
                      {autosaveTimelineQuery.data.map((snapshot) => (
                        <option
                          key={`target-${snapshot.id}`}
                          value={String(snapshot.id)}
                        >
                          r{snapshot.revision} · {snapshot.title || "Untitled"}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>

                {selectedBaseSnapshot &&
                selectedCompareTarget &&
                compareMeta ? (
                  <div className="mt-3 space-y-2 text-xs">
                    <div className="grid gap-2 md:grid-cols-2">
                      <div className="rounded-md border border-white/15 p-2">
                        <p className="text-[11px] uppercase text-white/60">
                          Base
                        </p>
                        <p className="text-white/90">
                          {selectedBaseSnapshot.revision >= 0
                            ? `r${selectedBaseSnapshot.revision}`
                            : "Current"}
                        </p>
                      </div>
                      <div className="rounded-md border border-white/15 p-2">
                        <p className="text-[11px] uppercase text-white/60">
                          Target
                        </p>
                        <p className="text-white/90">
                          {selectedCompareTarget.revision >= 0
                            ? `r${selectedCompareTarget.revision}`
                            : "Current"}
                        </p>
                      </div>
                    </div>

                    <div className="grid gap-2 md:grid-cols-2">
                      <p className="rounded-md border border-white/15 px-2 py-1.5 text-white/85">
                        Title:{" "}
                        {compareMeta.titleChanged ? "Changed" : "No change"}
                      </p>
                      <p className="rounded-md border border-white/15 px-2 py-1.5 text-white/85">
                        Excerpt:{" "}
                        {compareMeta.excerptChanged ? "Changed" : "No change"}
                      </p>
                      <p className="rounded-md border border-white/15 px-2 py-1.5 text-white/85">
                        Tags:{" "}
                        {compareMeta.tagsChanged ? "Changed" : "No change"}
                      </p>
                      <p className="rounded-md border border-white/15 px-2 py-1.5 text-white/85">
                        Markdown:{" "}
                        {compareMeta.markdownChanged ? "Changed" : "No change"}{" "}
                        ({compareMeta.baseMarkdownLength} -&gt;{" "}
                        {compareMeta.targetMarkdownLength} chars)
                      </p>
                    </div>

                    <div className="grid gap-2 md:grid-cols-2">
                      <div className="rounded-md border border-white/15 p-2">
                        <p className="text-[11px] uppercase text-white/60">
                          Tags (Base)
                        </p>
                        <p className="mt-1 text-white/85">
                          {compareMeta.baseTags || "No tags"}
                        </p>
                      </div>
                      <div className="rounded-md border border-white/15 p-2">
                        <p className="text-[11px] uppercase text-white/60">
                          Tags (Target)
                        </p>
                        <p className="mt-1 text-white/85">
                          {compareMeta.targetTags || "No tags"}
                        </p>
                      </div>
                    </div>

                    <details className="rounded-md border border-white/15 p-2">
                      <summary className="cursor-pointer text-white/85">
                        Preview markdown changes
                      </summary>
                      <div className="mt-2 grid gap-2 md:grid-cols-2">
                        <div>
                          <p className="text-[11px] uppercase text-white/60">
                            Base
                          </p>
                          <pre className="mt-1 max-h-40 overflow-auto rounded border border-white/10 bg-black p-2 text-[11px] leading-relaxed text-white/80">
                            {selectedBaseSnapshot.markdownContent || "(empty)"}
                          </pre>
                        </div>
                        <div>
                          <p className="text-[11px] uppercase text-white/60">
                            Target
                          </p>
                          <pre className="mt-1 max-h-40 overflow-auto rounded border border-white/10 bg-black p-2 text-[11px] leading-relaxed text-white/80">
                            {selectedCompareTarget.markdownContent || "(empty)"}
                          </pre>
                        </div>
                      </div>
                    </details>
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}
        <label className="mt-4 mb-2 block text-sm">Title</label>
        <input
          className="w-full rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          required
        />
        <label className="mt-4 mb-2 block text-sm">Excerpt</label>
        <input
          className="w-full rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
          value={excerpt}
          onChange={(event) => setExcerpt(event.target.value)}
        />
        <label className="mt-4 mb-2 block text-sm">Schedule Publish</label>
        <input
          type="datetime-local"
          className="w-full rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
          value={scheduledPublishAt}
          onChange={(event) => setScheduledPublishAt(event.target.value)}
        />
        <p className="mt-2 text-xs text-white/60">
          Leave blank to keep this draft unscheduled.
        </p>
        <label className="mt-4 mb-2 block text-sm">Markdown Content</label>
        <RichMarkdownEditor
          key={editorResetKey}
          initialMarkdown={markdownContent}
          onMarkdownChange={setMarkdownContent}
          collaborationSession={collaborationSessionQuery.data ?? null}
          collaboratorName={profileQuery.data?.username}
        />
        {draftPostId ? (
          <div className="mt-6 rounded-lg border border-white/20 bg-black p-4 text-sm text-white">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="font-medium">Collaborative editing</p>
                <p className="text-xs text-white/70">
                  {collaborationSessionQuery.isLoading
                    ? "Checking collaboration access..."
                    : collaborationSessionQuery.isError
                      ? "Collaboration is unavailable for this draft right now."
                      : collaborationSessionQuery.data
                        ? `${collaborationSessionQuery.data.role} access in room ${collaborationSessionQuery.data.room}`
                        : "Save the draft to open a collaboration room."}
                </p>
              </div>
              {collaborationSessionQuery.data?.degradedModeFallback ? (
                <span className="rounded-full border border-white/40 px-2 py-1 text-xs text-white/80">
                  Fallback mode
                </span>
              ) : null}
            </div>

            {collaboratorsQuery.data ? (
              <div className="mt-4 space-y-3">
                <p className="text-xs uppercase tracking-wide text-white/60">
                  Collaborators
                </p>
                {collaboratorsQuery.data.length === 0 ? (
                  <p className="text-sm text-white/70">
                    No collaborators added yet.
                  </p>
                ) : (
                  collaboratorsQuery.data.map((collaborator) => (
                    <div
                      key={collaborator.userId}
                      className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-white/20 bg-black px-3 py-2"
                    >
                      <div>
                        <p className="font-medium">{collaborator.username}</p>
                        <p className="text-xs text-white/65">
                          {collaborator.email}
                        </p>
                      </div>
                      {collaborationSessionQuery.data?.role === "OWNER" ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() =>
                            removeCollaboratorMutation.mutate(
                              collaborator.userId,
                            )
                          }
                        >
                          Remove
                        </Button>
                      ) : null}
                    </div>
                  ))
                )}
              </div>
            ) : null}

            {collaborationSessionQuery.data?.role === "OWNER" ? (
              <form
                className="mt-4 flex flex-wrap gap-2"
                onSubmit={(event) => {
                  event.preventDefault();

                  if (!collaboratorIdentifier.trim()) {
                    return;
                  }

                  addCollaboratorMutation.mutate(collaboratorIdentifier.trim());
                }}
              >
                <input
                  className="min-w-72 flex-1 rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
                  value={collaboratorIdentifier}
                  onChange={(event) =>
                    setCollaboratorIdentifier(event.target.value)
                  }
                  placeholder="Invite by username or email"
                />
                <Button
                  type="submit"
                  disabled={addCollaboratorMutation.isPending}
                >
                  Invite
                </Button>
              </form>
            ) : null}
          </div>
        ) : null}
        <label className="mt-4 mb-2 block text-sm">
          Tags (comma separated)
        </label>
        <input
          className="w-full rounded-md border border-white/20 bg-black px-3 py-2 text-white outline-none ring-0 focus:border-white"
          value={tagsInput}
          onChange={(event) => setTagsInput(event.target.value)}
          placeholder="spring-boot, java, backend"
        />
        <Button
          className="mt-4 bg-white text-black hover:bg-white/90"
          type="submit"
          disabled={
            createPostMutation.isPending || updatePostMutation.isPending
          }
        >
          {createPostMutation.isPending || updatePostMutation.isPending
            ? "Saving..."
            : "Save Draft"}
        </Button>
        <Button
          className="mt-4 ml-3 border-white/30 text-white hover:bg-white hover:text-black"
          type="button"
          variant="outline"
          onClick={handleStartNewDraft}
        >
          Start New Draft
        </Button>
      </form>

      <section className="mt-8 rounded-xl border border-white/20 bg-black p-6 text-white">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-xl font-semibold">Your Posts</h2>
          <select
            className="rounded-md border border-white/20 bg-black px-3 py-2 text-sm text-white"
            value={statusFilter}
            onChange={(event) =>
              setStatusFilter(event.target.value as PostStatus | "ALL")
            }
          >
            <option value="ALL">All</option>
            <option value="DRAFT">Draft</option>
            <option value="PUBLISHED">Published</option>
          </select>
        </div>

        {postsQuery.isLoading ? <p className="mt-4">Loading posts...</p> : null}
        {postsQuery.isError ? (
          <div className="mt-4 rounded-md border border-white/20 bg-black px-3 py-2 text-sm text-white">
            Unable to load your posts.
          </div>
        ) : null}
        {postsQuery.data ? (
          <div className="mt-4 space-y-3">
            {postsQuery.data.content.length === 0 ? (
              <p className="text-sm text-white/70">
                No posts found for this filter.
              </p>
            ) : (
              postsQuery.data.content.map((post) => (
                <div
                  key={post.id}
                  className="rounded-lg border border-white/20 p-4 transition-transform hover:-translate-y-0.5"
                >
                  <p className="text-xs text-white/70">{post.status}</p>
                  <h3 className="mt-1 text-lg font-semibold">{post.title}</h3>
                  <p className="mt-2 text-sm text-white/70">
                    {post.excerpt ?? "No excerpt"}
                  </p>
                  {post.tags.length > 0 ? (
                    <div className="mt-3 flex flex-wrap gap-2">
                      {post.tags.map((tag) => (
                        <span
                          key={`${post.id}-${tag}`}
                          className="rounded-full border border-white/20 px-2 py-0.5 text-xs text-white/70"
                        >
                          #{tag}
                        </span>
                      ))}
                    </div>
                  ) : null}
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => router.push(`/posts/${post.slug}`)}
                    >
                      View
                    </Button>
                    {post.status === "DRAFT" ? (
                      <Button
                        size="sm"
                        onClick={() => publishPostMutation.mutate(post.id)}
                        disabled={publishPostMutation.isPending}
                      >
                        Publish
                      </Button>
                    ) : null}
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => deletePostMutation.mutate(post.id)}
                      disabled={deletePostMutation.isPending}
                    >
                      Delete
                    </Button>
                  </div>
                </div>
              ))
            )}
          </div>
        ) : null}
      </section>
    </main>
  );
}
