// Mirrors the settlement service's rules, so people hear about a problem before the upload. The backend re-checks.
export const ALLOWED_EXTENSIONS = [".pdf", ".xlsx", ".docx", ".txt"];
const MAX_BYTES = 10 * 1024 * 1024;

export function uploadProblem(file: { name: string; size: number }): string | null {
  const name = file.name.toLowerCase();
  const ext = name.includes(".") ? name.slice(name.lastIndexOf(".")) : "";
  if (file.size === 0) return "Choose a file that isn't empty";
  if (!ALLOWED_EXTENSIONS.includes(ext)) return `Allowed: ${ALLOWED_EXTENSIONS.join(" ")}`;
  return file.size > MAX_BYTES ? "The file exceeds 10 MB" : null;
}
