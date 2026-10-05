import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "../core/http";
import { sessionStore } from "../core/session";

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Retry network/5xx failures only; 4xx (incl. 429 rate limiting) won't improve by retrying.
        retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
        refetchOnWindowFocus: false,
        staleTime: 30_000,
      },
      mutations: { retry: false },
    },
  });
}

export const queryClient = createQueryClient();

// No personal data survives the session in memory.
sessionStore.onEnd(() => queryClient.clear());
