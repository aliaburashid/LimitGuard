import { afterEach, expect, it, vi } from "vitest";
import { allPages } from "./portfolio";
afterEach(() => vi.unstubAllGlobals());
it("loads all API pages before calculating register totals", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ content: [{ id: 1 }], totalPages: 2 })),
    )
    .mockResolvedValueOnce(
      new Response(JSON.stringify({ content: [{ id: 2 }], totalPages: 2 })),
    );
  vi.stubGlobal("fetch", fetch);
  expect(await allPages("/credit-requests/search?status=USED")).toEqual([
    { id: 1 },
    { id: 2 },
  ]);
  expect(fetch.mock.calls[1][0]).toBe(
    "/api/credit-requests/search?status=USED&page=1&size=100",
  );
});
it("does not turn a malformed directory into zero portfolio totals", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("{}")));
  await expect(allPages("/group-credit-limits")).rejects.toThrow(
    "incomplete directory",
  );
});
