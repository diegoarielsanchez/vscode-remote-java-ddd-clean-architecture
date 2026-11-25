"use client";
const MESSAGES: Record<string, string> = {
  GATEWAY_429: "Too many requests. Please wait a minute and try again.",
  GATEWAY_502: "A service sent an unexpected answer. Please try again later.",
  GATEWAY_503: "A service is temporarily unavailable. Please try again later.",
};

export function ErrorView({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return (
    <div className="card grid gap-4">
      <h1 className="text-xl font-semibold">Something went wrong</h1>
      <p role="alert">{MESSAGES[error.digest ?? ""] ?? "The page couldn't be loaded. Please try again."}</p>
      <div><button className="btn-primary" onClick={retry}>Try again</button></div>
    </div>
  );
}
