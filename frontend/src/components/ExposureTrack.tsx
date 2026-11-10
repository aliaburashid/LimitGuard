import "../styles/exposure-track.css";

export type ExposureSegment = {
  color: "used" | "reserved" | "available" | "proposed";
  value: number;
};

export function ExposureTrack({
  limit,
  segments,
  label,
  className,
}: {
  limit: number;
  segments: ExposureSegment[];
  label?: string;
  className?: string;
}) {
  let remaining = Number.isFinite(limit) ? Math.max(0, limit) : 0;
  const visible = segments.flatMap((segment) => {
    const amount = Math.min(remaining, Math.max(0, segment.value));
    if (!Number.isFinite(amount) || amount <= 0) return [];
    remaining -= amount;
    return [{ color: segment.color, width: (amount * 100) / limit }];
  });
  // absorb subpixel rounding on the last visible segment of a complete track
  const fillsTrack = remaining <= limit * Number.EPSILON * segments.length;
  return (
    <div
      className={className ? "exposure-track " + className : "exposure-track"}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      {visible.map((segment, index) => (
        <span
          key={segment.color}
          className={"rm-segment-" + segment.color}
          data-fill={
            fillsTrack && index === visible.length - 1 ? "" : undefined
          }
          style={{ width: segment.width + "%" }}
        />
      ))}
    </div>
  );
}

export function facilitySegments(exposure: {
  usedAmount: number;
  reservedAmount: number;
  availableHeadroom: number;
}): ExposureSegment[] {
  return [
    { color: "used", value: Number(exposure.usedAmount) },
    { color: "reserved", value: Number(exposure.reservedAmount) },
    { color: "available", value: Number(exposure.availableHeadroom) },
  ];
}

export function committedSegments(
  committed: number,
  available: number,
): ExposureSegment[] {
  return [
    { color: "used", value: Number(committed) },
    { color: "available", value: Number(available) },
  ];
}
