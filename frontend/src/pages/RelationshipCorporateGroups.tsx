import { CompanyLogo } from "../components/CompanyLogo";
import { ArrowLeft, ArrowUpRight, Plus, Search } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { api } from "../api/client";
import type { RequestCompany } from "../components/CreateRequest";
import {
  ExposureHeadroomBar,
  facilityHeadroom,
  groupHeadroom,
} from "../components/ExposureHeadroomBar";
import { Badge, Empty, ErrorBox, Loading } from "../components/ui";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import type { Exposure } from "../types";

type GroupBreakdown = Omit<Exposure, "creditLimitId" | "counterpartyId"> & {
  facilities: Exposure[];
};

type GroupRead = { data: GroupBreakdown } | { error: string };

export default function RelationshipCorporateGroups({
  portfolio,
  active,
  selectedGroupId,
  selectGroup,
  createRequest,
  openCounterparty,
}: {
  portfolio: RelationshipPortfolio;
  active: boolean;
  selectedGroupId: number | null;
  selectGroup: (id: number | null) => void;
  createRequest: (company: RequestCompany) => void;
  openCounterparty: (id: number) => void;
}) {
  const { groups, members, counterparties, errors, refreshing } = portfolio;
  const selection = selectedGroupId;
  const cardId = useId();
  const lastOpened = useRef<number | null>(null);
  const detailsHeading = useRef<HTMLHeadingElement>(null);
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("");
  const [sort, setSort] = useState("name");
  const [reads, setReads] = useState<Record<number, GroupRead>>({});
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!active || !groups?.length) return;
    const controller = new AbortController();
    for (const group of groups) {
      const id = group.corporateGroupId;
      void api<GroupBreakdown>(
        `/group-credit-limits/groups/${id}/exposure-breakdown`,
        "GET",
        undefined,
        controller.signal,
      )
        .then((data) => {
          if (!Array.isArray(data?.facilities))
            throw new Error("The shared exposure breakdown is unavailable.");
          if (!controller.signal.aborted)
            setReads((previous) => ({ ...previous, [id]: { data } }));
        })
        .catch((error: Error) => {
          if (!controller.signal.aborted)
            setReads((previous) => ({
              ...previous,
              [id]: { error: error.message },
            }));
        });
    }
    return () => controller.abort();
  }, [active, groups, retry]);

  const companies = new Map(
    counterparties?.map((company) => [company.id, company]),
  );
  const membersByGroup = new Map<number, Set<number>>();
  for (const member of members ?? []) {
    const ids =
      membersByGroup.get(member.corporateGroupId) ?? new Set<number>();
    ids.add(member.counterpartyId);
    membersByGroup.set(member.corporateGroupId, ids);
  }

  function breakdown(id: number) {
    const result = reads[id];
    return result && "data" in result ? result.data : null;
  }

  function groupMemberIds(id: number) {
    return [
      ...new Set([
        ...(membersByGroup.get(id) ?? []),
        ...(breakdown(id)?.facilities.map(
          (facility) => facility.counterpartyId,
        ) ?? []),
      ]),
    ];
  }

  const selected = groups?.find(
    (group) => group.corporateGroupId === selection,
  );
  useEffect(() => {
    if (!active) return;
    if (selected) {
      detailsHeading.current?.focus();
    } else if (lastOpened.current !== null) {
      document.getElementById(`${cardId}-${lastOpened.current}`)?.focus();
      lastOpened.current = null;
    }
  }, [active, cardId, selected?.corporateGroupId]);

  if (selected) {
    const id = selected.corporateGroupId;
    const result = reads[id];
    const data = breakdown(id);
    const facilitiesByMember = new Map<number, Exposure[]>();
    for (const facility of data?.facilities ?? []) {
      const facilities = facilitiesByMember.get(facility.counterpartyId) ?? [];
      facilities.push(facility);
      facilitiesByMember.set(facility.counterpartyId, facilities);
    }
    const memberIds = groupMemberIds(id);
    return (
      <>
        <button
          className="text-button rm-back"
          onClick={() => selectGroup(null)}
        >
          <ArrowLeft size={16} aria-hidden="true" /> Back to corporate groups
        </button>
        <section className="panel">
          <div className="section-head">
            <div>
              <span className="eyebrow">SHARED GROUP CAPACITY</span>
              <h2
                id={`${cardId}-member-heading`}
                ref={detailsHeading}
                tabIndex={-1}
                className="company-brand"
              >
                <CompanyLogo name={selected.corporateGroupName} />
                <span>{selected.corporateGroupName}</span>
              </h2>
            </div>
          </div>
          <ExposureHeadroomBar
            title="Shared group limit"
            approvedLabel="Shared approved credit limit"
            capacity={data ? facilityHeadroom(data) : groupHeadroom(selected)}
            attention
          />
        </section>
        <section className="panel" aria-labelledby={`${cardId}-members`}>
          <div className="section-head">
            <div>
              <h2 id={`${cardId}-members`}>Member counterparties</h2>
              <p className="muted">
                Individual facilities are shown separately from the shared
                limit.
              </p>
            </div>
          </div>
          {result && "error" in result && (
            <div className="rm-group-error">
              <ErrorBox message="The detailed group exposure could not be loaded." />
              <button onClick={() => setRetry(retry + 1)}>
                Retry exposure
              </button>
            </div>
          )}
          {!result || (members === null && refreshing) ? (
            <Loading />
          ) : memberIds.length ? (
            <div className="rm-facility-grid">
              {memberIds.map((memberId) => {
                const company = companies.get(memberId);
                const name = company?.name ?? "Group member";
                const facilities = facilitiesByMember.get(memberId) ?? [];
                return (
                  <article className="rm-group-member" key={memberId}>
                    <div className="section-head">
                      <div>
                        <h3 className="company-brand">
                          <CompanyLogo name={name} />
                          <span>{name}</span>
                        </h3>
                      </div>
                      {company && <Badge status={company.status} />}
                    </div>
                    {facilities.length ? (
                      facilities.map((facility) => (
                        <ExposureHeadroomBar
                          key={facility.creditLimitId}
                          title="Individual facility"
                          capacity={facilityHeadroom(facility)}
                          approvedLabel="Approved individual limit"
                          embedded
                          attention
                        />
                      ))
                    ) : (
                      <p className="muted">
                        {data
                          ? "No individual facility at your institution."
                          : "Individual facility exposure is unavailable."}
                      </p>
                    )}
                    {company && (
                      <div className="rm-member-actions">
                        <button
                          className="text-button"
                          onClick={() => openCounterparty(memberId)}
                        >
                          View counterparty <ArrowUpRight size={15} />
                        </button>
                        {facilities.length > 0 &&
                          company.status === "ACTIVE" && (
                            <button
                              className="rm-member-request"
                              aria-label={`Request credit for ${name}`}
                              onClick={() =>
                                createRequest({
                                  counterpartyId: memberId,
                                  name,
                                })
                              }
                            >
                              <Plus size={15} /> Request credit
                            </button>
                          )}
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          ) : (
            <Empty
              title={
                members === null
                  ? "Group membership is unavailable"
                  : "No members in this group"
              }
            />
          )}
        </section>
      </>
    );
  }

  const term = query.trim().toLowerCase();
  const headroom = (group: NonNullable<typeof groups>[number]) =>
    breakdown(group.corporateGroupId)?.availableHeadroom ?? group.headroom;
  const visible = (groups ?? []).filter((group) => {
    const memberNames = groupMemberIds(group.corporateGroupId)
      .map((id) => companies.get(id)?.name)
      .join(" ");
    const available = headroom(group);
    return (
      `${group.corporateGroupName} ${memberNames}`
        .toLowerCase()
        .includes(term) &&
      (!filter || (filter === "available" ? available > 0 : available <= 0))
    );
  });
  visible.sort((a, b) => {
    const byName = a.corporateGroupName.localeCompare(b.corporateGroupName);
    if (sort === "headroom") return headroom(b) - headroom(a) || byName;
    if (sort === "utilization") {
      const usage = (group: typeof a) => {
        const limit =
          breakdown(group.corporateGroupId)?.limitAmount ?? group.amount;
        return limit > 0 ? (limit - headroom(group)) / limit : 0;
      };
      return usage(b) - usage(a) || byName;
    }
    return byName;
  });

  return (
    <section className="panel">
      <div className="section-head">
        <div>
          <h2>Connected corporate groups</h2>
          <p className="muted">
            Groups with an approved shared limit at your institution.
          </p>
        </div>
      </div>
      <div
        className="rm-directory-toolbar"
        role="group"
        aria-label="Corporate group controls"
      >
        <label className="rm-directory-search">
          <Search size={17} aria-hidden="true" />
          <input
            aria-label="Search corporate groups"
            placeholder="Search groups or member companies…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        <select
          aria-label="Filter groups by headroom"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
        >
          <option value="">All headroom</option>
          <option value="available">Has headroom</option>
          <option value="limited">No headroom</option>
        </select>
        <select
          aria-label="Sort corporate groups"
          value={sort}
          onChange={(event) => setSort(event.target.value)}
        >
          <option value="name">Group name</option>
          <option value="headroom">Most headroom</option>
          <option value="utilization">Top utilization</option>
        </select>
      </div>
      {errors.length > 0 && (
        <ErrorBox message={[...new Set(errors)].join(" ")} />
      )}
      {groups === null && (refreshing || errors.length === 0) ? (
        <Loading />
      ) : !visible.length ? (
        <Empty
          title={
            groups?.length
              ? "No matching corporate groups"
              : "No shared group limits available"
          }
        />
      ) : (
        <div className="rm-facility-grid rm-group-grid">
          {visible.map((group) => {
            const id = group.corporateGroupId;
            const data = breakdown(id);
            const count = groupMemberIds(id).length;
            const memberCount =
              members === null
                ? refreshing
                  ? "Loading members…"
                  : "Member count unavailable"
                : `${count} member ${count === 1 ? "company" : "companies"}`;
            return (
              <article className="rm-group-card" key={id}>
                <ExposureHeadroomBar
                  title={group.corporateGroupName}
                  headingIcon={<CompanyLogo name={group.corporateGroupName} />}
                  description={memberCount}
                  approvedLabel="Shared approved credit limit"
                  capacity={
                    data ? facilityHeadroom(data) : groupHeadroom(group)
                  }
                  embedded
                  attention
                />
                <button
                  id={`${cardId}-${id}`}
                  className="text-button"
                  aria-label={`View members of ${group.corporateGroupName}`}
                  onClick={() => {
                    lastOpened.current = id;
                    selectGroup(id);
                  }}
                >
                  {members === null
                    ? "View members"
                    : `View ${count} ${count === 1 ? "member" : "members"}`}
                  <ArrowUpRight size={15} aria-hidden="true" />
                </button>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
