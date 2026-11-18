import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowUpRight,
  BrainCircuit,
  CheckCircle2,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { api, ApiError } from "../api/client";
import { usePortfolio } from "../hooks/usePortfolio";
import { useAuth } from "../context/Auth";
import { bhd } from "../analytics/metrics";
import {
  ChartCard,
  DataNote,
  ExposureStack,
  ProbabilityGauge,
} from "../components/AnalyticsCharts";
import { CompanyLogo, CorporateGroupLogo } from "../components/CompanyLogo";
import { counterpartyLogoDomain } from "../config/verifiedLogoDomains";
import "../styles/risk-assessment.css";
import { Badge, Empty, ErrorBox, Loading, Modal } from "../components/ui";
import { GroupRiskAssessment } from "../components/GroupRiskAssessment";
import type { Exposure, FinancialProfile, Prediction, Request } from "../types";
import { date, label, requestReference } from "../utils/format";
const profileKey = (p: FinancialProfile) =>
  JSON.stringify([
    p.counterpartyId,
    p.returnOnAssets,
    p.profitMargin,
    p.fixedAssetTurnover,
    p.debtToTotalCapitalPercent,
    p.interestCoverage,
    p.totalDebtToEbitda,
    p.financialYearEnd,
  ]);
export function RiskAnalytics({
  requestId,
  selectRequest,
  version,
  back,
  changed,
}: {
  requestId: number | null;
  selectRequest: (id: number) => void;
  version: number;
  back: () => void;
  changed: () => void;
}) {
  const { user } = useAuth();
  const portfolio = usePortfolio(version);
  const [request, setRequest] = useState<Request | null>(null);
  const [exposure, setExposure] = useState<Exposure | null>(null);
  const [profile, setProfile] = useState<FinancialProfile | null>(null);
  const [profileError, setProfileError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [loading, setLoading] = useState(false);
  const [prediction, setPrediction] = useState<Prediction | null>(null);
  const [predictionInput, setPredictionInput] = useState("");
  const [received, setReceived] = useState("");
  const [predictionError, setPredictionError] = useState("");
  const [predicting, setPredicting] = useState(false);
  const predictor = useRef<AbortController | null>(null);
  const [confirmation, setConfirmation] = useState<"approve" | "reject" | null>(
    null,
  );
  const [reason, setReason] = useState("");
  const [deciding, setDeciding] = useState(false);
  const [decisionError, setDecisionError] = useState("");
  const [outcome, setOutcome] = useState("");
  const [expandedHistory, setExpandedHistory] = useState(false);
  useEffect(() => {
    if (requestId !== null || !portfolio.requests?.length) return;
    const candidates = portfolio.requests;
    const initial =
      candidates.find((r) => r.status === "PENDING_APPROVAL") ?? candidates[0];
    if (initial) selectRequest(initial.id);
  }, [requestId, portfolio.requests, selectRequest]);
  useEffect(() => {
    setRequest(null);
    setExposure(null);
    setProfile(null);
    setPrediction(null);
    setPredictionInput("");
    setReceived("");
    setPredictionError("");
    setOutcome("");
    setExpandedHistory(false);
    setConfirmation(null);
    setReason("");
    predictor.current?.abort();
    setPredicting(false);
  }, [requestId]);
  useEffect(() => {
    if (requestId === null) return;
    const c = new AbortController();
    setLoading(true);
    setLoadError("");
    setProfileError("");
    void api<Request>(
      `/credit-requests/${requestId}`,
      "GET",
      undefined,
      c.signal,
    )
      .then(async (r) => {
        if (c.signal.aborted) return;
        setRequest(r);
        await Promise.all([
          api<Exposure>(
            `/credit-limits/${r.creditLimitId}/exposure`,
            "GET",
            undefined,
            c.signal,
          ).then((e) => {
            if (!c.signal.aborted) setExposure(e);
          }),
          api<FinancialProfile>(
            `/counterparties/${r.counterpartyId}/financial-profile`,
            "GET",
            undefined,
            c.signal,
          )
            .then((p) => {
              if (!c.signal.aborted) setProfile(p);
            })
            .catch((e) => {
              if (!c.signal.aborted) {
                setProfile(null);
                setProfileError(
                  e instanceof ApiError && e.status === 404
                    ? "No financial profile has been recorded for this company."
                    : e.message,
                );
              }
            }),
        ]);
      })
      .catch((e) => {
        if (!c.signal.aborted) setLoadError(e.message);
      })
      .finally(() => {
        if (!c.signal.aborted) setLoading(false);
      });
    return () => c.abort();
  }, [requestId, version]);
  useEffect(() => () => predictor.current?.abort(), []);
  const member = portfolio.members?.find(
    (m) => m.counterpartyId === request?.counterpartyId,
  );
  const group = portfolio.groups?.find(
    (g) => g.corporateGroupId === member?.corporateGroupId,
  );
  const history = (portfolio.requests ?? [])
    .filter((r) => r.counterpartyId === request?.counterpartyId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const recentHistory = history.filter((row) => row.id !== request?.id);
  const displayedHistory = expandedHistory
    ? history
    : recentHistory.slice(0, 3);
  const predictionStale =
    !!predictionInput && (!profile || profileKey(profile) !== predictionInput);
  const pending = request?.status === "PENDING_APPROVAL";
  const canDecide =
    user?.role === "RISK_OFFICER" &&
    pending &&
    request.requesterId !== user.id &&
    !loading &&
    !loadError;
  async function predict() {
    if (!profile || !request) return;
    predictor.current?.abort();
    const c = new AbortController();
    predictor.current = c;
    setPredicting(true);
    setPredictionError("");
    setPrediction(null);
    setPredictionInput("");
    setReceived("");
    const input = profileKey(profile);
    try {
      const result = await api<Prediction>(
        `/counterparties/${request.counterpartyId}/risk-prediction`,
        "POST",
        undefined,
        c.signal,
      );
      if (c.signal.aborted) return;
      if (
        typeof result.probability !== "number" ||
        !Number.isFinite(result.probability) ||
        Number(result.probability) < 0 ||
        Number(result.probability) > 1 ||
        !["LOW_RISK", "MEDIUM_RISK", "HIGH_RISK"].includes(result.riskLevel)
      )
        throw new Error("The model returned an unsupported prediction.");
      setPrediction(result);
      setPredictionInput(input);
      setReceived(new Date().toLocaleString("en-GB"));
    } catch (e) {
      if (!c.signal.aborted) setPredictionError((e as Error).message);
    } finally {
      if (!c.signal.aborted) setPredicting(false);
    }
  }
  async function decide() {
    if (
      !request ||
      !confirmation ||
      deciding ||
      (confirmation === "reject" && !reason.trim())
    )
      return;
    setDeciding(true);
    setDecisionError("");
    try {
      const result = await api<Request>(
        `/credit-requests/${request.id}/${confirmation}`,
        "PATCH",
        confirmation === "reject" ? { reason: reason.trim() } : undefined,
      );
      if (result?.id !== request.id || typeof result.status !== "string")
        throw new Error(
          "The decision response could not be read. Refresh the request status before trying again.",
        );
      setRequest(result);
      setOutcome(
        `Operation completed. ${requestReference(result.id)} is now ${label(result.status).toLowerCase()}.`,
      );
      setConfirmation(null);
      setReason("");
      changed();
    } catch (e) {
      setDecisionError((e as Error).message);
      changed();
    } finally {
      setDeciding(false);
    }
  }
  const requestSelector = (
    <label className="assessment-selector">
      <span>Credit request</span>
      <select
        aria-label="Select credit request for assessment"
        value={requestId ?? ""}
        disabled={deciding || !!confirmation}
        onChange={(e) => selectRequest(Number(e.target.value))}
      >
        <option value="" disabled>
          Select a request
        </option>
        {portfolio.requests?.map((r) => (
          <option key={r.id} value={r.id}>
            {r.counterpartyName} · {requestReference(r.id)}
          </option>
        ))}
      </select>
    </label>
  );
  return (
    <div className="risk-analytics-page ra-workspace">
      <header className="ra-page-heading">
        <div>
          <button className="back-to-queue" onClick={back}>
            <ArrowLeft size={14} />
            Approval queue
          </button>
          <h1>Risk Analytics</h1>
        </div>
      </header>
      {loadError && <ErrorBox message={loadError} />}
      {requestId === null ? (
        <ChartCard title="Choose a credit request">
          {requestSelector}
          <Empty title="Start a company assessment">
            Select a request to review its financial profile and credit
            capacity.
          </Empty>
        </ChartCard>
      ) : !request ? (
        <Loading />
      ) : (
        <>
          <section
            className="ra-company-header"
            aria-label="Company and request summary"
          >
            <CompanyLogo
              name={request.counterpartyName}
              domain={counterpartyLogoDomain(request.counterpartyName)}
              large
            />
            <div className="ra-company-identity">
              <h2>{request.counterpartyName}</h2>
              <p>
                {member?.corporateGroupName ??
                  (portfolio.members
                    ? "Standalone company"
                    : "Loading group membership…")}
              </p>
              <div className="ra-badges">
                <Badge status={request.counterpartyStatus} />
                <Badge status={request.status} />
              </div>
            </div>
            <div className="ra-request-summary">
              <span>Requested credit</span>
              <strong>{bhd(request.amount)}</strong>
              <small>{requestReference(request.id)}</small>
            </div>
            {requestSelector}
          </section>
          {pending && Number(request.amount) <= 500000 && (
            <div className="data-note warning">
              <ShieldCheck size={16} />
              <span>
                This existing pending request is below the current BHD 500,000
                automatic-reservation threshold. Review its history before
                deciding.
              </span>
            </div>
          )}
          {!!portfolio.errors.length && (
            <ErrorBox message={[...new Set(portfolio.errors)].join(" ")} />
          )}
          <div className="ra-assessment-grid">
            <ChartCard
              title="ML Risk Assessment"
              className="ra-model-card"
              action={
                <span className="ra-advisory-label">
                  <BrainCircuit size={15} />
                  Advisory
                </span>
              }
            >
              <div className="ra-model-body" aria-busy={predicting}>
                <ProbabilityGauge
                  probability={
                    prediction && !predictionStale
                      ? Number(prediction.probability)
                      : null
                  }
                  risk={
                    prediction && !predictionStale ? prediction.riskLevel : null
                  }
                />
                <div className="ra-model-output">
                  <span className="field-caption">Model classification</span>
                  {prediction && !predictionStale ? (
                    <Badge status={prediction.riskLevel} />
                  ) : (
                    <strong className="not-run">Not assessed</strong>
                  )}
                  <button
                    className="primary"
                    disabled={!profile || predicting || loading}
                    onClick={() => void predict()}
                  >
                    <RefreshCw size={15} className={predicting ? "spin" : ""} />
                    {predicting
                      ? "Running model…"
                      : prediction
                        ? "Run prediction again"
                        : "Run risk prediction"}
                  </button>
                  <p className="ra-model-status" role="status">
                    {predicting
                      ? "Assessing the saved financial profile."
                      : prediction && !predictionStale
                        ? "Assessment completed."
                        : "No current assessment."}
                  </p>
                </div>
              </div>
              {predictionError && <ErrorBox message={predictionError} />}
              {predictionStale && (
                <div className="data-note warning">
                  The saved financial profile is unavailable or has changed. Run
                  the prediction again.
                </div>
              )}
              <div className="ra-model-metadata">
                <div>
                  <span>Model version</span>
                  <strong>{prediction?.modelVersion ?? "—"}</strong>
                </div>
                <div>
                  <span>Received locally</span>
                  <strong>{received || "—"}</strong>
                </div>
              </div>
              <p className="ra-model-disclosure">
                Advisory only. The model does not approve or reject credit
                requests. Class-1 probability is not a verified probability of
                default.
              </p>
            </ChartCard>
            <ChartCard
              title="Credit Capacity"
              className="ra-capacity-card"
              action={<span className="chart-unit">BHD</span>}
            >
              {exposure ? (
                <ExposureStack data={exposure} />
              ) : loadError ? (
                <DataNote>Current facility capacity is unavailable.</DataNote>
              ) : (
                <Loading />
              )}
              {exposure && pending && (
                <div className="approval-impact">
                  <span>
                    Headroom after approval
                    <small>Illustrative; subject to backend validation</small>
                  </span>
                  <strong>
                    {bhd(
                      Number(exposure.availableHeadroom) -
                        Number(request.amount),
                    )}
                  </strong>
                </div>
              )}
            </ChartCard>
          </div>
          {outcome && (
            <div className="decision-outcome" role="status">
              <CheckCircle2 size={17} />
              {outcome}
            </div>
          )}
          <section
            className="decision-dock ra-decision-bar"
            aria-label="Credit decision"
          >
            <div>
              <ShieldCheck size={19} />
              <span>
                <strong>
                  {pending ? "Credit decision" : "Decision status"}
                </strong>
                <small>
                  {pending
                    ? `Requested by ${request.requesterName}`
                    : `${requestReference(request.id)} is ${label(request.status).toLowerCase()}.`}
                </small>
              </span>
            </div>
            <div className="decision-dock-actions">
              <button
                className="danger"
                disabled={!canDecide}
                onClick={() => {
                  setDecisionError("");
                  setConfirmation("reject");
                }}
              >
                Reject Request
              </button>
              <button
                className="primary"
                disabled={!canDecide}
                onClick={() => {
                  setDecisionError("");
                  setConfirmation("approve");
                }}
              >
                Approve &amp; Reserve
                <ArrowUpRight size={15} />
              </button>
            </div>
          </section>
          <ChartCard
            title="Financial Health"
            className="ra-financial-card"
            action={
              profile ? (
                <span className="reporting-pill">
                  Year end {profile.financialYearEnd}
                </span>
              ) : undefined
            }
          >
            {profileError ? (
              <DataNote>{profileError}</DataNote>
            ) : !profile ? (
              <Loading />
            ) : (
              <div className="ra-financial-categories">
                <section aria-label="Profitability">
                  <h3>Profitability</h3>
                  <dl>
                    <div>
                      <dt>Return on assets</dt>
                      <dd>{profile.returnOnAssets}%</dd>
                    </div>
                    <div>
                      <dt>Net income margin</dt>
                      <dd>{profile.profitMargin}%</dd>
                    </div>
                  </dl>
                </section>
                <section aria-label="Capital Structure">
                  <h3>Capital Structure</h3>
                  <dl>
                    <div>
                      <dt>Debt / total capital</dt>
                      <dd>{profile.debtToTotalCapitalPercent}%</dd>
                    </div>
                  </dl>
                  {Number(profile.debtToTotalCapitalPercent) >= 0 &&
                    Number(profile.debtToTotalCapitalPercent) <= 100 && (
                      <div className="ra-debt-track" aria-hidden="true">
                        <span
                          style={{
                            width: `${profile.debtToTotalCapitalPercent}%`,
                          }}
                        />
                      </div>
                    )}
                </section>
                <section aria-label="Operating Efficiency and Debt Coverage">
                  <h3>Operating Efficiency &amp; Debt Coverage</h3>
                  <dl>
                    <div>
                      <dt>Fixed asset turnover</dt>
                      <dd>{profile.fixedAssetTurnover}×</dd>
                    </div>
                    <div>
                      <dt>Interest coverage</dt>
                      <dd>{profile.interestCoverage}×</dd>
                    </div>
                    <div>
                      <dt>Total debt / EBITDA</dt>
                      <dd>{profile.totalDebtToEbitda}×</dd>
                    </div>
                  </dl>
                </section>
              </div>
            )}
          </ChartCard>
          <div className={`ra-context-grid${member ? "" : " ra-standalone"}`}>
            {member && (
              <ChartCard
                title="Corporate Group Assessment"
                className="ra-company-group"
                action={<CorporateGroupLogo name={member.corporateGroupName} />}
              >
                <h3>{member.corporateGroupName}</h3>
                {group ? (
                  <dl className="ra-group-capacity">
                    <div>
                      <dt>Shared limit</dt>
                      <dd>{bhd(group.amount)}</dd>
                    </div>
                    <div>
                      <dt>Committed exposure</dt>
                      <dd>{bhd(group.exposure)}</dd>
                    </div>
                    <div>
                      <dt>Available headroom</dt>
                      <dd>{bhd(group.headroom)}</dd>
                    </div>
                  </dl>
                ) : (
                  <DataNote>
                    {portfolio.groups === null
                      ? "Shared capacity is unavailable. Refresh the workspace to try again."
                      : "Group limit not configured for this institution. Shared capacity is unavailable."}
                  </DataNote>
                )}
                <GroupRiskAssessment
                  key={member.corporateGroupId}
                  groups={[
                    {
                      corporateGroupId: member.corporateGroupId,
                      corporateGroupName: member.corporateGroupName,
                    },
                  ]}
                  fixedGroup
                />
              </ChartCard>
            )}
            <ChartCard
              title="Recent Credit Activity"
              className="ra-recent-card"
              action={
                history.length > 0 ? (
                  <button
                    className="text-button"
                    onClick={() => setExpandedHistory((value) => !value)}
                  >
                    {expandedHistory ? "Show less" : "View complete history"}
                    <ArrowUpRight size={13} />
                  </button>
                ) : undefined
              }
            >
              {!displayedHistory.length ? (
                <Empty title="No other credit activity" />
              ) : (
                <div className="ra-activity-list">
                  {displayedHistory.map((r) => (
                    <article className="ra-activity-row" key={r.id}>
                      <button
                        className="table-link"
                        onClick={() => selectRequest(r.id)}
                      >
                        {requestReference(r.id)}
                        <ArrowUpRight size={12} />
                      </button>
                      <time dateTime={r.createdAt}>{date(r.createdAt)}</time>
                      <strong>{bhd(r.amount)}</strong>
                      <Badge status={r.status} />
                    </article>
                  ))}
                </div>
              )}
            </ChartCard>
          </div>
        </>
      )}
      {!!portfolio.groups?.length && (
        <details className="ra-portfolio-assessment">
          <summary>Other portfolio group assessments</summary>
          <p className="muted small">
            Independent assessment. Selecting a group here does not change the
            company or request above.
          </p>
          <GroupRiskAssessment groups={portfolio.groups} />
        </details>
      )}
      {confirmation && request && (
        <Modal
          title={
            confirmation === "approve" ? "Approve request" : "Reject request"
          }
          onClose={() => {
            if (!deciding) setConfirmation(null);
          }}
        >
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void decide();
            }}
          >
            <p className="confirmation-summary">
              {requestReference(request.id)} · {request.counterpartyName}
              <strong>{bhd(request.amount)}</strong>
            </p>
            <p className="muted">
              {confirmation === "approve"
                ? "Approval reserves the requested capacity, subject to backend headroom and counterparty checks."
                : "Record the reason for your decision. The requester will receive the resulting status update."}
            </p>
            {confirmation === "reject" && (
              <label className="field">
                <span>Rejection reason</span>
                <textarea
                  required
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  rows={3}
                  maxLength={250}
                  placeholder="Explain the credit decision"
                />
              </label>
            )}
            {decisionError && <ErrorBox message={decisionError} />}
            <div className="modal-actions">
              <button
                type="button"
                disabled={deciding}
                onClick={() => setConfirmation(null)}
              >
                Back to assessment
              </button>
              <button
                className={confirmation === "approve" ? "primary" : "danger"}
                disabled={
                  deciding || (confirmation === "reject" && !reason.trim())
                }
              >
                {deciding
                  ? "Submitting decision…"
                  : `Confirm ${confirmation} request`}
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
