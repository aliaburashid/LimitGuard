import { afterEach, describe, it, expect, vi } from "vitest";
import { api, setToken, safeError } from "./client";
afterEach(() => {
  setToken(null);
  vi.unstubAllGlobals();
});
describe("production API boundary", () => {
  it("uses the exact login DTO and reads token from message", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response(JSON.stringify({ message: "test-token" })),
      );
    vi.stubGlobal("fetch", fetch);
    const response = await api<{ message: string }>(
      "/auth/users/login",
      "POST",
      { email: "user@example.com", password: "test-only" },
    );
    expect(response.message).toBe("test-token");
    expect(fetch.mock.calls[0][0]).toBe("/api/auth/users/login");
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      email: "user@example.com",
      password: "test-only",
    });
    expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });
  it("sends authorization in headers and keeps money as decimal strings", async () => {
    setToken("test-token");
    const fetch = vi.fn().mockResolvedValue(new Response("{}"));
    vi.stubGlobal("fetch", fetch);
    await api("/credit-requests", "POST", {
      creditLimitId: 12,
      amount: "100000.25",
    });
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe(
      "Bearer test-token",
    );
    expect(JSON.parse(fetch.mock.calls[0][1].body).amount).toBe("100000.25");
    expect(fetch.mock.calls[0][0]).not.toContain("test-token");
  });
  it("preserves multipart boundary handling", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response("{}"));
    vi.stubGlobal("fetch", fetch);
    const data = new FormData();
    await api("/auth/users/profile-picture", "PATCH", data);
    expect(fetch.mock.calls[0][1].body).toBe(data);
    expect(fetch.mock.calls[0][1].headers["Content-Type"]).toBeUndefined();
  });
  it("invalidates expired sessions", async () => {
    setToken("test-token");
    const dispatchEvent = vi.fn();
    vi.stubGlobal("window", { dispatchEvent });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("{}", { status: 401 })),
    );
    await expect(api("/auth/users/profile")).rejects.toThrow(
      "session has expired",
    );
    expect(dispatchEvent).toHaveBeenCalledOnce();
  });
  it("shows safe domain errors and suppresses implementation failures", () => {
    expect(safeError(400, "Insufficient available headroom")).toBe(
      "Insufficient available headroom",
    );
    expect(safeError(500, "java.sql.SQLException")).not.toContain(
      "SQLException",
    );
    expect(safeError(403)).toContain("permission");
    expect(safeError(400, "java.lang.IllegalArgumentException")).not.toContain(
      "java.lang",
    );
  });
});

describe("authenticated SSE", () => {
  it("parses a status event across chunk boundaries and stops on abort", async () => {
    const { stream } = await import("./client");
    const controller = new AbortController();
    const onEvent = vi.fn(() => controller.abort());
    const encoder = new TextEncoder();
    const body = new ReadableStream({
      start(c) {
        c.enqueue(encoder.encode("event:credit-request-status\r"));
        c.enqueue(
          encoder.encode(
            '\ndata:{"creditRequestId":42,"status":"USED"}\r\n\r\n',
          ),
        );
        c.close();
      },
    });
    const fetch = vi
      .fn()
      .mockResolvedValue(
        new Response(body, {
          headers: { "Content-Type": "text/event-stream" },
        }),
      );
    vi.stubGlobal("fetch", fetch);
    setToken("fixture-only-token");
    await stream(controller.signal, onEvent, vi.fn());
    expect(onEvent).toHaveBeenCalledWith({
      creditRequestId: 42,
      status: "USED",
    });
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe(
      "Bearer fixture-only-token",
    );
    expect(fetch).toHaveBeenCalledOnce();
  });
});
