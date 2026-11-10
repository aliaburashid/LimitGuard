import { CompanyLogo } from "../components/CompanyLogo";
import { ArrowUpRight, Search, SlidersHorizontal } from "lucide-react";
import { lazy, Suspense, useEffect, useId, useState } from "react";
import { compact, exposureTotals } from "../analytics/metrics";
import { preciseMoney } from "../components/ExposureHeadroomBar";
import { ExposureTrack, facilitySegments } from "../components/ExposureTrack";
import { Badge, Empty, ErrorBox, Loading, Pager } from "../components/ui";
import type { RequestCompany } from "../components/CreateRequest";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import { useAuth } from "../context/Auth";
import {
  readWorkspaceState,
  saveWorkspaceState,
} from "../utils/workspaceState";
import { label, utilization } from "../utils/format";

import type { Counterparty } from "../types";
import { RelationshipCounterpartyDetails } from "./RelationshipCounterpartyDetails";

import { RelationshipFacilities } from "./RelationshipFacilities";

const RelationshipCorporateGroups = lazy(
  () => import("./RelationshipCorporateGroups"),
);

const tabs = [
  { id: "individual", label: "Individual Counterparties" },
  { id: "groups", label: "Corporate Groups" },
  { id: "facilities", label: "All Facilities" },
] as const;

type CounterpartyView = {
  tab: (typeof tabs)[number]["id"];
  detailId: number | null;
  facilityId?: number;
  groupId: number | null;
};

