import { CompanyLogo } from "../components/CompanyLogo";
import {
  ArrowLeft,
  ArrowRight,
  Database,
  Info,
  Layers3,
  Plus,
} from "lucide-react";
import { useEffect, useId, useState } from "react";
import { api } from "../api/client";
import type { RequestCompany } from "../components/CreateRequest";
import {
  ExposureHeadroomBar,
  facilityHeadroom,
  groupHeadroom,
  preciseMoney,
} from "../components/ExposureHeadroomBar";
import { Badge, Empty, ErrorBox, Loading } from "../components/ui";
import { useAuth } from "../context/Auth";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import type { Counterparty, Exposure, GroupExposure } from "../types";
import { date, label, utilization } from "../utils/format";

type SharedExposure = Omit<Exposure, "creditLimitId" | "counterpartyId">;
const shortDate = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
});
const shortTime = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
});

function requestTimestamp(value: string | null | undefined) {
  if (!value) return null;
  const timestamp = new Date(value);
  return Number.isFinite(timestamp.getTime()) ? timestamp : null;
}

export function RelationshipCounterpartyDetails({
  company,
  initialFacilityId,
  onFacilityChange,
  portfolio,
  backLabel,
  goBack,
  createRequest,
  openRequest,
  openCompanyRequests,
}: {
  company: Counterparty;
  initialFacilityId?: number;
  onFacilityChange: (id: number) => void;
  portfolio: RelationshipPortfolio;
  backLabel: string;
  goBack: () => void;
  createRequest: (company: RequestCompany) => void;
  openRequest: (id: number) => void;
  openCompanyRequests: (company: Counterparty) => void;
}) {
  const { user } = useAuth();
  const { facilities, groups, members, requests, refreshing } = portfolio;
  const facilityId = initialFacilityId;
  const [sharedRead, setSharedRead] = useState<{
    group: GroupExposure;
    exposure: SharedExposure;
  }>();
  const exposureHeading = useId();
  const historyHeading = useId();
  const choices = (facilities ?? []).filter(
    (facility) => facility.counterpartyId === company.id,
  );
  const facility =
    choices.length === 1
      ? choices[0]
      : choices.find((item) => item.creditLimitId === facilityId);
  const membership = members?.find(
    (member) => member.counterpartyId === company.id,
  );
  const group = groups?.find(
    (item) => item.corporateGroupId === membership?.corporateGroupId,
  );

  useEffect(() => {
    if (!group) return;
    const controller = new AbortController();
    void api<SharedExposure>(
      `/group-credit-limits/groups/${group.corporateGroupId}/exposure-breakdown`,
      "GET",
      undefined,
      controller.signal,
    )
      .then((exposure) => {
        const balances = [
          exposure.limitAmount,
          exposure.usedAmount,
          exposure.reservedAmount,
          exposure.availableHeadroom,
        ];
        if (
          !controller.signal.aborted &&
          balances.every(
            (value) => value != null && Number.isFinite(Number(value)),
          )
        ) {
          setSharedRead({ group, exposure });
        }
      })
      .catch(() => {
        // the directory already provides authorized committed exposure and headroom
      });
    return () => controller.abort();
  }, [group]);

  const individual = facility ? facilityHeadroom(facility) : null;
  const shared =
    sharedRead && sharedRead.group === group
      ? facilityHeadroom(sharedRead.exposure)
      : group
        ? groupHeadroom(group)
        : null;
  const active = company.status === "ACTIVE";
  const effective =
    active && individual && members !== null && (!membership || shared)
      ? Math.max(
          0,
          Math.min(
            individual.available,
            shared?.available ?? individual.available,
          ),
        )
      : null;
  let constraint = "No individual facility";
  if (!active)
    constraint = label(company.status) + " status prevents new requests";
  else if (facilities === null) constraint = "Facility balances unavailable";
  else if (!individual && choices.length)
    constraint = "Select an individual facility";
  else if (individual) {
    if (members === null) constraint = "Group membership unavailable";
    else if (membership && !shared) {
      constraint =
        groups === null
          ? "Shared capacity unavailable"
          : "No shared limit at your institution";
    } else if (shared && shared.available < individual.available) {
      constraint = "Limited by shared group headroom";
    } else if (shared && shared.available === individual.available) {
      constraint = "Individual and shared limits are equal";
    } else constraint = "Limited by individual facility headroom";
  }

  const history = (requests ?? [])
    .filter((request) => request.counterpartyId === company.id)
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
    .slice(0, 5)
    .map((request) => ({
      request,
      submitted: requestTimestamp(request.createdAt),
      updated: requestTimestamp(request.updatedAt),
    }));

  return (
    <div className="rm-page rm-counterparty-details">
      <button className="text-button rm-back" onClick={goBack}>
        <ArrowLeft size={16} aria-hidden="true" />
        {backLabel}
      </button>
      <header className="rm-overview-header rm-counterparty-identity">
        <div>
          <div className="rm-counterparty-title">
            <CompanyLogo name={company.name} large />
            <h1>{company.name}</h1>
            <Badge status={company.status} />
          </div>
          <p>
            {membership?.corporateGroupName ??
              (members === null
                ? "Group membership unavailable"
                : "Independent counterparty")}
            {user?.financialInstitution.name && (
              <> · {user.financialInstitution.name}</>
            )}
            {choices.length > 0 && <> · Individual credit facility</>}
          </p>
        </div>
        {active && choices.length > 0 && (
          <button
            className="primary rm-counterparty-request"
            disabled={!facility}
            onClick={() => {
              if (facility)
                createRequest({
                  counterpartyId: company.id,
                  name: company.name,
                  facilityId: facility.creditLimitId,
                });
            }}
          >
            <Plus size={17} aria-hidden="true" />
            Request Credit
          </button>
        )}
      </header>
      <dl
        className="rm-counterparty-capacity"
        aria-label="Available credit capacity"
      >
        <div className="panel rm-counterparty-capacity-card">
          <dt>
            <span className="rm-counterparty-capacity-icon" aria-hidden="true">
              <Database size={24} strokeWidth={1.65} />
            </span>
            Effective available headroom
            {effective !== null && (
              <span
                title="Subject to request checks"
                aria-label="Subject to request checks"
                role="img"
              >
                <Info size={14} aria-hidden="true" />
              </span>
            )}
          </dt>
          <dd>{effective === null ? "—" : preciseMoney(effective)}</dd>
          <dd className="rm-counterparty-note">{constraint}</dd>
        </div>
        <div className="panel rm-counterparty-capacity-card">
          <dt>
            <span className="rm-counterparty-capacity-icon" aria-hidden="true">
              <Layers3 size={24} strokeWidth={1.65} />
            </span>
            Individual available headroom
          </dt>
          <dd>{individual ? preciseMoney(individual.available) : "—"}</dd>
          <dd className="rm-counterparty-note">
            {facility && individual && individual.limit > 0
              ? utilization(facility).toFixed(1) +
                "% of " +
                preciseMoney(individual.limit) +
                " utilized"
              : "Individual facility balance"}
          </dd>
        </div>
      </dl>
      <section
        className="panel rm-counterparty-exposure"
        aria-labelledby={exposureHeading}
      >
        <div className="section-head">
          <h2 id={exposureHeading}>Credit exposure</h2>
          {choices.length > 1 && (
            <select
              className="rm-counterparty-facility"
              aria-label="Individual facility"
              value={facility?.creditLimitId ?? ""}
              onChange={(event) => {
                const id = Number(event.target.value);
                onFacilityChange(id);
              }}
            >
              <option value="" disabled>
                Select a facility
              </option>
              {choices.map((item, index) => (
                <option key={item.creditLimitId} value={item.creditLimitId}>
                  Facility {index + 1} · {preciseMoney(item.limitAmount)}{" "}
                  approved
                </option>
              ))}
            </select>
          )}
        </div>
        <div className="rm-capacity-comparison">
          {individual ? (
            <ExposureHeadroomBar
              embedded
              attention
              title="Individual facility"
              capacity={individual}
              approvedLabel="approved"
            />
          ) : facilities === null ? (
            refreshing ? (
              <Loading />
            ) : (
              <ErrorBox message="Facility data is unavailable. Refresh the workspace to try again." />
            )
          ) : choices.length ? (
            <p className="rm-counterparty-note">
              Select an individual facility to view its balances.
            </p>
          ) : (
            <Empty title="No credit facility">
              Your institution has no individual facility for this company.
            </Empty>
          )}
          {shared && (
            <ExposureHeadroomBar
              embedded
              attention
              title={
                membership
                  ? membership.corporateGroupName + " · Shared group limit"
                  : "Shared group limit"
              }
              capacity={shared}
              approvedLabel="approved"
            />
          )}
        </div>
      </section>
      {history.length > 0 && (
        <section
          className="panel rm-counterparty-history"
          aria-labelledby={historyHeading}
        >
          <div className="section-head">
            <h2 id={historyHeading}>Recent credit requests</h2>
            <button
              className="text-button rm-history-action"
              onClick={() => openCompanyRequests(company)}
            >
              View all requests <ArrowRight size={15} aria-hidden="true" />
            </button>
          </div>
          <div
            className="table-scroll rm-counterparty-requests"
            role="region"
            aria-labelledby={historyHeading}
            tabIndex={0}
          >
            <table>
              <thead>
                <tr>
                  <th scope="col">Amount</th>
                  <th scope="col">Submitted</th>
                  <th scope="col">Status</th>
                  <th className="rm-counterparty-updated" scope="col">
                    Last updated
                  </th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {history.map(({ request, submitted, updated }) => {
                  const submittedDay = submitted
                    ? shortDate.format(submitted)
                    : "";
                  const updatedDay = updated ? shortDate.format(updated) : "";
                  const changed =
                    updated && updated.getTime() !== submitted?.getTime();
                  return (
                    <tr key={request.id}>
                      <td className="numeric">
                        {preciseMoney(request.amount)}
                      </td>
                      <td className="rm-history-date">
                        {submitted ? (
                          <time
                            dateTime={request.createdAt}
                            title={date(request.createdAt)}
                          >
                            <span>{submittedDay}</span>{" "}
                            <span className="rm-history-clock">
                              {shortTime.format(submitted)}
                            </span>
                          </time>
                        ) : (
                          <span aria-label="Submitted time unavailable">—</span>
                        )}
                        {changed && (
                          <time
                            className="rm-history-mobile-update"
                            dateTime={request.updatedAt}
                            aria-label={
                              "Last updated " + date(request.updatedAt)
                            }
                          >
                            {updatedDay !== submittedDay && (
                              <span>{updatedDay}</span>
                            )}
                            Updated {shortTime.format(updated)}
                          </time>
                        )}
                      </td>
                      <td>
                        <Badge status={request.status} />
                      </td>
                      <td className="rm-counterparty-updated rm-history-date">
                        {updated ? (
                          <time dateTime={request.updatedAt}>
                            {date(request.updatedAt)}
                          </time>
                        ) : (
                          <span
                            aria-label="Last updated time unavailable"
                            title="Last updated time unavailable"
                          >
                            —
                          </span>
                        )}
                      </td>
                      <td>
                        <button
                          className="text-button rm-history-action"
                          aria-label="View details"
                          onClick={() => openRequest(request.id)}
                        >
                          View
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {requests === null && !refreshing && (
        <ErrorBox message="Request history is unavailable. Refresh the workspace to try again." />
      )}
      <details className="rm-counterparty-metadata">
        <summary>Additional details</summary>
        <dl>
          <div>
            <dt>Created</dt>
            <dd>{date(company.createdAt)}</dd>
          </div>
          <div>
            <dt>Last updated</dt>
            <dd>{date(company.updatedAt)}</dd>
          </div>
        </dl>
      </details>
    </div>
  );
}
