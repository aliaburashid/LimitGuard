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
    if (response.status === 401 && token) {
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
  onEvent: (data: { creditRequestId: number; status: string }) => void,
  onState: (s: string) => void,
) {
  let delay = 1000;
  while (!signal.aborted) {
    try {
      onState("Connecting");
      const res = await fetch(BASE + "/credit-requests/events", {
        signal,
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "text/event-stream",
        },
      });
      if (!res.ok) {
        if (res.status === 401) {
          setToken(null);
          window.dispatchEvent(new Event("session-expired"));
          return;
        }
        throw new Error("Stream unavailable");
      }
      onState("Live");
      delay = 1000;
      const reader = res.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (!signal.aborted) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        buffer = buffer.replace(/\r\n/g, "\n");
        let end;
        while ((end = buffer.indexOf("\n\n")) >= 0) {
          const block = buffer.slice(0, end);
          buffer = buffer.slice(end + 2);
          const eventName = block
            .split("\n")
            .find((line) => line.startsWith("event:"))
            ?.slice(6)
            .trim();
          if (eventName === "credit-request-status") {
            const data = block
              .split("\n")
              .filter((x) => x.startsWith("data:"))
              .map((x) => x.slice(5).trim())
              .join("\n");
            try {
              const parsed = JSON.parse(data);
              if (
                Number.isInteger(parsed.creditRequestId) &&
                typeof parsed.status === "string"
              )
                onEvent(parsed);
            } catch {
              /* Ignore malformed event */
            }
          }
        }
      }
    } catch {
      if (signal.aborted) return;
    }
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
