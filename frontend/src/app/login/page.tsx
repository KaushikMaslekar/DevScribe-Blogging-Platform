"use client";

import { FormEvent, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AxiosError } from "axios";
import { Button } from "@/components/ui/button";
import { login } from "@/lib/auth-api";
import { setAccessToken } from "@/lib/auth-storage";

export default function LoginPage() {
  const router = useRouter();
  const nextPath = useMemo(() => {
    if (typeof window === "undefined") {
      return "/dashboard";
    }

    const params = new URLSearchParams(window.location.search);
    return params.get("next") ?? "/dashboard";
  }, []);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const data = await login({ email, password });
      setAccessToken(data.accessToken, data.expiresInMs);
      router.replace(nextPath);
    } catch (err) {
      let errorMessage = "Unable to sign in. Please check your credentials.";

      // Extract error from AxiosError
      if (err instanceof AxiosError) {
        // Try to get error message from response data
        if (typeof err.response?.data === "object" && err.response.data !== null) {
          const data = err.response.data as Record<string, unknown>;
          if ("message" in data && typeof data.message === "string") {
            errorMessage = data.message;
          } else if ("detail" in data && typeof data.detail === "string") {
            errorMessage = data.detail;
          }
        }
        // Fallback to error message
        if (err.message) {
          errorMessage = err.message;
        }
      } else if (err instanceof Error) {
        errorMessage = err.message;
      }

      setError(errorMessage);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <main className="mx-auto flex w-full max-w-lg flex-1 flex-col justify-center px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">
        Sign in to DevScribe
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Welcome back. Continue writing where you left off.
      </p>
      <form
        onSubmit={handleSubmit}
        className="mt-8 rounded-xl border bg-card p-6 text-card-foreground"
      >
        <label className="mb-2 block text-sm">Email</label>
        <input
          type="email"
          className="w-full rounded-md border bg-background px-3 py-2 outline-none ring-ring/40 focus:ring-2"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />

        <label className="mb-2 mt-4 block text-sm">Password</label>
        <input
          type="password"
          className="w-full rounded-md border bg-background px-3 py-2 outline-none ring-ring/40 focus:ring-2"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
          minLength={8}
        />

        {error ? (
          <div className="mt-3 rounded-md border border-white/20 bg-black px-3 py-2 text-sm text-white">
            {error}
          </div>
        ) : null}

        <Button type="submit" className="mt-5 w-full" disabled={isSubmitting}>
          {isSubmitting ? "Signing in..." : "Continue"}
        </Button>
      </form>
      <p className="mt-4 text-sm text-muted-foreground">
        Need an account?{" "}
        <Link href="/register" className="underline">
          Register
        </Link>
      </p>
    </main>
  );
}
