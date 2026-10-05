import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { sessionStore } from "../../core/session";
import { authRepository } from "./authRepository";

const END_MESSAGES = {
  expired: "Your session has expired. Please sign in again.",
  idle: "You were signed out after a period of inactivity.",
  logout: null,
} as const;

export function useLoginViewModel() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const login = useMutation({
    mutationFn: () => authRepository.login(username, password),
    onSettled: () => setPassword(""), // don't keep the password in memory longer than needed
  });

  const reason = sessionStore.lastEndReason();

  return {
    username,
    password,
    setUsername,
    setPassword,
    submitting: login.isPending,
    // Generic message only (OWASP A07): the backend never says which field was wrong.
    error: login.error?.message ?? null,
    notice: reason ? END_MESSAGES[reason] : null,
    canSubmit: username.trim() !== "" && password !== "" && !login.isPending,
    submit(e: FormEvent) {
      e.preventDefault();
      if (username.trim() !== "" && password !== "" && !login.isPending) login.mutate();
    },
  };
}
