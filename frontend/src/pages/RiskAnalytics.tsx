import { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ArrowUpRight,
  BrainCircuit,
  Building2,
  CheckCircle2,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { api, ApiError } from "../api/client";
import { usePortfolio } from "../hooks/usePortfolio";
import { useAuth } from "../context/Auth";
import { bhd, contributions, percent } from "../analytics/metrics";
import {
  ChartCard,
  ContributionChart,
  DataNote,
  DebtCapitalChart,
  ExposureStack,
  ProbabilityGauge,
  ProfitabilityChart,
  RatioChart,
} from "../components/AnalyticsCharts";
import { Badge, Empty, ErrorBox, Loading, Modal } from "../components/ui";
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
  const [historyPage, setHistoryPage] = useState(0);
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
    setHistoryPage(0);
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
  const groupContributions =
    group && portfolio.members && portfolio.facilities
      ? contributions(group, portfolio.members, portfolio.facilities)
      : null;
  const history = (portfolio.requests ?? [])
    .filter((r) => r.counterpartyId === request?.counterpartyId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const predictionStale =
    !!predictionInput && (!profile || profileKey(profile) !== predictionInput);
  const pending = request?.status === "PENDING_APPROVAL";
  const canDecide =
    pending && request.requesterId !== user?.id && !loading && !loadError;
  async function predict() {
    if (!profile || !request) return;
    predictor.current?.abort();
    const c = new AbortController();
    predictor.current = c;
    setPredicting(true);
    setPredictionError("");
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
        !Number.isFinite(Number(result.probability)) ||
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
  return (
    <div className="risk-analytics-page">
      <div className="analytics-page-heading">
        <div>
          <button className="back-to-queue" onClick={back}>
            <ArrowLeft size={14} />
            Approval queue
          </button>
          <h1>Risk Analytics</h1>
          <p>Financial analysis and model-assisted credit review.</p>
        </div>
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
                {requestReference(r.id)} · {r.counterpartyName}
              </option>
            ))}
          </select>
        </label>
      </div>
      {loadError && <ErrorBox message={loadError} />}
      {requestId === null ? (
        <ChartCard title="Choose a credit request">
          <Empty title="Start a company assessment">
            Select a request above or choose Assess risk in the approval queue.
          </Empty>
        </ChartCard>
      ) : !request ? (
        <Loading />
      ) : (
        <>
          <section className="company-summary">
            <div className="company-heading">
              <span className="company-mark large">
                <Building2 size={25} />
              </span>
              <div>
                <h2 title={request.counterpartyName}>
                  {request.counterpartyName}
                </h2>
                <p>
                  {member?.corporateGroupName ??
                    (portfolio.members
                      ? "No corporate group membership"
                      : "Loading group membership…")}
                  <span>·</span>
                  {request.financialInstitutionName}
                </p>
              </div>
              <div className="company-status">
                <Badge status={request.status} />
                <small>{requestReference(request.id)}</small>
              </div>
            </div>
            <div className="assessment-summary-values">
              <div>
                <span>Requested credit</span>
                <strong>{bhd(request.amount)}</strong>
              </div>
              <div>
                <span>Committed exposure</span>
                <strong>
                  {exposure
                    ? bhd(
                        Number(exposure.usedAmount) +
                          Number(exposure.reservedAmount),
                      )
                    : "—"}
                </strong>
              </div>
              <div>
                <span>Individual headroom</span>
                <strong>
                  {exposure ? bhd(exposure.availableHeadroom) : "—"}
                </strong>
              </div>
              <div>
                <span>Counterparty status</span>
                <Badge status={request.counterpartyStatus} />
              </div>
            </div>
          </section>
          {pending && Number(request.amount) <= 500000 && (
            <div className="data-note warning">
              <ShieldCheck size={16} />
              <span>
                This existing pending request is below the current BHD 500,000
                automatic-reservation threshold. Review its history before
                deciding. Its backend status has been preserved.
              </span>
            </div>
          )}
          {!!portfolio.errors.length && (
            <ErrorBox message={[...new Set(portfolio.errors)].join(" ")} />
          )}
          <div className="assessment-top-grid">
            <ChartCard
              title="Model risk prediction"
              subtitle="Decision support · not an automatic credit decision"
              className="model-analytics-card"
              action={
                <span className="model-icon">
                  <BrainCircuit size={18} />
                </span>
              }
            >
              <div className="model-output-layout">
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
                <div className="model-output-meta">
                  <span className="field-caption">Model category</span>
                  {prediction && !predictionStale ? (
                    <Badge status={prediction.riskLevel} />
                  ) : (
                    <strong className="not-run">Not assessed</strong>
                  )}
                  <button
                    className="primary"
                    disabled={!profile || predicting}
                    onClick={() => void predict()}
                  >
                    <RefreshCw size={14} className={predicting ? "spin" : ""} />
                    {predicting
                      ? "Running model…"
                      : prediction
                        ? "Run prediction again"
                        : "Run risk prediction"}
                  </button>
                  <p>
                    {prediction && !predictionStale
                      ? "Actual model output for the saved financial profile."
                      : "Run the model using the company’s six saved financial indicators."}
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
              <div className="model-metadata">
                <div>
                  <span>Model version</span>
                  <strong>
                    {prediction?.modelVersion ?? "Available after prediction"}
                  </strong>
                </div>
                <div>
                  <span>Received locally</span>
                  <strong>{received || "—"}</strong>
                </div>
              </div>
              <p className="model-interpretation">
                Class 1 has no business-outcome definition in the supplied model
                metadata. This is not labelled as default probability. The API
                provides no prediction timestamp; the time above is local
                receipt time.
              </p>
            </ChartCard>
            <ChartCard
              title="Individual credit capacity"
              subtitle={`Facility #${request.creditLimitId} · current exposure`}
              action={<span className="chart-unit">BHD</span>}
            >
              {exposure ? <ExposureStack data={exposure} /> : <Loading />}
              {exposure && pending && (
                <div className="approval-impact">
                  <span>
                    After approval{" "}
                    <small>Illustrative; subject to backend validation</small>
                  </span>
                  <strong>
                    {bhd(
                      Number(exposure.availableHeadroom) -
                        Number(request.amount),
                    )}
                    <small>individual headroom</small>
                  </strong>
                </div>
              )}
              <DataNote>
                Used and reserved exposure consume capacity. Pending requests do
                not reserve additional credit.
              </DataNote>
            </ChartCard>
          </div>
          <div className="financial-section-heading">
            <div>
              <h2>Financial health analytics</h2>
              <p>
                Reported values for a single financial period. No historical
                series or peer benchmarks are supplied.
              </p>
            </div>
            {profile && (
              <span className="reporting-pill">
                Year end {profile.financialYearEnd}
              </span>
            )}
          </div>
          {profileError ? (
            <ChartCard title="Financial profile">
              <DataNote>{profileError}</DataNote>
            </ChartCard>
          ) : !profile ? (
            <Loading />
          ) : (
            <div className="financial-analytics-grid">
              <ChartCard
                title="Profitability"
                subtitle="Reported return and income margin · %"
              >
                <ProfitabilityChart profile={profile} />
                <div className="financial-value-pair">
                  <span>
                    Return on assets
                    <strong>{Number(profile.returnOnAssets)}%</strong>
                  </span>
                  <span>
                    Net income margin
                    <strong>{Number(profile.profitMargin)}%</strong>
                  </span>
                </div>
              </ChartCard>
              <ChartCard
                title="Capital structure"
                subtitle="Total debt as a share of total capital"
              >
                <DebtCapitalChart
                  value={Number(profile.debtToTotalCapitalPercent)}
                />
                <div className="mini-legend centered">
                  <span>
                    <i style={{ background: "#254d7d" }} />
                    Debt share
                  </span>
                  <span>
                    <i style={{ background: "#e8edf2" }} />
                    Remaining share
                  </span>
                </div>
              </ChartCard>
              <ChartCard
                title="Operating & debt ratios"
                subtitle="Each ratio has its own scale · multiples"
              >
                <RatioChart
                  name="Fixed asset turnover"
                  value={Number(profile.fixedAssetTurnover)}
                />
                <RatioChart
                  name="EBITDA / interest expense"
                  value={Number(profile.interestCoverage)}
                />
                <RatioChart
                  name="Total debt / EBITDA"
                  value={Number(profile.totalDebtToEbitda)}
                />
                {Number(profile.interestCoverage) < 1 && (
                  <p className="indicator-warning">
                    EBITDA / interest expense is below 1×.
                  </p>
                )}
              </ChartCard>
            </div>
          )}
          <div className="group-history-grid">
            <ChartCard
              title="Corporate group concentration"
              subtitle={member?.corporateGroupName ?? "Group relationship"}
              action={
                group ? (
                  <span className="utilization-pill">
                    {percent(group.exposure, group.amount)?.toFixed(1)}%
                    utilized
                  </span>
                ) : undefined
              }
            >
              {!portfolio.groups || !portfolio.members ? (
                portfolio.errors.length ? (
                  <DataNote>
                    Group configuration could not be loaded. No zero-capacity
                    assumption has been made.
                  </DataNote>
                ) : (
                  <Loading />
                )
              ) : !member ? (
                <DataNote>
                  This company has no corporate group membership.
                </DataNote>
              ) : !group ? (
                <DataNote>
                  Group limit not configured for this institution. Group
                  headroom is unavailable; this does not mean the group’s credit
                  is exhausted.
                </DataNote>
              ) : (
                <>
                  <div className="group-capacity-summary">
                    <div>
                      <span>Group limit</span>
                      <strong>{bhd(group.amount)}</strong>
                    </div>
                    <div>
                      <span>Committed exposure</span>
                      <strong>{bhd(group.exposure)}</strong>
                    </div>
                    <div>
                      <span>Available headroom</span>
                      <strong>{bhd(group.headroom)}</strong>
                    </div>
                  </div>
                  {groupContributions &&
                  !groupContributions.inconsistent &&
                  groupContributions.rows.length > 0 ? (
                    <>
                      <ContributionChart rows={groupContributions.rows} />
                      <p className="chart-disclosure">
                        Company contributions · BHD ·{" "}
                        {groupContributions.knownCount} of{" "}
                        {groupContributions.memberCount} members have
                        discoverable facility detail.{" "}
                        {groupContributions.rows.length > 6
                          ? "Largest six contributions shown."
                          : ""}
                      </p>
                    </>
                  ) : (
                    <DataNote>
                      {groupContributions?.inconsistent
                        ? "Group and member snapshots do not reconcile. Refresh before assessing concentration."
                        : "Member-level facility contributions are not available in the current API scope."}
                    </DataNote>
                  )}
                </>
              )}
            </ChartCard>
            <ChartCard
              title="Credit request history"
              subtitle={`${history.length} request${history.length === 1 ? "" : "s"} for this company`}
            >
              <div className="assessment-history-table">
                <table>
                  <thead>
                    <tr>
                      <th>Request / date</th>
                      <th>Amount</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {history
                      .slice(historyPage * 5, historyPage * 5 + 5)
                      .map((r) => (
                        <tr key={r.id}>
                          <td>
                            <button
                              className="table-link"
                              onClick={() => selectRequest(r.id)}
                            >
                              {requestReference(r.id)}
                              <ArrowUpRight size={11} />
                            </button>
                            <small>{date(r.createdAt)}</small>
                          </td>
                          <td className="numeric">{bhd(r.amount)}</td>
                          <td>
                            <Badge status={r.status} />
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
                {!history.length && (
                  <Empty title="No request history available" />
                )}
              </div>
              {history.length > 5 && (
                <div className="history-pagination">
                  <span>
                    Page {historyPage + 1} of {Math.ceil(history.length / 5)}
                  </span>
                  <div>
                    <button
                      disabled={!historyPage}
                      onClick={() => setHistoryPage((p) => p - 1)}
                    >
                      Previous
                    </button>
                    <button
                      disabled={(historyPage + 1) * 5 >= history.length}
                      onClick={() => setHistoryPage((p) => p + 1)}
                    >
                      Next
                    </button>
                  </div>
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
          <section className="decision-dock" aria-label="Credit decision">
            <div>
              <span className="decision-icon">
                <ShieldCheck size={20} />
              </span>
              <span>
                <strong>
                  {pending ? "Complete your credit review" : "Decision status"}
                </strong>
                <small>
                  {pending
                    ? `Maker: ${request.requesterName} · Model output is advisory.`
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
                Approve & Reserve
                <ArrowUpRight size={15} />
              </button>
            </div>
          </section>
        </>
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
