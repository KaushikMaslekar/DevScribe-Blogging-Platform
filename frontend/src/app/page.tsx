"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, PenSquare, Search } from "lucide-react";
import { motion } from "framer-motion";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { listPosts } from "@/lib/post-api";
import { listTags } from "@/lib/tag-api";

const SEARCH_DEBOUNCE_MS = 350;

export default function Home() {
  const [activeTag, setActiveTag] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");

  const tagsQuery = useQuery({
    queryKey: ["tags"],
    queryFn: listTags,
  });

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedSearch(searchInput.trim());
    }, SEARCH_DEBOUNCE_MS);

    return () => window.clearTimeout(timer);
  }, [searchInput]);

  const publishedPostsQuery = useQuery({
    queryKey: ["posts", "published", activeTag, debouncedSearch],
    queryFn: () =>
      listPosts({
        page: 0,
        size: 6,
        tag: activeTag ?? undefined,
        query: debouncedSearch || undefined,
      }),
  });

  return (
    <main className="relative flex flex-1 overflow-hidden">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(29,78,216,0.15),transparent_40%),radial-gradient(circle_at_80%_30%,rgba(251,146,60,0.2),transparent_35%)]" />
      <section className="mx-auto flex w-full max-w-6xl flex-1 flex-col px-6 py-20 md:px-10">
        <motion.p
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="mb-4 inline-flex items-center gap-2 rounded-full border border-border/70 bg-card/60 px-4 py-2 text-xs tracking-[0.2em] text-muted-foreground"
        >
          <PenSquare className="h-3.5 w-3.5" />
          DEVSCRIBE PLATFORM
        </motion.p>
        <motion.h1
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.1 }}
          className="max-w-4xl text-4xl font-semibold leading-tight tracking-tight md:text-6xl"
        >
          Write, collaborate, and ship technical stories with confidence.
        </motion.h1>
        <motion.p
          initial={{ opacity: 0, y: 14 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.2 }}
          className="mt-6 max-w-2xl text-base leading-8 text-muted-foreground md:text-lg"
        >
          DevScribe is a full-stack, production-ready blogging system with
          secure authentication, markdown-first writing, autosave reliability,
          and collaborative editing.
        </motion.p>
        <div className="mt-10 max-w-2xl rounded-2xl border bg-card/80 p-4 shadow-sm backdrop-blur">
          <label className="mb-2 flex items-center gap-2 text-sm font-medium">
            <Search className="h-4 w-4 text-muted-foreground" />
            Search published posts
          </label>
          <input
            value={searchInput}
            onChange={(event) => setSearchInput(event.target.value)}
            placeholder="Search title, excerpt, or content..."
            className="w-full rounded-xl border bg-background px-4 py-3 text-sm outline-none ring-ring/40 focus:ring-2"
          />
          <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
            <span>
              {debouncedSearch
                ? `Searching for \"${debouncedSearch}\"`
                : "Showing the latest published posts"}
            </span>
            {debouncedSearch ? (
              <button
                type="button"
                className="underline-offset-4 hover:underline"
                onClick={() => setSearchInput("")}
              >
                Clear search
              </button>
            ) : null}
          </div>
        </div>
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, delay: 0.3 }}
          className="mt-10 flex flex-wrap items-center gap-4"
        >
          <Button asChild size="lg">
            <Link href="/dashboard" className="inline-flex items-center gap-2">
              Open Dashboard
              <ArrowRight className="h-4 w-4" />
            </Link>
          </Button>
          <Button asChild variant="outline" size="lg">
            <Link href="/login">Sign in</Link>
          </Button>
        </motion.div>

        <div className="mt-14 w-full">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-xl font-semibold">Latest Published Posts</h2>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => setActiveTag(null)}
                className={`rounded-full border px-3 py-1 text-xs ${
                  activeTag === null
                    ? "border-primary text-primary"
                    : "text-muted-foreground"
                }`}
              >
                All
              </button>
              {tagsQuery.data?.map((tag) => (
                <button
                  key={tag}
                  type="button"
                  onClick={() => setActiveTag(tag)}
                  className={`rounded-full border px-3 py-1 text-xs ${
                    activeTag === tag
                      ? "border-primary text-primary"
                      : "text-muted-foreground"
                  }`}
                >
                  #{tag}
                </button>
              ))}
            </div>
          </div>
          {publishedPostsQuery.isLoading ? (
            <p className="mt-3 text-sm text-muted-foreground">
              Loading posts...
            </p>
          ) : null}
          {publishedPostsQuery.isError ? (
            <p className="mt-3 text-sm text-red-500">
              Unable to load posts right now.
            </p>
          ) : null}
          {publishedPostsQuery.data ? (
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              {publishedPostsQuery.data.content.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {debouncedSearch || activeTag
                    ? "No posts matched your search."
                    : "No published posts yet."}
                </p>
              ) : (
                publishedPostsQuery.data.content.map((post) => (
                  <Link
                    href={`/posts/${post.slug}`}
                    key={post.id}
                    className="rounded-xl border bg-card p-5 transition hover:border-primary/50"
                  >
                    <p className="text-xs tracking-wide text-muted-foreground">
                      @{post.authorUsername}
                    </p>
                    <h3 className="mt-2 text-lg font-semibold">{post.title}</h3>
                    <p className="mt-2 text-sm text-muted-foreground line-clamp-3">
                      {post.excerpt ?? "No excerpt available."}
                    </p>
                    {post.tags.length > 0 ? (
                      <div className="mt-3 flex flex-wrap gap-2">
                        {post.tags.map((tag) => (
                          <span
                            key={`${post.id}-${tag}`}
                            className="rounded-full border px-2 py-0.5 text-xs text-muted-foreground"
                          >
                            #{tag}
                          </span>
                        ))}
                      </div>
                    ) : null}
                  </Link>
                ))
              )}
            </div>
          ) : null}
        </div>
      </section>
    </main>
  );
}
