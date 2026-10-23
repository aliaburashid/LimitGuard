import { describe, expect, it } from "vitest";
import { money, requestReference } from "./format";

describe("BHD presentation", () => {
  it("displays whole dinars with grouped amounts", () => {
    expect(money(750000)).toBe("BD 750,000");
    expect(money(0)).toBe("BD 0");
    expect(money(1500000)).toBe("BD 1,500,000");
    expect(money(-1000)).toBe("BD -1,000");
  });

  it("rounds only the display without changing the input", () => {
    const amount = 100000.25;
    expect(money(amount)).toBe("BD 100,000");
    expect(money(100000.75)).toBe("BD 100,001");
    expect(amount).toBe(100000.25);
  });

  it("pads request references without truncating larger IDs", () => {
    expect(requestReference(28)).toBe("CR-000028");
    expect(requestReference(1)).toBe("CR-000001");
    expect(requestReference(1234567)).toBe("CR-1234567");
  });
});
