"use client";
import { ErrorView } from "@/ui/ErrorView";

export default function ErrorPage({ error }: { error: Error & { digest?: string } }) {
  return <ErrorView error={error} retry={() => window.location.reload()} />;
}
