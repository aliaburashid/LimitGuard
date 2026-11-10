import { ArrowRight } from "lucide-react";
import { useState } from "react";
import { compact, type Facility } from "../analytics/metrics";
import { preciseMoney } from "../components/ExposureHeadroomBar";
import { Badge, Empty, Loading, Pager } from "../components/ui";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import { utilization } from "../utils/format";

export function RelationshipFacilities({
  portfolio,
  openFacility,
}: {
  portfolio: RelationshipPortfolio;
  openFacility: (facility: Facility) => void;
}) {
  const { facilities, members } = portfolio;
  const [sort, setSort] = useState("name");
  const [page, setPage] = useState(0);
  const membershipByCompany = new Map(
    members?.map((member) => [member.counterpartyId, member]),
  );
  const table = [...(facilities ?? [])];
  table.sort(
    (a, b) =>
      (sort === "headroom"
        ? Number(b.availableHeadroom) - Number(a.availableHeadroom)
        : sort === "approved"
          ? Number(b.limitAmount) - Number(a.limitAmount)
          : sort === "utilization"
            ? utilization(b) - utilization(a)
            : 0) || a.name.localeCompare(b.name),
  );
  const totalPages = Math.ceil(table.length / 10);
  const visiblePage = Math.min(page, Math.max(0, totalPages - 1));
  const amounts = (value: number) => "BD " + compact(Number(value));

  return (
    <section className="panel rm-capacity-browse" aria-label="All facilities">
      <div className="section-head">
        <h2>All Facilities</h2>
        <div className="rm-directory-toolbar">
          <select
            aria-label="Sort facilities"
            value={sort}
            onChange={(event) => {
              setSort(event.target.value);
              setPage(0);
            }}
          >
            <option value="name">Company name</option>
            <option value="approved">Top approved limit</option>
            <option value="headroom">Most headroom</option>
            <option value="utilization">Top utilization</option>
          </select>
        </div>
      </div>
      {!facilities ? (
        portfolio.refreshing ? (
          <Loading />
        ) : (
          <Empty title="Facility directory unavailable" />
        )
      ) : table.length ? (
        <>
          <div
            className="table-scroll"
            tabIndex={0}
            role="region"
            aria-label="Authorized facilities"
          >
            <table aria-label="All Facilities">
              <thead>
                <tr>
                  <th scope="col">Company</th>
                  <th scope="col">Corporate Group</th>
                  <th scope="col">Approved Limit</th>
                  <th scope="col">Committed Exposure</th>
                  <th scope="col">Available Headroom</th>
                  <th scope="col">Utilization</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {table
                  .slice(visiblePage * 10, visiblePage * 10 + 10)
                  .map((item) => (
                    <tr key={item.creditLimitId}>
                      <td>
                        <button
                          type="button"
                          className="table-link"
                          onClick={() => openFacility(item)}
                        >
                          {item.name}
                        </button>{" "}
                        <Badge status={item.status ?? "UNKNOWN"} />
                      </td>
                      <td>
                        {membershipByCompany.get(item.counterpartyId)
                          ?.corporateGroupName ??
                          (members ? "Independent" : "—")}
                      </td>
                      <td
                        className="numeric"
                        title={preciseMoney(Number(item.limitAmount))}
                      >
                        {amounts(item.limitAmount)}
                      </td>
                      <td
                        className="numeric"
                        title={preciseMoney(
                          Number(item.usedAmount) + Number(item.reservedAmount),
                        )}
                      >
                        {amounts(
                          Number(item.usedAmount) + Number(item.reservedAmount),
                        )}
                      </td>
                      <td
                        className="numeric"
                        title={preciseMoney(Number(item.availableHeadroom))}
                      >
                        {amounts(item.availableHeadroom)}
                      </td>
                      <td>{utilization(item).toFixed(1)}%</td>
                      <td>
                        <button
                          className="text-button"
                          aria-label={`View exposure for ${item.name}`}
                          onClick={() => openFacility(item)}
                        >
                          View exposure <ArrowRight size={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={visiblePage}
            total={totalPages}
            count={table.length}
            onChange={setPage}
          />
        </>
      ) : (
        <Empty title="No matching facilities" />
      )}
    </section>
  );
}
