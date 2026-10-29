import { describe, expect, it } from "vitest";
import { parseEvent, notification } from "./events";
describe("banking event payloads", () => {
  const events = [
    [
      "credit-request-status",
      { creditRequestId: 5, status: "REJECTED" },
      "danger",
      "#5 rejected",
    ],
    [
      "counterparty-status",
      { counterpartyId: 12, status: "FROZEN" },
      "warning",
      "FROZEN",
    ],
    [
      "credit-limit-updated",
      { creditLimitId: 3, oldLimitAmount: 2000000, newLimitAmount: 2500000 },
      "info",
      "BD 2,500,000",
    ],
    [
      "group-credit-limit-updated",
      {
        groupCreditLimitId: 2,
        corporateGroupId: 9,
        oldLimitAmount: 4000000,
        newLimitAmount: 5000000,
      },
      "info",
      "group #9",
    ],
  ] as const;
  it.each(events)("validates and describes %s", (type, data, tone, text) => {
    const event = parseEvent(`event:${type}\ndata:${JSON.stringify(data)}`);
    expect(event).toEqual({ type, data });
    expect(notification(event!).tone).toBe(tone);
    expect(notification(event!).message).toContain(text);
  });
  it("ignores unknown, malformed, null and unsupported events", () => {
    for (const block of [
      ":connected",
      "event:counterparty-status\ndata:null",
      'event:counterparty-status\ndata:{"counterpartyId":1,"status":"MISSING"}',
      'event:credit-request-status\ndata:{"creditRequestId":-1,"status":"USED"}',
      "event:new\ndata:{}",
      'event:credit-limit-updated\ndata:{"creditLimitId":1,"oldLimitAmount":"0","newLimitAmount":4}',
    ])
      expect(parseEvent(block)).toBeNull();
  });
  it("does not describe every reservation as an approval", () => {
    expect(
      notification({
        type: "credit-request-status",
        data: { creditRequestId: 1, status: "RESERVED" },
      }).message,
    ).toBe("Credit request #1 reserved successfully.");
  });
});
