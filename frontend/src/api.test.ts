import { it, expect, vi, afterEach } from "vitest";
import { apiFetch, setAdmin, clearAdmin } from "./api";
afterEach(() => {
  clearAdmin();
  vi.unstubAllGlobals();
});
it("keeps admin authorization in memory and sends the private session cookie", async () => {
  const fake = vi.fn(
    async (_path: RequestInfo | URL, _options?: RequestInit) =>
      new Response("{}", { headers: { "Content-Type": "application/json" } }),
  );
  vi.stubGlobal("fetch", fake);
  setAdmin("admin", "not-a-real-password");
  await apiFetch("/api/coven");
  let options = fake.mock.calls[0][1] as RequestInit;
  expect(options.credentials).toBe("same-origin");
  expect(new Headers(options.headers).get("Authorization")).toMatch(/^Basic /);
  clearAdmin();
  await apiFetch("/api/coven");
  options = fake.mock.calls[1][1] as RequestInit;
  expect(new Headers(options.headers).has("Authorization")).toBe(false);
});
it("reports a missing API proxy instead of rendering a false zero state", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response("<html/>", { headers: { "Content-Type": "text/html" } }),
    ),
  );
  await expect(apiFetch("/api/coven")).rejects.toThrow("reverse proxy");
});
