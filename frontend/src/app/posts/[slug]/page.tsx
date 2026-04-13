"use client";

import Link from "next/link";
import { useMemo } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Bookmark,
  Heart,
  ShieldCheck,
  Sparkles,
  UserPlus,
  UserRoundCheck,
} from "lucide-react";
import {
  bookmarkPost,
  getPostBySlug,
  likePost,
  unlikePost,
  unbookmarkPost,
} from "@/lib/post-api";
import { followUser, unfollowUser } from "@/lib/user-api";
import { markdownToHtml } from "@/lib/markdown";
import { getAccessToken } from "@/lib/auth-storage";
import { Button } from "@/components/ui/button";

export default function PostDetailPage() {
  const params = useParams<{ slug: string }>();
  const queryClient = useQueryClient();

  const postQuery = useQuery({
    queryKey: ["post", params.slug],
    queryFn: () => getPostBySlug(params.slug),
    enabled: Boolean(params.slug),
  });

  const likeMutation = useMutation({
    mutationFn: (id: number) => likePost(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["post", params.slug] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
    },
  });

  const unlikeMutation = useMutation({
    mutationFn: (id: number) => unlikePost(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["post", params.slug] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
    },
  });

  const bookmarkMutation = useMutation({
    mutationFn: (id: number) => bookmarkPost(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["post", params.slug] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
      queryClient.invalidateQueries({ queryKey: ["posts", "bookmarks"] });
    },
  });

  const unbookmarkMutation = useMutation({
    mutationFn: (id: number) => unbookmarkPost(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["post", params.slug] });
      queryClient.invalidateQueries({ queryKey: ["posts", "published"] });
      queryClient.invalidateQueries({ queryKey: ["posts", "bookmarks"] });
    },
  });

  const followMutation = useMutation({
    mutationFn: (username: string) => followUser(username),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["post", params.slug] });
      queryClient.invalidateQueries({ queryKey: ["posts", "feed"] });
    },
  });

  const unfollowMutation = useMutation({
    mutationFn: (username: string) => unfollowUser(username),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["post", params.slug] });
      queryClient.invalidateQueries({ queryKey: ["posts", "feed"] });
    },
  });

  const canInteract = Boolean(getAccessToken());
  const renderedContent = useMemo(
    () =>
      postQuery.data ? markdownToHtml(postQuery.data.markdownContent) : "",
    [postQuery.data],
  );

  return (
    <main className="relative mx-auto flex w-full max-w-5xl flex-1 flex-col px-6 py-14 md:px-10 page-surface">
      {postQuery.isLoading ? (
        <div className="rounded-3xl border bg-card/80 p-8 shadow-sm">
          Loading post...
        </div>
      ) : null}
      {postQuery.isError ? (
        <div className="rounded-3xl border border-white/20 bg-black p-6 text-white">
          Unable to load this post.
        </div>
      ) : null}

      {postQuery.data ? (
        <article className="space-y-8">
          <section className="rounded-3xl border bg-card/85 p-6 shadow-sm backdrop-blur md:p-8">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="inline-flex items-center gap-2 rounded-full border bg-background/80 px-3 py-1 text-xs uppercase tracking-[0.18em] text-muted-foreground">
                <Sparkles className="h-3.5 w-3.5" />
                Published story
              </div>
              <Link
                href={`/u/${postQuery.data.authorUsername}`}
                className="text-sm text-muted-foreground underline-offset-4 hover:underline"
              >
                @{postQuery.data.authorUsername}
              </Link>
            </div>

            <h1 className="mt-5 max-w-4xl text-4xl font-semibold tracking-tight md:text-5xl">
              {postQuery.data.title}
            </h1>

            {postQuery.data.excerpt ? (
              <p className="mt-4 max-w-3xl text-lg leading-8 text-muted-foreground">
                {postQuery.data.excerpt}
              </p>
            ) : null}

            <div className="mt-6 flex flex-wrap items-center gap-3">
              <Button
                type="button"
                variant={postQuery.data.likedByMe ? "default" : "outline"}
                size="sm"
                disabled={
                  !canInteract ||
                  likeMutation.isPending ||
                  unlikeMutation.isPending
                }
                onClick={() => {
                  if (!canInteract) {
                    return;
                  }

                  if (postQuery.data.likedByMe) {
                    unlikeMutation.mutate(postQuery.data.id);
                  } else {
                    likeMutation.mutate(postQuery.data.id);
                  }
                }}
              >
                <Heart className="mr-2 h-4 w-4" />
                {postQuery.data.likedByMe ? "Unlike" : "Like"}
              </Button>

              <Button
                type="button"
                variant={postQuery.data.bookmarkedByMe ? "default" : "outline"}
                size="sm"
                disabled={
                  !canInteract ||
                  bookmarkMutation.isPending ||
                  unbookmarkMutation.isPending
                }
                onClick={() => {
                  if (!canInteract) {
                    return;
                  }

                  if (postQuery.data.bookmarkedByMe) {
                    unbookmarkMutation.mutate(postQuery.data.id);
                  } else {
                    bookmarkMutation.mutate(postQuery.data.id);
                  }
                }}
              >
                <Bookmark className="mr-2 h-4 w-4" />
                {postQuery.data.bookmarkedByMe ? "Bookmarked" : "Bookmark"}
              </Button>

              <Button
                type="button"
                variant={
                  postQuery.data.authorFollowedByMe ? "default" : "outline"
                }
                size="sm"
                disabled={
                  !canInteract ||
                  followMutation.isPending ||
                  unfollowMutation.isPending
                }
                onClick={() => {
                  if (!canInteract) {
                    return;
                  }

                  if (postQuery.data.authorFollowedByMe) {
                    unfollowMutation.mutate(postQuery.data.authorUsername);
                  } else {
                    followMutation.mutate(postQuery.data.authorUsername);
                  }
                }}
              >
                {postQuery.data.authorFollowedByMe ? (
                  <UserRoundCheck className="mr-2 h-4 w-4" />
                ) : (
                  <UserPlus className="mr-2 h-4 w-4" />
                )}
                {postQuery.data.authorFollowedByMe
                  ? "Following author"
                  : "Follow author"}
              </Button>

              <div className="inline-flex items-center gap-1 rounded-full border bg-background/80 px-3 py-2 text-xs text-muted-foreground">
                <Heart className="h-3 w-3" />
                {postQuery.data.likesCount}{" "}
                {postQuery.data.likesCount === 1 ? "like" : "likes"}
              </div>

              {!canInteract ? (
                <span className="text-xs text-muted-foreground">
                  Sign in to interact
                </span>
              ) : null}
            </div>

            {postQuery.data.tags.length > 0 ? (
              <div className="mt-6 flex flex-wrap gap-2">
                {postQuery.data.tags.map((tag) => (
                  <span
                    key={`${postQuery.data.id}-${tag}`}
                    className="rounded-full border bg-background/80 px-3 py-1 text-xs text-muted-foreground"
                  >
                    #{tag}
                  </span>
                ))}
              </div>
            ) : null}

            <div className="mt-6 rounded-2xl border bg-muted/40 px-4 py-3 text-xs text-muted-foreground">
              <ShieldCheck className="mr-2 inline h-4 w-4" />
              Markdown content is rendered safely with live caching for faster
              reads.
            </div>
          </section>

          <section className="rounded-3xl border bg-card/90 p-6 shadow-sm md:p-8">
            <div
              className="prose max-w-none prose-headings:tracking-tight prose-headings:text-foreground prose-p:text-foreground prose-li:text-foreground prose-strong:text-foreground prose-code:text-foreground prose-pre:rounded-2xl prose-pre:bg-black prose-pre:text-white"
              dangerouslySetInnerHTML={{ __html: renderedContent }}
            />
          </section>
        </article>
      ) : null}
    </main>
  );
}
