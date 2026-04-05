"use client";

import Link from "next/link";
import { ArrowRight, PenSquare } from "lucide-react";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";

export default function Home() {
  return (
    <main className="relative flex flex-1 overflow-hidden">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(29,78,216,0.15),transparent_40%),radial-gradient(circle_at_80%_30%,rgba(251,146,60,0.2),transparent_35%)]" />
      <section className="mx-auto flex w-full max-w-6xl flex-1 flex-col items-start justify-center px-6 py-20 md:px-10">
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
      </section>
    </main>
  );
}
