/** Mirrors the backend controllers' rules so users get feedback before uploading. */
export const ALLOWED_EXTENSIONS = [".pdf", ".xlsx", ".docx", ".txt"];
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
export const ACCEPT_ATTRIBUTE = ALLOWED_EXTENSIONS.join(",");

/** Returns a user-facing problem, or null when the file is acceptable. The server re-checks. */
export function validateUpload(file: File): string | null {
  const name = file.name.toLowerCase();
  const ext = name.includes(".") ? name.slice(name.lastIndexOf(".")) : "";
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return `${file.name}: unsupported file type. Allowed: ${ALLOWED_EXTENSIONS.join(" ")}`;
  }
  if (file.size === 0) return `${file.name} is empty.`;
  if (file.size > MAX_UPLOAD_BYTES) return `${file.name} exceeds the 10 MB limit.`;
  return null;
}
