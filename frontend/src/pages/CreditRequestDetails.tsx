import { ArrowRight } from "lucide-react";
import {
  ExposureHeadroomBar,
  facilityHeadroom,
  preciseMoney,
} from "../components/ExposureHeadroomBar";
import { Badge, ErrorBox, Modal } from "../components/ui";
import type { Exposure, Request, Review } from "../types";
import { date, label, money, requestReference } from "../utils/format";

type RequestTransition = "approve" | "reject" | "use" | "cancel";

export function CreditRequestDetails({
  request,
  exposure,
  review,
  loading,
  error,
  canDecide,
  canManageReservation,
  onClose,
  transition,
}: {
  request: Request;
  exposure: Exposure | null;
  review: Review | null;
  loading: boolean;
  error: string;
  canDecide: boolean;
  canManageReservation: boolean;
  onClose: () => void;
  transition: (action: RequestTransition) => void;
}) {
  const reserved = request.status === "RESERVED";
  const expiryIsRelevant = reserved || request.status === "EXPIRED";
  const reference = requestReference(request.id);

  return (
    <Modal
      className="credit-request-details"
      title={
        <>
          Credit request{" "}
          <span className="credit-request-reference">{reference}</span>
        </>
      }
      onClose={onClose}
    >
      <div
        className="credit-request-body"
        role="region"
        aria-label="Request details"
        tabIndex={0}
      >
        <div className="credit-request-identity">
          <strong>{preciseMoney(Number(request.amount))}</strong>
          <Badge status={request.status} />
        </div>
        <p className="credit-request-company">{request.counterpartyName}</p>

        <section className="credit-request-section">
          <h3>Request information</h3>
          <dl className="credit-request-fields">
            <div>
              <dt>Request type</dt>
              <dd>Credit capacity</dd>
            </div>
            <div>
              <dt>Requested by</dt>
              <dd>{request.requesterName}</dd>
            </div>
            <div>
              <dt>Submitted</dt>
              <dd>
                <time dateTime={request.createdAt}>
                  {date(request.createdAt)}
                </time>
              </dd>
            </div>
            {expiryIsRelevant && (
              <div>
                <dt>
                  {reserved ? "Reservation expires" : "Reservation expiry"}
                </dt>
                <dd>
                  {request.expiresAt ? (
                    <time dateTime={request.expiresAt}>
                      {date(request.expiresAt)}
                    </time>
                  ) : (
                    "Unavailable"
                  )}
                </dd>
              </div>
            )}
          </dl>
          {reserved && (
            <ol
              className="credit-request-progression"
              aria-label="Request progression"
            >
              <li>Created</li>
              <li aria-current="step">
                <ArrowRight size={13} aria-hidden="true" />
                {label(request.status)}
              </li>
            </ol>
          )}
          <details className="credit-request-metadata">
            <summary>Additional details</summary>
            <dl className="credit-request-fields">
              <div>
                <dt>Facility</dt>
                <dd>Individual credit facility</dd>
              </div>
              <div>
                <dt>Institution</dt>
                <dd>{request.financialInstitutionName}</dd>
              </div>
              <div>
                <dt>Last updated</dt>
                <dd>
                  <time dateTime={request.updatedAt}>
                    {date(request.updatedAt)}
                  </time>
                </dd>
              </div>
              {request.counterpartyStatus !== "ACTIVE" && (
                <div>
                  <dt>Counterparty status</dt>
                  <dd>
                    <Badge status={request.counterpartyStatus} />
                  </dd>
                </div>
              )}
              {!expiryIsRelevant && request.expiresAt && (
                <div>
                  <dt>Reservation expiry</dt>
                  <dd>
                    <time dateTime={request.expiresAt}>
                      {date(request.expiresAt)}
                    </time>
                  </dd>
                </div>
              )}
            </dl>
          </details>
        </section>

        <section className="credit-request-section credit-request-exposure">
          {exposure && !loading && !error ? (
            <ExposureHeadroomBar
              embedded
              attention
              title="Facility exposure"
              capacity={facilityHeadroom(exposure)}
            />
          ) : (
            <>
              <h3>Facility exposure</h3>
              {loading ? (
                <p className="credit-request-note" role="status">
                  Loading facility exposure…
                </p>
              ) : (
                <ErrorBox
                  message={error || "Facility exposure is unavailable."}
                />
              )}
            </>
          )}
          {review && !loading && !error && (
            <p className="credit-request-note">
              If approved, committed exposure would be{" "}
              {money(
                Number(review.usedAmount) +
                  Number(review.reservedAmount) +
                  Number(review.requestedAmount),
              )}{" "}
              and remaining headroom would be{" "}
              {money(
                Number(review.availableHeadroom) -
                  Number(review.requestedAmount),
              )}
              . Approval is subject to capacity checks.
            </p>
          )}
        </section>
      </div>
      <div className="modal-actions credit-request-footer">
        {canDecide ? (
          <>
            <button
              className="danger"
              disabled={loading || Boolean(error)}
              onClick={() => transition("reject")}
            >
              Reject request
            </button>
            <button
              className="primary"
              disabled={loading || Boolean(error)}
              onClick={() => transition("approve")}
            >
              Approve & reserve
            </button>
          </>
        ) : canManageReservation ? (
          <>
            <button disabled={loading} onClick={() => transition("cancel")}>
              Cancel reservation
            </button>
            <button
              className="primary"
              disabled={loading || !request.expiresAt}
              onClick={() => transition("use")}
            >
              Mark as used
            </button>
          </>
        ) : (
          <button onClick={onClose}>Close</button>
        )}
      </div>
    </Modal>
  );
}
