import { afterEach, expect, it, vi } from "vitest";
import { assessGroupRisk } from "./groupRisk";
import { setToken } from "./client";
afterEach(() => {
  vi.unstubAllGlobals();
  setToken(null);
});
const result = {
  counterpartyId: 5,
  counterpartyName: "Falcon Freight Services",
  successful: true,
  probability: 0.2,
  riskLevel: "LOW_RISK",
  errorMessage: null,
};
it("sends one authenticated group POST and preserves a failed company without a fake zero score", async () => {
  const values = [
    result,
    {
      ...result,
      counterpartyId: 6,
      successful: false,
      probability: null,
      riskLevel: null,
      errorMessage: "Risk assessment failed",
    },
  ];
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(values)));
  vi.stubGlobal("fetch", fetch);
  setToken("test-session");
  expect(await assessGroupRisk(3)).toEqual(values);
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0][0]).toBe(
    "/api/corporate-groups/3/risk-assessment",
  );
  expect(fetch.mock.calls[0][1]).toMatchObject({
    method: "POST",
    headers: { Authorization: "Bearer test-session" },
  });
});
it.each([
  { name: "invalid score", value: [{ ...result, probability: 2 }] },
  { name: "duplicate companies", value: [result, result] },
  { name: "missing fields", value: [{}] },
])("rejects $name", async ({ value }) => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(new Response(JSON.stringify(value))),
  );
  await expect(assessGroupRisk(3)).rejects.toThrow("unsupported result");
});
