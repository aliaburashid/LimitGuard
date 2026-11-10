import { useEffect, useRef, useState } from "react";
import { ArrowLeft, ArrowRight, Check } from "lucide-react";
import { api } from "../api/client";
import {
  facilityDirectory,
  groupPortfolio,
  memberships,
} from "../api/portfolio";
import type { Facility } from "../analytics/metrics";
import type {
  Counterparty,
  Exposure,
  GroupExposure,
  Membership,
  Request,
} from "../types";
import type { RelationshipPortfolio } from "../hooks/useRelationshipPortfolio";
import { Modal, ErrorBox, Loading } from "./ui";
import {
  ExposureHeadroomBar,
  facilityHeadroom,
  groupHeadroom,
  minorAmount,
  preciseMoney,
  remainingHeadroom,
} from "./ExposureHeadroomBar";
import { label, requestReference } from "../utils/format";

export interface RequestCompany {
  facilityId?: number;
  counterpartyId: number;
  name: string;
}

export function CreateRequest({
  close,
  done,
  initialCompany,
  portfolio,
}: {
  close: () => void;
  done: (message: string) => void;
  initialCompany?: RequestCompany;
  portfolio?: RelationshipPortfolio;
}) {
  const [fallback, setFallback] = useState<{
    facilities: Facility[];
    groups: GroupExposure[];
    members: Membership[];
  }>();
  const [selectedFacility, setSelectedFacility] = useState(
    initialCompany?.facilityId,
  );
  const [counterpartyId, setCounterpartyId] = useState(
    initialCompany?.counterpartyId,
  );
  const [query, setQuery] = useState("");
  const [amount, setAmount] = useState("");
  const [step, setStep] = useState(0);
  const [error, setError] = useState("");
  const [lookupError, setLookupError] = useState("");
  const [capacityError, setCapacityError] = useState("");
  const [capacityVersion, setCapacityVersion] = useState(0);
  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(false);
  const submitting = useRef(false);
  const checkController = useRef<AbortController | null>(null);
  const backgroundController = useRef<AbortController | null>(null);
  const stepHeading = useRef<HTMLHeadingElement>(null);
  const [reading, setReading] = useState<{
    exposure: Exposure;
    company: Counterparty;
    group?: GroupExposure;
  }>();

  useEffect(() => {
    const refresh = () => setCapacityVersion((version) => version + 1);
    window.addEventListener("workspace-refresh", refresh);
    return () => {
      window.removeEventListener("workspace-refresh", refresh);
      checkController.current?.abort();
    };
  }, []);
  useEffect(() => {
    if (step > 0) stepHeading.current?.focus();
  }, [step]);

  useEffect(() => {
    if (portfolio) return;
    const controller = new AbortController();
    setCapacityError("");
    void Promise.all([
      facilityDirectory(controller.signal),
      groupPortfolio(controller.signal),
      memberships(controller.signal),
    ])
      .then(([facilities, groups, members]) => {
        if (!controller.signal.aborted)
          setFallback({ facilities, groups, members });
      })
      .catch((error: Error) => {
        if (!controller.signal.aborted) setCapacityError(error.message);
      });
    return () => controller.abort();
  }, [capacityVersion, Boolean(portfolio)]);

  const facilities = portfolio ? portfolio.facilities : fallback?.facilities;
  const facilityChoices = (facilities ?? []).filter(
    (facility) => facility.counterpartyId === counterpartyId,
  );
  const facility =
    facilityChoices.length === 1
      ? facilityChoices[0]
      : facilityChoices.find(
          (choice) => choice.creditLimitId === selectedFacility,
        );
  const id = facility?.creditLimitId;
  const members = portfolio?.members ?? fallback?.members;
  const groups = portfolio?.groups ?? fallback?.groups;
  const member = members?.find(
    (item) => item.counterpartyId === counterpartyId,
  );
  const groupSummary = groups?.find(
    (item) => item.corporateGroupId === member?.corporateGroupId,
  );
  const companyOptions = new Map(
    (portfolio?.individualCounterparties ?? []).map((company) => [
      company.id,
      company.name,
    ]),
  );
  for (const item of facilities ?? [])
    companyOptions.set(item.counterpartyId, item.name);
  const companyName =
    initialCompany?.name ?? companyOptions.get(counterpartyId ?? 0);
  const options = [...companyOptions]
    .filter(
      ([companyId, name]) =>
        companyId === counterpartyId ||
        name.toLowerCase().includes(query.trim().toLowerCase()),
    )
    .sort((a, b) => a[1].localeCompare(b[1]));
  function facilityDescription(choice: Facility) {
    const index = facilityChoices.indexOf(choice) + 1;
    return `Individual credit facility${facilityChoices.length > 1 ? ` ${index}` : ""} · ${preciseMoney(Number(choice.limitAmount))} approved`;
  }

  async function readCapacity(signal: AbortSignal) {
    if (!id || !counterpartyId)
      throw new Error("Choose an eligible credit facility.");
    const [exposure, company, shared] = await Promise.all([
      api<Exposure>(`/credit-limits/${id}/exposure`, "GET", undefined, signal),
      api<Counterparty>(
        `/counterparties/${counterpartyId}`,
        "GET",
        undefined,
        signal,
      ),
      groupSummary
        ? api<
            Pick<
              Exposure,
              | "limitAmount"
              | "usedAmount"
              | "reservedAmount"
              | "availableHeadroom"
            >
          >(
            `/group-credit-limits/groups/${groupSummary.corporateGroupId}/exposure-breakdown`,
            "GET",
            undefined,
            signal,
          )
        : Promise.resolve(undefined),
    ]);
    if (
      exposure.creditLimitId !== id ||
      exposure.counterpartyId !== counterpartyId ||
      company.id !== counterpartyId
    )
      throw new Error(
        "The facility could not be verified for this counterparty.",
      );
    if (
      shared &&
      [
        shared.limitAmount,
        shared.usedAmount,
        shared.reservedAmount,
        shared.availableHeadroom,
      ].some(
        (value) =>
          value === null ||
          value === undefined ||
          !Number.isFinite(Number(value)),
      )
    ) {
      throw new Error("The shared exposure balances could not be verified.");
    }
    const group =
      groupSummary && shared
        ? {
            ...groupSummary,
            amount: Number(shared.limitAmount),
            headroom: Number(shared.availableHeadroom),
            exposure: Number(shared.usedAmount) + Number(shared.reservedAmount),
          }
        : undefined;
    return { exposure, company, group };
  }

  useEffect(() => {
    const controller = new AbortController();
    setLookupError("");
    if (!id) return;
    backgroundController.current = controller;
    void readCapacity(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setReading(value);
      })
      .catch((error: Error) => {
        if (!controller.signal.aborted) setLookupError(error.message);
      });
    return () => controller.abort();
  }, [
    id,
    counterpartyId,
    groupSummary?.corporateGroupId,
    groupSummary?.amount,
    capacityVersion,
  ]);

  const current =
    reading?.exposure.creditLimitId === id &&
    reading?.company.id === counterpartyId
      ? reading
      : undefined;
  const exposure = current?.exposure;
  const selectedCompany = current?.company;
  const group = current?.group;
  const proposed = Number(amount);
  const validAmount =
    /^(?:\d+(?:\.\d{1,2})?|\.\d{1,2})$/.test(amount) &&
    proposed > 0 &&
    Number.isSafeInteger(minorAmount(proposed));
  const restricted =
    selectedCompany?.status === "FROZEN" ||
    selectedCompany?.status === "CLOSED";
  const groupScopeReady = Boolean(members && groups);
  const missingSharedLimit =
    groupScopeReady && Boolean(member && !groupSummary);
  const canContinue =
    validAmount &&
    Boolean(exposure) &&
    groupScopeReady &&
    !missingSharedLimit &&
    !lookupError &&
    !restricted;
  const facilityRemaining = exposure
    ? remainingHeadroom(Number(exposure.availableHeadroom), proposed)
    : 0;
  const groupRemaining = group
    ? remainingHeadroom(Number(group.headroom), proposed)
    : null;
  const groupTighter = Boolean(
    group &&
    exposure &&
    Number(group.headroom) < Number(exposure.availableHeadroom),
  );
  const exceedsCapacity =
    facilityRemaining < 0 ||
    (proposed <= 500000 && groupRemaining !== null && groupRemaining < 0);
  const amountField = (
    <label className="field">
      <span>Requested amount (BHD)</span>
      <input
        type="text"
        inputMode="decimal"
        disabled={checking}
        maxLength={18}
        required
        value={amount}
        onChange={(event) => {
          setAmount(event.target.value);
          setError("");
        }}
        placeholder="0.00"
      />
    </label>
  );

  return (
    <Modal
      title="Create credit request"
      className="rm-create-request"
      onClose={() => {
        if (!busy) close();
      }}
    >
      <ol className="rm-request-steps" aria-label="Request steps">
        {["Request details", "Exposure check", "Review & submit"].map(
          (name, index) => (
            <li
              key={name}
              aria-current={step === index ? "step" : undefined}
              data-complete={index < step}
            >
              <span aria-hidden="true">
                {index < step ? <Check size={13} /> : index + 1}
              </span>
              {name}
            </li>
          ),
        )}
      </ol>
      <form
        onSubmit={async (event) => {
          event.preventDefault();
          if (busy || checking || submitting.current || !canContinue) return;
          if (step === 0) {
            backgroundController.current?.abort();
            const controller = new AbortController();
            checkController.current = controller;
            setChecking(true);
            setError("");
            try {
              const value = await readCapacity(controller.signal);
              if (!controller.signal.aborted) {
                setReading(value);
                setStep(1);
              }
            } catch (error) {
              if (!controller.signal.aborted)
                setError((error as Error).message);
            } finally {
              if (!controller.signal.aborted) setChecking(false);
            }
            return;
          }
          if (exceedsCapacity) return;
          if (step === 1) {
            setStep(2);
            return;
          }
          submitting.current = true;
          setBusy(true);
          setError("");
          try {
            // preserve the submitted decimal string for the existing BigDecimal request contract
            const request = await api<Request>("/credit-requests", "POST", {
              creditLimitId: id,
              amount,
            });
            done(`${requestReference(request.id)}: ${label(request.status)}.`);
            close();
          } catch (error) {
            const message = (error as Error).message;
            setError(
              /(?:\bID\s*:?\s*|#)\d+/i.test(message)
                ? "The request could not be submitted for this facility. Review the current balances and try again."
                : message,
            );
            window.dispatchEvent(new Event("workspace-refresh"));
          } finally {
            submitting.current = false;
            setBusy(false);
          }
        }}
      >
        {step === 0 && (
          <>
            {initialCompany ? (
              <div className="rm-request-company">
                <span>Counterparty</span>
                <strong>{companyName}</strong>
              </div>
            ) : (
              <div className="rm-request-company-picker">
                <label className="field">
                  <span>Find a counterparty</span>
                  <input
                    type="search"
                    disabled={checking}
                    placeholder="Search by company name"
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                  />
                </label>
                <label className="field">
                  <span>Counterparty</span>
                  <select
                    value={counterpartyId ?? ""}
                    disabled={checking}
                    onChange={(event) => {
                      setCounterpartyId(
                        Number(event.target.value) || undefined,
                      );
                      setSelectedFacility(undefined);
                      setError("");
                    }}
                  >
                    <option value="">Choose a counterparty</option>
                    {options.map(([value, name]) => (
                      <option value={value} key={value}>
                        {name}
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            )}
            {facilityChoices.length > 1 && (
              <label className="field">
                <span>Credit facility</span>
                <select
                  value={id ?? ""}
                  disabled={checking}
                  onChange={(event) => {
                    setSelectedFacility(
                      Number(event.target.value) || undefined,
                    );
                    setError("");
                  }}
                >
                  <option value="">Choose a credit facility</option>
                  {facilityChoices.map((choice) => (
                    <option
                      value={choice.creditLimitId}
                      key={choice.creditLimitId}
                    >
                      {facilityDescription(choice)} ·{" "}
                      {preciseMoney(Number(choice.availableHeadroom))} available
                    </option>
                  ))}
                </select>
              </label>
            )}
            {counterpartyId && facilities && !facilityChoices.length && (
              <p className="rm-request-note">
                No eligible individual credit facility is available for this
                counterparty.
              </p>
            )}
            {counterpartyId && !facilities && portfolio?.errors.length ? (
              <ErrorBox message="The institution’s facilities could not be loaded." />
            ) : (
              ((!facilities && !capacityError) ||
                (id && !exposure && !lookupError)) && <Loading />
            )}
            {exposure && (
              <dl className="rm-request-balances">
                <div>
                  <dt>Individual headroom</dt>
                  <dd>{preciseMoney(Number(exposure.availableHeadroom))}</dd>
                </div>
                {group && (
                  <div>
                    <dt>{group.corporateGroupName} · shared headroom</dt>
                    <dd>{preciseMoney(group.headroom)}</dd>
                  </div>
                )}
              </dl>
            )}
            {amountField}
          </>
        )}
        {step === 1 && (
          <>
            <h3 ref={stepHeading} tabIndex={-1}>
              Exposure check
            </h3>
            {amountField}
          </>
        )}
        {step === 2 && (
          <>
            <h3 ref={stepHeading} tabIndex={-1}>
              Review &amp; submit
            </h3>
            <dl className="rm-request-balances">
              <div>
                <dt>Counterparty</dt>
                <dd>{companyName}</dd>
              </div>
              {facility && (
                <div>
                  <dt>Facility</dt>
                  <dd>{facilityDescription(facility)}</dd>
                </div>
              )}
              <div>
                <dt>Requested amount</dt>
                <dd>{preciseMoney(proposed)}</dd>
              </div>
              {exposure && (
                <div>
                  <dt>Projected individual headroom</dt>
                  <dd>{preciseMoney(facilityRemaining)}</dd>
                </div>
              )}
              {groupRemaining !== null && (
                <div>
                  <dt>Projected shared headroom</dt>
                  <dd>{preciseMoney(groupRemaining)}</dd>
                </div>
              )}
            </dl>
          </>
        )}
        {lookupError && <ErrorBox message={lookupError} />}
        {restricted && (
          <div className="notice risk-warning">
            This counterparty is {label(selectedCompany.status).toLowerCase()}{" "}
            and cannot receive new credit requests.
          </div>
        )}
        {amount && !validAmount && (
          <p className="scope-note">
            Enter a positive amount with up to two decimal places.
          </p>
        )}
        {step > 0 && exposure && validAmount && (
          <div className="rm-request-projection">
            {step === 1 && (
              <>
                <ExposureHeadroomBar
                  title="Individual facility"
                  capacity={facilityHeadroom(exposure)}
                  proposed={proposed}
                />
                {group && (
                  <ExposureHeadroomBar
                    title={`Shared group · ${group.corporateGroupName}`}
                    capacity={groupHeadroom(group)}
                    proposed={proposed}
                  />
                )}
              </>
            )}
            {step === 1 && group && (
              <p className="scope-note">
                {groupTighter
                  ? "The shared group limit has the tighter headroom."
                  : "The individual facility has the tighter or equal headroom."}
              </p>
            )}
            {step === 1 &&
              proposed <= 500000 &&
              facilityRemaining >= 0 &&
              (groupRemaining === null || groupRemaining >= 0) && (
                <p className="rm-request-note">
                  The displayed balances cover this amount. Capacity is checked
                  again on submission.
                </p>
              )}
            {facilityRemaining < 0 && (
              <div className="notice risk-warning">
                The proposed amount exceeds individual headroom by{" "}
                {preciseMoney(-facilityRemaining)}.
              </div>
            )}
            {group && groupRemaining !== null && groupRemaining < 0 && (
              <div className="notice risk-warning">
                {facilityRemaining >= 0
                  ? `The company has sufficient individual headroom, but its corporate group has only ${preciseMoney(group.headroom)} available.`
                  : `The proposed amount also exceeds shared group headroom by ${preciseMoney(-groupRemaining)}.`}{" "}
                {proposed <= 500000
                  ? "The shared group limit prevents this reservation."
                  : "Approval remains subject to the shared group limit."}
              </div>
            )}
            {(proposed > 500000 || step === 2) && (
              <p className="scope-note">
                {proposed > 500000
                  ? "Requests above BD 500,000 enter Risk Officer review. Pending requests do not reserve capacity until approval."
                  : "LimitGuard checks current balances on submission."}
              </p>
            )}
          </div>
        )}
        {missingSharedLimit && (
          <p className="rm-request-note">
            No shared limit is configured for {member?.corporateGroupName} at
            your institution.
          </p>
        )}
        {id && !groupScopeReady && (
          <p className="rm-request-note">
            {capacityError || portfolio?.errors.length
              ? "Corporate group capacity could not be verified."
              : "Checking corporate group capacity…"}
          </p>
        )}
        {capacityError && <ErrorBox message={capacityError} />}
        {error && <ErrorBox message={error} />}
        <div className="modal-actions">
          <button type="button" disabled={busy} onClick={close}>
            Cancel
          </button>
          {step > 0 && (
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                setError("");
                setStep(step - 1);
              }}
            >
              <ArrowLeft size={15} />
              Back
            </button>
          )}
          <button
            className="primary"
            disabled={
              busy || checking || !canContinue || (step > 0 && exceedsCapacity)
            }
          >
            {busy
              ? "Submitting…"
              : checking
                ? "Checking…"
                : step === 0
                  ? "Check exposure"
                  : step === 1
                    ? "Review request"
                    : "Confirm create credit request"}
            <ArrowRight size={16} />
          </button>
        </div>
      </form>
    </Modal>
  );
}
