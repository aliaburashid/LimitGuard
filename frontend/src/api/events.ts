import { money } from "../utils/format";
export type LiveEvent =
  | {
      type: "credit-request-status";
      data: { creditRequestId: number; status: string };
    }
  | {
      type: "counterparty-status";
      data: { counterpartyId: number; status: string };
    }
  | {
      type: "credit-limit-updated";
      data: {
        creditLimitId: number;
        oldLimitAmount: number;
        newLimitAmount: number;
      };
    }
  | {
      type: "group-credit-limit-updated";
      data: {
        groupCreditLimitId: number;
        corporateGroupId: number;
        oldLimitAmount: number;
        newLimitAmount: number;
      };
    };
export type Tone = "success" | "danger" | "warning" | "info";
export function parseEvent(block: string): LiveEvent | null {
  const lines = block.split("\n");
  const type = lines
    .find((l) => l.startsWith("event:"))
    ?.slice(6)
    .trim();
  try {
    const data = JSON.parse(
      lines
        .filter((l) => l.startsWith("data:"))
        .map((l) => l.slice(5).trimStart())
        .join("\n"),
    );
    if (!data || typeof data !== "object") return null;
    const id = (key: string) =>
      Number.isSafeInteger(data[key]) && data[key] > 0;
    const amounts = () =>
      [data.oldLimitAmount, data.newLimitAmount].every(
        (n) => typeof n === "number" && Number.isFinite(n),
      );
    if (
      type === "credit-request-status" &&
      id("creditRequestId") &&
      [
        "PENDING_APPROVAL",
        "RESERVED",
        "USED",
        "REJECTED",
        "CANCELLED",
        "EXPIRED",
      ].includes(data.status)
    )
      return { type, data };
    if (
      type === "counterparty-status" &&
      id("counterpartyId") &&
      ["ACTIVE", "FROZEN", "CLOSED"].includes(data.status)
    )
      return { type, data };
    if (type === "credit-limit-updated" && id("creditLimitId") && amounts())
      return { type, data };
    if (
      type === "group-credit-limit-updated" &&
      id("groupCreditLimitId") &&
      id("corporateGroupId") &&
      amounts()
    )
      return { type, data };
  } catch {
    /* An invalid event must not interrupt the stream. */
  }
  return null;
}
export function notification(event: LiveEvent): {
  message: string;
  tone: Tone;
} {
  if (event.type === "credit-request-status") {
    const { creditRequestId: id, status } = event.data;
    const text: Record<string, string> = {
      RESERVED: "reserved successfully",
      PENDING_APPROVAL: "awaiting Risk Officer approval",
      USED: "marked as used",
      REJECTED: "rejected",
      CANCELLED: "cancelled",
      EXPIRED: "expired",
    };
    return {
      message: `Credit request #${id} ${text[status]}.`,
      tone:
        status === "REJECTED"
          ? "danger"
          : ["EXPIRED", "CANCELLED"].includes(status)
            ? "warning"
            : ["RESERVED", "USED"].includes(status)
              ? "success"
              : "info",
    };
  }
  if (event.type === "counterparty-status")
    return {
      message: `Counterparty #${event.data.counterpartyId} status changed to ${event.data.status}.`,
      tone: event.data.status === "ACTIVE" ? "success" : "warning",
    };
  return {
    message: `${event.type === "credit-limit-updated" ? `Company credit limit #${event.data.creditLimitId}` : `Corporate group #${event.data.corporateGroupId} credit limit`} updated: ${money(event.data.oldLimitAmount)} → ${money(event.data.newLimitAmount)}.`,
    tone: "info",
  };
}
