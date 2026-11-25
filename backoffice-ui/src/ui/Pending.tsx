"use client";
import { useLinkStatus } from "next/link";

/**
 * Navigation feedback. Instead of loading.tsx, which would send "200 OK" before a page knows
 * whether it is a redirect, 403 or 404.
 */
export function Pending() {
  return useLinkStatus().pending ? <span role="status" aria-label="Loading"> …</span> : null;
}
