import { parseEvent, type LiveEvent } from "./events";
export const BASE = (import.meta.env.VITE_API_BASE_URL || "/api").replace(
  /\/$/,
  "",
);
let token: string | null = null;
export function setToken(value: string | null) {
  token = value;
}
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}
export function safeError(status: number, message?: unknown) {
  if (status === 401) return "Your session has expired. Please sign in again.";
  if (status === 403)
    return "Your account does not have permission to perform this action.";
  if (status >= 500)
    return "The service could not complete this operation. Please try again.";
  if (
    typeof message === "string" &&
    message.length < 300 &&
    !/exception|java\.|sql|jdbc|stack|eyJ/i.test(message)
  )
    return message;
  return "The operation could not be completed. Check the information and try again.";
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const requestToken = token;
  let response: Response;
  try {
    response = await fetch(BASE + path, {
      method,
      signal,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body && !(body instanceof FormData)
          ? { "Content-Type": "application/json" }
          : {}),
      },
      body:
        body instanceof FormData
          ? body
          : body === undefined
            ? undefined
            : JSON.stringify(body),
    });
  } catch (e) {
    if ((e as Error).name === "AbortError") throw e;
    throw new ApiError(
      "Unable to reach LimitGuard. Check your connection and try again.",
      0,
    );
  }
  const raw = await response.text();
  let data;
  try {
    data = JSON.parse(raw);
  } catch {
    data = raw;
  }
  if (!response.ok) {
    if (response.status === 401 && requestToken && token === requestToken) {
      setToken(null);
      window.dispatchEvent(new Event("session-expired"));
    }
    throw new ApiError(
      path === "/auth/users/login" && response.status === 401
        ? "Sign-in failed. Check your email and password."
        : safeError(response.status, data?.message),
      response.status,
    );
  }
  return data as T;
}
export async function stream(
  signal: AbortSignal,
  onEvent: (event: LiveEvent) => void,
  onState: (s: string) => void,
) {
  const sessionToken = token;
  const current = () =>
    !signal.aborted && !!sessionToken && token === sessionToken;
  let delay = 1000;
  while (current()) {
    let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
    try {
      onState("Connecting");
      const res = await fetch(BASE + "/credit-requests/events", {
        signal,
        headers: {
          Authorization: `Bearer ${sessionToken}`,
          Accept: "text/event-stream",
        },
        cache: "no-store",
      });
      if (!current()) {
        await res.body?.cancel();
        return;
      }
      if (res.status === 401) {
        setToken(null);
        window.dispatchEvent(new Event("session-expired"));
        return;
      }
      if (res.status === 403) {
        onState("Access denied");
        await res.body?.cancel();
        return;
      }
      if (
        !res.ok ||
        !res.headers.get("content-type")?.includes("text/event-stream") ||
        !res.body
      )
        throw new Error("Stream unavailable");
      onState("Live");
      reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (current()) {
        const { value, done } = await reader.read();
        if (done) break;
        if (!current()) return;
        buffer += decoder.decode(value, { stream: true });
        buffer = buffer.replace(/\r\n/g, "\n");
        if (buffer.length > 1048576) throw new Error("Oversized event");
        let end;
        while (current() && (end = buffer.indexOf("\n\n")) >= 0) {
          const event = parseEvent(buffer.slice(0, end));
          buffer = buffer.slice(end + 2);
          if (event) {
            delay = 1000;
            onEvent(event);
          }
        }
      }
    } catch {
      if (!current()) return;
    } finally {
      await reader?.cancel().catch(() => {});
      reader?.releaseLock();
    }
    if (!current()) return;
    onState("Reconnecting");
    await new Promise<void>((resolve) => {
      function finish() {
        clearTimeout(timer);
        signal.removeEventListener("abort", finish);
        resolve();
      }
      const timer = setTimeout(finish, delay);
      signal.addEventListener("abort", finish, { once: true });
      if (signal.aborted) finish();
    });
    delay = Math.min(delay * 2, 30000);
  }
}
