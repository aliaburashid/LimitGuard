import type { GroupExposure } from "../types";
import { money } from "../utils/format";
export function GroupPanel({ group }: { group: GroupExposure }) {
  const percent =
    Number(group.amount) > 0
      ? (100 * group.exposure) / Number(group.amount)
      : 0;
  return (
    <article className="group-card">
      <div className="section-head">
        <div>
          <span className="eyebrow">CORPORATE GROUP</span>
          <h3>{group.corporateGroupName}</h3>
        </div>
        <span className={`utilization-chip ${percent >= 85 ? "elevated" : ""}`}>
          {percent.toFixed(1)}%
        </span>
      </div>
      <div
        className="group-bar"
        role="img"
        aria-label={`${group.corporateGroupName}: ${percent.toFixed(1)} percent utilized`}
      >
        <span style={{ width: `${Math.max(0, Math.min(100, percent))}%` }} />
      </div>
      <div className="group-figures">
        <span>
          Approved limit<strong>{money(group.amount)}</strong>
        </span>
        <span>
          Committed exposure<strong>{money(group.exposure)}</strong>
        </span>
        <span>
          Headroom<strong>{money(group.headroom)}</strong>
        </span>
      </div>
    </article>
  );
}
