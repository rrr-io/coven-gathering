// Public and private calls use the same origin, including with Vercel rewrites.
// Participant identity is an HttpOnly cookie; admin Basic credentials stay in memory.
let authorization = "";
export function setAdmin(user: string, password: string) {
  authorization =
    "Basic " + btoa(unescape(encodeURIComponent(user + ":" + password)));
}
export function clearAdmin() {
  authorization = "";
}
export async function apiFetch(path: string, options: RequestInit = {}) {
  const headers = new Headers(options.headers);
  if (authorization) headers.set("Authorization", authorization);
  const response = await fetch(path, {
    ...options,
    headers,
    credentials: "same-origin",
    cache: "no-store",
  });
  const contentType = response.headers.get("Content-Type") || "";
  if (!contentType.includes("application/json"))
    throw Error(
      response.ok
        ? "Unexpected API response. Check the /api reverse proxy."
        : "Request failed (" +
            response.status +
            "). Check the connection and upload size.",
    );
  return response;
}
export async function openPrivateProof(id: string) {
  const response = await fetch("/api/proof/" + id, {
    headers: authorization ? { Authorization: authorization } : {},
    credentials: "same-origin",
  });
  if (!response.ok) throw Error("Unable to open private proof.");
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement("a");
  a.href = url;
  a.target = "_blank";
  a.rel = "noopener";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}
