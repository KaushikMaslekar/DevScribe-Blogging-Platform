import Link from "next/link";
import { Button } from "@/components/ui/button";

export default function RegisterPage() {
  return (
    <main className="mx-auto flex w-full max-w-lg flex-1 flex-col justify-center px-6 py-16">
      <h1 className="text-3xl font-semibold tracking-tight">
        Create your DevScribe account
      </h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Module 1 scaffold: registration endpoint integration lands in Module 2.
      </p>
      <div className="mt-8 rounded-xl border bg-card p-6 text-card-foreground">
        <p className="text-sm text-muted-foreground">
          Registration form is part of next module delivery.
        </p>
        <Button className="mt-4" disabled>
          Create Account
        </Button>
      </div>
      <p className="mt-4 text-sm text-muted-foreground">
        Already registered?{" "}
        <Link href="/login" className="underline">
          Sign in
        </Link>
      </p>
    </main>
  );
}