export function RelationshipCounterparties({
  portfolio,
  restoreView = false,
  selection,
  groupView = false,
  groupSelection,
  facilitiesView = false,
  facilitySelection,
  createRequest,
  openRequest,
  openCompanyRequests,
}: {
  portfolio: RelationshipPortfolio;
  restoreView?: boolean;
  selection: { id: number } | null;
  groupView?: boolean;
  groupSelection?: { id: number } | null;
  createRequest: (company: RequestCompany) => void;
  facilitiesView?: boolean;
  facilitySelection?: number;
  openRequest: (id: number) => void;
  openCompanyRequests: (company: Counterparty) => void;
}) {
  const {
    individualCounterparties: counterparties,
    directoryError,
    facilities,
    members,
  } = portfolio;
  const { user } = useAuth();
  const viewKey = user
    ? user.id + ":" + user.financialInstitution.id + ":counterparties"
    : null;
  const [savedView] = useState(() =>
    restoreView && viewKey
      ? readWorkspaceState<CounterpartyView>(viewKey)
      : null,
  );
  const [detailId, setDetailId] = useState<number | null>(
    savedView ? savedView.detailId : (selection?.id ?? null),
  );
  const [detailFacilityId, setDetailFacilityId] = useState(
    savedView?.facilityId,
  );
  const [groupId, setGroupId] = useState(
    savedView ? savedView.groupId : (groupSelection?.id ?? null),
  );
  const tabId = useId();
  const [tab, setTab] = useState<CounterpartyView["tab"]>(
    savedView?.tab ??
      (groupView ? "groups" : facilitiesView ? "facilities" : "individual"),
  );
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [sort, setSort] = useState("name");
  const [groupFilter, setGroupFilter] = useState("");
  const [facilityFilter, setFacilityFilter] = useState("");
  const [moreFilters, setMoreFilters] = useState(false);
  const [page, setPage] = useState(0);
  const linkedCompanyId = facilities?.find(
    (facility) => facility.creditLimitId === facilitySelection,
  )?.counterpartyId;
  useEffect(() => {
    if (savedView && restoreView) return;
    setDetailId(linkedCompanyId ?? selection?.id ?? null);
    setDetailFacilityId(linkedCompanyId ? facilitySelection : undefined);
    setGroupId(groupSelection?.id ?? null);
    setTab(groupView ? "groups" : facilitiesView ? "facilities" : "individual");
  }, [
    selection,
    groupView,
    groupSelection,
    facilitiesView,
    facilitySelection,
    linkedCompanyId,
    savedView,
    restoreView,
  ]);
  useEffect(() => {
    if (viewKey)
      saveWorkspaceState<CounterpartyView>(viewKey, {
        tab,
        detailId,
        facilityId: detailFacilityId,
        groupId,
      });
  }, [viewKey, tab, detailId, detailFacilityId, groupId]);
  const facilityByCompany = new Map<number, NonNullable<typeof facilities>>();
  for (const facility of facilities ?? []) {
    const existing = facilityByCompany.get(facility.counterpartyId) ?? [];
    facilityByCompany.set(facility.counterpartyId, [...existing, facility]);
  }
  const groupByCompany = new Map(
    (members ?? []).map((member) => [member.counterpartyId, member]),
  );
  const selectedCompany = (
    tab === "individual" ? counterparties : portfolio.counterparties
  )?.find((company) => company.id === detailId);
  const directoryIds = new Set(counterparties?.map((company) => company.id));
  const groupOptions = [
    ...new Map(
      (members ?? [])
        .filter((member) => directoryIds.has(member.counterpartyId))
        .map((member) => [member.corporateGroupId, member.corporateGroupName]),
    ),
  ].sort((a, b) => a[1].localeCompare(b[1]));
  const filtersActive = Boolean(
    query || status || groupFilter || facilityFilter || sort !== "name",
  );
  function resetFilters() {
    setQuery("");
    setStatus("");
    setGroupFilter("");
    setFacilityFilter("");
    setSort("name");
    setPage(0);
  }
  const rows = (counterparties ?? [])
    .map((company) => ({
      company,
      membership: groupByCompany.get(company.id),
      capacity: exposureTotals(facilityByCompany.get(company.id) ?? []),
    }))
    .filter(
      ({ company, membership, capacity }) =>
        (!status || company.status === status) &&
        (!groupFilter ||
          (members !== null &&
            (groupFilter === "independent"
              ? !membership
              : String(membership?.corporateGroupId) === groupFilter))) &&
        (!facilityFilter ||
          (facilities !== null &&
            (facilityFilter === "with" ? Boolean(capacity) : !capacity))) &&
        `${company.name} ${membership?.corporateGroupName ?? ""}`
          .toLowerCase()
          .includes(query.trim().toLowerCase()),
    );
  rows.sort((a, b) => {
    const sortValue = ({ capacity }: (typeof rows)[number]) => {
      if (!capacity) return -Infinity;
      if (sort === "utilization")
        return capacity.limitAmount > 0 ? utilization(capacity) : -Infinity;
      return sort === "approved"
        ? capacity.limitAmount
        : capacity.availableHeadroom;
    };
    const comparison =
      sort === "updated"
        ? b.company.updatedAt.localeCompare(a.company.updatedAt)
        : sort === "name"
          ? 0
          : sortValue(b) - sortValue(a);
    return comparison || a.company.name.localeCompare(b.company.name);
  });
  const totalPages = Math.ceil(rows.length / 10);
  const visiblePage = Math.min(page, Math.max(0, totalPages - 1));
  if (selectedCompany) {
    return (
      <RelationshipCounterpartyDetails
        key={selectedCompany.id}
        company={selectedCompany}
        initialFacilityId={detailFacilityId}
        onFacilityChange={setDetailFacilityId}
        portfolio={portfolio}
        backLabel={
          tab === "groups"
            ? "Back to corporate groups"
            : tab === "facilities"
              ? "Back to facilities"
              : "Back to counterparties"
        }
        goBack={() => {
          setDetailId(null);
          setDetailFacilityId(undefined);
        }}
        createRequest={createRequest}
        openRequest={openRequest}
        openCompanyRequests={openCompanyRequests}
      />
    );
  }
  return (
    <div className="rm-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">RELATIONSHIP PORTFOLIO</span>
          <h1>Counterparties</h1>
          <p>
            Manage corporate relationships and access credit facility
            information.
          </p>
        </div>
      </div>
      <div
        className="rm-relationship-tabs"
        role="tablist"
        aria-label="Counterparty views"
        onKeyDown={(event) => {
          if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key))
            return;
          event.preventDefault();
          const current = tabs.findIndex(({ id }) => id === tab);
          const direction = event.key === "ArrowLeft" ? -1 : 1;
          const next =
            event.key === "Home"
              ? 0
              : event.key === "End"
                ? tabs.length - 1
                : (current + direction + tabs.length) % tabs.length;
          setTab(tabs[next].id);
          event.currentTarget
            .querySelectorAll<HTMLButtonElement>('[role="tab"]')
            [next]?.focus();
        }}
      >
        {tabs.map(({ id, label }) => (
          <button
            key={id}
            id={`${tabId}-${id}`}
            type="button"
            role="tab"
            aria-selected={tab === id}
            aria-controls={`${tabId}-${id}-panel`}
            tabIndex={tab === id ? 0 : -1}
            onClick={() => setTab(id)}
          >
            {label}
          </button>
        ))}
      </div>
      <div
        id={`${tabId}-individual-panel`}
        className="rm-individual-panel"
        role="tabpanel"
        aria-labelledby={`${tabId}-individual`}
        hidden={tab !== "individual"}
      >
        <section className="panel rm-directory">
          <div className="section-head rm-directory-heading">
            <h2>Corporate Directory</h2>
            {counterparties && (
              <span className="muted" role="status">
                {rows.length !== counterparties.length &&
                  `${rows.length.toLocaleString()} of `}
                {counterparties.length.toLocaleString()}{" "}
                {counterparties.length === 1
                  ? "counterparty"
                  : "counterparties"}
              </span>
            )}
          </div>
          <div
            className="rm-directory-toolbar"
            role="group"
            aria-label="Directory controls"
          >
            <label className="rm-directory-search">
              <Search size={17} aria-hidden="true" />
              <input
                aria-label="Search counterparties by name"
                placeholder="Search companies or groups…"
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setPage(0);
                }}
              />
            </label>
            <select
              aria-label="Counterparty status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              {[
                ...new Set(
                  (counterparties ?? []).map((company) => company.status),
                ),
              ]
                .sort()
                .map((value) => (
                  <option key={value} value={value}>
                    {label(value)}
                  </option>
                ))}
            </select>
            <select
              aria-label="Sort counterparties"
              value={sort}
              onChange={(event) => {
                setSort(event.target.value);
                setPage(0);
              }}
            >
              <option value="name">Company name</option>
              <option value="utilization">Top utilization</option>
              <option value="approved">Top approved limit</option>
              <option value="headroom">Most headroom</option>
              <option value="updated">Recently updated</option>
            </select>
            <button
              type="button"
              aria-expanded={moreFilters}
              aria-controls={`${tabId}-filters`}
              onClick={() => setMoreFilters(!moreFilters)}
            >
              <SlidersHorizontal size={15} aria-hidden="true" /> Filters
              {(groupFilter || facilityFilter) &&
                `(${Number(Boolean(groupFilter)) + Number(Boolean(facilityFilter))})`}
            </button>
            {filtersActive && (
              <button
                type="button"
                className="rm-directory-reset"
                onClick={resetFilters}
              >
                Reset filters
              </button>
            )}
          </div>
          <div
            id={`${tabId}-filters`}
            className="rm-directory-toolbar rm-directory-secondary"
            hidden={!moreFilters}
          >
            <select
              aria-label="Corporate group"
              value={groupFilter}
              disabled={members === null}
              onChange={(event) => {
                setGroupFilter(event.target.value);
                setPage(0);
              }}
            >
              <option value="">All corporate groups</option>
              <option value="independent">Independent</option>
              {groupOptions.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
            <select
              aria-label="Facility availability"
              value={facilityFilter}
              disabled={facilities === null}
              onChange={(event) => {
                setFacilityFilter(event.target.value);
                setPage(0);
              }}
            >
              <option value="">All facilities</option>
              <option value="with">With facility</option>
              <option value="without">Without facility</option>
            </select>
          </div>
          {directoryError && <ErrorBox message={directoryError} />}
          {portfolio.errors.length > 0 && (
            <ErrorBox message={[...new Set(portfolio.errors)].join(" ")} />
          )}
          {counterparties === null && !directoryError ? (
            <Loading />
          ) : directoryError &&
            counterparties === null ? null : !rows.length ? (
            <Empty title="No matching counterparties">
              Try changing your search or filters.
            </Empty>
          ) : (
            <>
              <p className="rm-directory-scroll-note">
                Scroll the table to see more columns.
              </p>
              <div
                className="table-scroll rm-directory-table"
                tabIndex={0}
                role="region"
                aria-label="Corporate directory results"
              >
                <table aria-label="Counterparty portfolio">
                  <thead>
                    <tr>
                      <th scope="col">Company</th>
                      <th scope="col">Corporate Group</th>
                      <th scope="col">Credit Facility</th>
                      <th scope="col">Utilization</th>
                      <th scope="col">Status</th>
                      <th scope="col">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows
                      .slice(visiblePage * 10, visiblePage * 10 + 10)
                      .map(({ company, capacity, membership }) => {
                        const usedPercent =
                          capacity && capacity.limitAmount > 0
                            ? utilization(capacity)
                            : null;
                        return (
                          <tr key={company.id}>
                            <th scope="row">
                              <button
                                className="table-link rm-directory-company"
                                onClick={() => setDetailId(company.id)}
                              >
                                <CompanyLogo name={company.name} />
                                <span>{company.name}</span>
                              </button>
                            </th>
                            <td>
                              {membership?.corporateGroupName ??
                                (members === null ? "—" : "Independent")}
                            </td>
                            <td className="numeric">
                              {capacity ? (
                                <span
                                  title={preciseMoney(capacity.limitAmount)}
                                >
                                  BD {compact(capacity.limitAmount)} approved
                                </span>
                              ) : facilities !== null ? (
                                "No facility"
                              ) : portfolio.refreshing ? (
                                "Loading…"
                              ) : (
                                "Unavailable"
                              )}
                            </td>
                            <td>
                              {usedPercent === null || !capacity ? (
                                "—"
                              ) : (
                                <div className="rm-directory-utilization">
                                  <span
                                    className="numeric"
                                    data-level={
                                      usedPercent >= 95
                                        ? "critical"
                                        : usedPercent >= 80
                                          ? "high"
                                          : "normal"
                                    }
                                  >
                                    {usedPercent.toFixed(1)}%
                                  </span>
                                  <ExposureTrack
                                    className="rm-directory-track"
                                    limit={capacity.limitAmount}
                                    segments={facilitySegments(capacity)}
                                  />
                                </div>
                              )}
                            </td>
                            <td>
                              <Badge status={company.status} />
                            </td>
                            <td>
                              <button
                                className="text-button rm-directory-details"
                                aria-label={`View details for ${company.name}`}
                                onClick={() => setDetailId(company.id)}
                              >
                                View details{" "}
                                <ArrowUpRight size={14} aria-hidden="true" />
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                  </tbody>
                </table>
              </div>
              <Pager
                page={visiblePage}
                total={totalPages}
                count={rows.length}
                onChange={setPage}
              />
            </>
          )}
        </section>
      </div>
      <div
        id={`${tabId}-groups-panel`}
        className="rm-groups-panel"
        role="tabpanel"
        aria-labelledby={`${tabId}-groups`}
        hidden={tab !== "groups"}
      >
        <Suspense fallback={tab === "groups" ? <Loading /> : null}>
          <RelationshipCorporateGroups
            portfolio={portfolio}
            active={tab === "groups"}
            selectedGroupId={groupId}
            selectGroup={setGroupId}
            createRequest={createRequest}
            openCounterparty={setDetailId}
          />
        </Suspense>
      </div>
      <div
        id={tabId + "-facilities-panel"}
        className="rm-facilities-panel"
        role="tabpanel"
        aria-labelledby={tabId + "-facilities"}
        hidden={tab !== "facilities"}
      >
        {tab === "facilities" && (
          <>
            {facilitySelection &&
              facilities &&
              linkedCompanyId === undefined && (
                <ErrorBox message="The linked facility is unavailable in your institution’s directory." />
              )}
            <RelationshipFacilities
              portfolio={portfolio}
              openFacility={(facility) => {
                setDetailFacilityId(facility.creditLimitId);
                setDetailId(facility.counterpartyId);
              }}
            />
          </>
        )}
      </div>
    </div>
  );
}
