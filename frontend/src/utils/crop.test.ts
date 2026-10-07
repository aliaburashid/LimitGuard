import { describe, it, expect } from "vitest";
import { cropScale, constrainPosition } from "./crop";
describe("photo crop coverage", () => {
  it("covers a circular frame and clamps panning without exposing empty edges", () => {
    expect(cropScale(400, 200, 1)).toBe(1.3);
    expect(constrainPosition({ x: 1000, y: -1000 }, 400, 200, 1)).toEqual({
      x: 130,
      y: 0,
    });
    expect(constrainPosition({ x: -1000, y: 1000 }, 200, 400, 1)).toEqual({
      x: 0,
      y: 130,
    });
  });
  it("reclamps the crop when zoom is reduced", () => {
    expect(constrainPosition({ x: 300, y: 100 }, 400, 200, 2)).toEqual({
      x: 300,
      y: 100,
    });
    expect(constrainPosition({ x: 300, y: 100 }, 400, 200, 1)).toEqual({
      x: 130,
      y: 0,
    });
  });
});
