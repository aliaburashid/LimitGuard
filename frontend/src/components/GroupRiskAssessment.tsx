import { useEffect, useRef, useState } from "react";
import { BrainCircuit, RefreshCw } from "lucide-react";
import { assessGroupRisk } from "../api/groupRisk";
import type { CompanyRiskAssessment, GroupExposure } from "../types";
import { Badge, ErrorBox } from "./ui";

export function GroupRiskAssessment({
  groups,
}: {
  groups: GroupExposure[] | null;
}) {
  const [groupId, setGroupId] = useState("");
  const [results, setResults] = useState<CompanyRiskAssessment[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [received, setReceived] = useState("");
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    if (!groups || groups.some((g) => String(g.corporateGroupId) === groupId))
      return;
    controller.current?.abort();
    setBusy(false);
    setGroupId(groups[0] ? String(groups[0].corporateGroupId) : "");
    setResults(null);
    setReceived("");
  }, [groups, groupId]);
  async function run() {
    if (!groupId || busy) return;
    const c = new AbortController();
    controller.current?.abort();
    controller.current = c;
    setBusy(true);
    setError("");
    setResults(null);
    setReceived("");
    try {
      const values = await assessGroupRisk(Number(groupId), c.signal);
      if (c.signal.aborted) return;
      setResults(values);
      setReceived(new Date().toLocaleString("en-GB"));
    } catch (e) {
      if (!c.signal.aborted) setError((e as Error).message);
    } finally {
      if (!c.signal.aborted) setBusy(false);
    }
  }
  return (
    <section
      className="panel group-assessment-panel"
      aria-label="Parallel group risk assessment"
    >
      <div className="section-head">
        <div>
          <span className="eyebrow">PORTFOLIO RISK</span>
          <h2>Parallel group assessment</h2>
          <p className="muted">
            Assess saved financial profiles for your institution’s group
            companies.
          </p>
        </div>
        <BrainCircuit size={22} aria-hidden="true" />
      </div>
      <div className="group-assessment-controls">
        <label className="field">
          <span>Corporate group to assess</span>
          <select
            value={groupId}
            disabled={busy || !groups?.length}
            onChange={(e) => {
              setGroupId(e.target.value);
              setResults(null);
              setError("");
              setReceived("");
            }}
          >
            <option value="">
              {groups ? "Select a corporate group" : "Loading groups…"}
            </option>
            {groups?.map((g) => (
              <option key={g.corporateGroupId} value={g.corporateGroupId}>
                {g.corporateGroupName}
              </option>
            ))}
          </select>
        </label>
        <button
          className="primary"
          disabled={!groupId || busy}
          onClick={() => void run()}
        >
          <RefreshCw size={15} className={busy ? "spin" : ""} />
          {busy ? "Assessing group…" : "Run group assessment"}
        </button>
      </div>
      {groups?.length === 0 && (
        <p className="muted">
          No corporate group limits are available for your institution.
        </p>
      )}
      {error && <ErrorBox message={error} />}
      {results && (
        <>
          <p role="status" className="group-assessment-summary">
            {results.filter((r) => r.successful).length} of {results.length}{" "}
            companies assessed successfully.
            <span className="muted"> Received locally: {received}</span>
          </p>
          {results.length === 0 ? (
            <p className="muted">
              No authorized companies were returned for this group.
            </p>
          ) : (
            <div className="assessment-history-table">
              <table aria-label="Group assessment results">
                <thead>
                  <tr>
                    <th>Company</th>
                    <th>Outcome</th>
                    <th>Class 1 probability</th>
                  </tr>
                </thead>
                <tbody>
                  {results.map((r) => (
                    <tr key={r.counterpartyId}>
                      <td>
                        {r.counterpartyName}
                        <small>Company #{r.counterpartyId}</small>
                      </td>
                      <td>
                        {r.successful ? (
                          <Badge status={r.riskLevel!} />
                        ) : (
                          <span className="group-assessment-failed">
                            Assessment failed
                          </span>
                        )}
                      </td>
                      <td>
                        {r.successful
                          ? `${(r.probability! * 100).toFixed(2)}%`
                          : "Unavailable"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="small muted">
            Results use the saved financial profiles. Batch execution time and
            worker threads are recorded in the Spring Boot console.
          </p>
        </>
      )}
    </section>
  );
}
