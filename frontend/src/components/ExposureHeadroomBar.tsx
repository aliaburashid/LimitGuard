import type { ReactNode } from "react";
import type { Exposure, GroupExposure } from "../types";
import { ExposureTrack, type ExposureSegment } from "./ExposureTrack";

export type Headroom = {
  limit: number;
  available: number;
} & (
  | { used: number; reserved: number; committed?: never }
  | { committed: number; used?: never; reserved?: never }
);

export function facilityHeadroom(
  exposure: Pick<
    Exposure,
    "limitAmount" | "usedAmount" | "reservedAmount" | "availableHeadroom"
  >,
): Headroom {
  return {
    limit: Number(exposure.limitAmount),
    used: Number(exposure.usedAmount),
    reserved: Number(exposure.reservedAmount),
    available: Number(exposure.availableHeadroom),
  };
}
export function groupHeadroom(group: GroupExposure): Headroom {
  return {
    limit: Number(group.amount),
    committed: Number(group.exposure),
    available: Number(group.headroom),
  };
}

// keeps preview comparisons in hundredths so decimal input does not change a boundary decision
export const minorAmount = (amount: number) => Math.round(amount * 100);
export const remainingHeadroom = (available: number, proposed: number) =>
  (minorAmount(available) - minorAmount(proposed)) / 100;
const preciseAmount = new Intl.NumberFormat("en-GB", {
  maximumFractionDigits: 2,
});
export const preciseMoney = (amount: number) =>
  `BD ${preciseAmount.format(amount)}`;

export function ExposureHeadroomBar({
  title,
  headingIcon,
  capacity,
  proposed,
  compact = false,
  embedded = false,
  description,
  approvedLabel = "Approved capacity",
  attention = false,
}: {
  title: string;
  headingIcon?: ReactNode;
  capacity: Headroom;
  proposed?: number;
  compact?: boolean;
  embedded?: boolean;
  description?: string;
  approvedLabel?: string;
  attention?: boolean;
}) {
  const { limit, available } = capacity;
  const breakdown: (ExposureSegment & { name: string })[] =
    capacity.committed === undefined
      ? [
          { name: "Used", color: "used", value: capacity.used },
          { name: "Reserved", color: "reserved", value: capacity.reserved },
        ]
      : [
          {
            name: "Committed (used + reserved)",
            color: "used",
            value: capacity.committed,
          },
        ];
  const committed = breakdown.reduce((sum, segment) => sum + segment.value, 0);
  const remaining = remainingHeadroom(available, proposed ?? 0);
  const over = Math.max(0, -remaining);
  const usage =
    limit > 0 ? (100 * (committed + (proposed ?? 0))) / limit : null;
  const utilization = usage === null ? "—" : `${usage.toFixed(1)}%`;
  const attentionLevel =
    attention && usage !== null
      ? usage >= 95
        ? "critical"
        : usage >= 80
          ? "high"
          : "normal"
      : undefined;
  const segments: (ExposureSegment & { name: string })[] = [
    ...breakdown,
    ...(proposed !== undefined
      ? [{ name: "Proposed", color: "proposed" as const, value: proposed }]
      : []),
    { name: "Available", color: "available", value: Math.max(0, remaining) },
  ];
  const Container = embedded ? "div" : "article";
  return (
    <Container
      className={`rm-headroom${compact ? " rm-headroom-compact" : ""}${embedded ? " rm-headroom-embedded" : ""}`}
    >
      <div className="rm-headroom-heading">
        <div>
          <h3 className={headingIcon ? "company-brand" : undefined}>
            {headingIcon}
            <span>{title}</span>
          </h3>
          {description && (
            <p className="rm-headroom-description">{description}</p>
          )}
        </div>
        <span
          className={over ? "rm-over-limit" : ""}
          data-attention={attentionLevel}
        >
          {utilization} {proposed === undefined ? "utilized" : "projected"}
        </span>
      </div>
      <p className="small">
        {approvedLabel} <strong>{preciseMoney(limit)}</strong>
      </p>
      <ExposureTrack
        className="rm-capacity-track"
        limit={limit}
        segments={segments}
        label={`${title}. Approved ${preciseMoney(limit)}. ${segments.map((s) => `${s.name} ${preciseMoney(s.value)}`).join(". ")}${over ? `. Exceeded by ${preciseMoney(over)}` : ""}`}
      />
      <dl className="rm-capacity-legend">
        {segments.map((segment) => (
          <div key={segment.color}>
            <dt>
              <i className={`rm-segment-${segment.color}`} />
              {segment.color === "available" && proposed !== undefined
                ? "Projected headroom"
                : segment.name}
            </dt>
            <dd>{preciseMoney(segment.value)}</dd>
          </div>
        ))}
        {over > 0 && (
          <div className="rm-over-limit">
            <dt>
              <i className="rm-segment-exceeded" />
              Exceeded capacity
            </dt>
            <dd>{preciseMoney(over)}</dd>
          </div>
        )}
      </dl>
    </Container>
  );
}

export function GroupHeadroom({ group }: { group: GroupExposure }) {
  return (
    <ExposureHeadroomBar
      title={group.corporateGroupName}
      compact
      capacity={groupHeadroom(group)}
    />
  );
}
