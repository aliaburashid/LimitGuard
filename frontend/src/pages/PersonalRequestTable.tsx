import { CompanyLogo } from "../components/CompanyLogo";
import { ChevronRight } from "lucide-react";
import { Badge } from "../components/ui";
import { preciseMoney } from "../components/ExposureHeadroomBar";
import type { Request } from "../types";
import { date, requestReference } from "../utils/format";

export function PersonalRequestTable({
  requests,
  openRequest,
}: {
  requests: Request[];
  openRequest: (request: Request) => void;
}) {
  return (
    <table
      className="rm-personal-requests"
      role="table"
      aria-label="My credit requests"
    >
      <thead role="rowgroup">
        <tr role="row">
          <th scope="col" role="columnheader" className="rm-request-ref">
            Request
          </th>
          <th
            scope="col"
            role="columnheader"
            className="rm-request-company-name"
          >
            Counterparty
          </th>
          <th scope="col" role="columnheader" className="rm-request-amount">
            Amount
          </th>
          <th scope="col" role="columnheader" className="rm-request-status">
            Status
          </th>
          <th scope="col" role="columnheader" className="rm-request-created">
            Created
          </th>
          <th scope="col" role="columnheader" className="rm-request-open">
            <span className="sr-only">Action</span>
          </th>
        </tr>
      </thead>
      <tbody role="rowgroup">
        {requests.map((request) => {
          const reference = requestReference(request.id);
          return (
            <tr key={request.id} role="row">
              <td role="cell" className="rm-request-ref">
                <button
                  className="table-link"
                  onClick={() => openRequest(request)}
                >
                  {reference}
                </button>
              </td>
              <td role="cell" className="rm-request-company-name">
                <div className="company-brand">
                  <CompanyLogo name={request.counterpartyName} />
                  <span>{request.counterpartyName}</span>
                </div>
              </td>
              <td role="cell" className="numeric rm-request-amount">
                {preciseMoney(Number(request.amount))}
              </td>
              <td role="cell" className="rm-request-status">
                <Badge status={request.status} />
              </td>
              <td role="cell" className="rm-request-created">
                <time dateTime={request.createdAt}>
                  {date(request.createdAt)}
                </time>
              </td>
              <td role="cell" className="rm-request-open">
                <button
                  className="icon-button"
                  aria-label={"View details for " + reference}
                  onClick={() => openRequest(request)}
                >
                  <ChevronRight size={17} aria-hidden="true" />
                </button>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
