import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function LoginPage() {
  return (
    <main className="mx-auto flex w-full max-w-lg flex-1 flex-col justify-center px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">
        Sign in to DevScribe
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Module 1 scaffold: authentication UI will be wired to Spring Boot in
        Module 2.
      </p>
      <div className="mt-8 rounded-xl border bg-card p-6 text-card-foreground">
        <p className="text-sm text-muted-foreground">
          Form components are added in the next module.
        </p>
        <Button className="mt-4" disabled>
          Continue
        </Button>
      </div>
      <p className="mt-4 text-sm text-muted-foreground">
        Need an account?{" "}
        <Link href="/register" className="underline">
          Register
        </Link>
      </p>
    </main>
  );
}
