import { CompanyLogo } from "./CompanyLogo";
import type { GroupExposure } from "../types";
import { money } from "../utils/format";
import { ExposureTrack, committedSegments } from "./ExposureTrack";
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
          <h3 className="company-brand">
            <CompanyLogo name={group.corporateGroupName} />
            <span>{group.corporateGroupName}</span>
          </h3>
        </div>
        <span className={`utilization-chip ${percent >= 85 ? "elevated" : ""}`}>
          {percent.toFixed(1)}%
        </span>
      </div>
      <ExposureTrack
        className="group-exposure-track"
        limit={Number(group.amount)}
        segments={committedSegments(group.exposure, group.headroom)}
        label={`${group.corporateGroupName}: ${percent.toFixed(1)} percent utilized`}
      />
      <div className="group-figures">
        <span>
          Approved limit<strong>{money(group.amount)}</strong>
        </span>
        <span>
          Committed exposure<strong>{money(group.exposure)}</strong>
        </span>
        <span>
          Shared headroom<strong>{money(group.headroom)}</strong>
        </span>
      </div>
    </article>
  );
}
