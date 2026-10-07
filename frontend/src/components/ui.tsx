import { useEffect, useRef, type ReactNode } from "react";
import { X, Inbox, AlertCircle, ArrowRight } from "lucide-react";
import { label, money, utilization } from "../utils/format";
import type { Exposure } from "../types";
export function Badge({ status }: { status: string }) {
  return (
    <span className={`badge ${status.toLowerCase()}`}>
      <i />
      {label(status)}
    </span>
  );
}
export function Empty({
  title = "No records found",
  children,
}: {
  title?: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <Inbox size={30} />
      <h3>{title}</h3>
      <p>{children || "Records will appear here when they are available."}</p>
    </div>
  );
}
export function ErrorBox({ message }: { message: string }) {
  return (
    <div className="error" role="alert">
      <AlertCircle size={18} />
      {message}
    </div>
  );
}
export function Loading() {
  return (
    <div aria-label="Loading" aria-busy="true" className="skeletons">
      {[1, 2, 3, 4].map((n) => (
        <div className="skeleton" key={n} />
      ))}
    </div>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    const dialog = ref.current;
    dialog?.showModal();
    return () => {
      dialog?.close();
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      aria-labelledby="dialog-title"
    >
      <div className="modal-head">
        <h2 id="dialog-title">{title}</h2>
        <button
          className="icon-button"
          onClick={onClose}
          aria-label="Close dialog"
        >
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function ExposurePanel({ data }: { data: Exposure }) {
  const u = utilization(data);
  return (
    <section className="panel">
      <div className="section-head">
        <div>
          <span className="eyebrow">CREDIT LIMIT #{data.creditLimitId}</span>
          <h2>Exposure & headroom</h2>
        </div>
        <Badge status={u >= 100 ? "FULLY_UTILIZED" : "AVAILABLE"} />
      </div>
      <div className="exposure-values">
        {[
          ["Credit limit", data.limitAmount],
          ["Used exposure", data.usedAmount],
          ["Reserved exposure", data.reservedAmount],
          ["Available headroom", data.availableHeadroom],
        ].map(([l, v]) => (
          <div key={l}>
            <span>{l}</span>
            <strong>{money(Number(v))}</strong>
          </div>
        ))}
      </div>
      <div
        className="bar"
        role="img"
        aria-label={`${u.toFixed(1)} percent utilized, including reservations`}
      >
        <div
          className="used"
          style={{
            width: `${Math.min(100, (Number(data.usedAmount) / Number(data.limitAmount)) * 100)}%`,
          }}
        />
        <div
          className="reserved"
          style={{
            width: `${Math.min(100, (Number(data.reservedAmount) / Number(data.limitAmount)) * 100)}%`,
          }}
        />
      </div>
      <div className="legend">
        <span>
          <i className="used" />
          Used
        </span>
        <span>
          <i className="reserved" />
          Reserved
        </span>
        <span>
          <i className="available" />
          Available
        </span>
        <strong>{u.toFixed(1)}% utilized</strong>
      </div>
    </section>
  );
}
export function Pager({
  page,
  total,
  count,
  onChange,
}: {
  page: number;
  total: number;
  count: number;
  onChange: (n: number) => void;
}) {
  return (
    <div className="pager">
      <span>
        {count.toLocaleString()} records · Page {page + 1} of{" "}
        {Math.max(1, total)}
      </span>
      <div>
        <button disabled={!page} onClick={() => onChange(page - 1)}>
          Previous
        </button>
        <button disabled={page + 1 >= total} onClick={() => onChange(page + 1)}>
          Next <ArrowRight size={14} />
        </button>
      </div>
    </div>
  );
}
export function Field({
  name,
  label: caption,
  type = "text",
  value,
  required = true,
  options,
  defaultValue,
}: {
  name: string;
  label: string;
  type?: string;
  value?: string;
  defaultValue?: string | number;
  required?: boolean;
  options?: string[];
}) {
  return (
    <label className="field">
      <span>{caption}</span>
      {options ? (
        <select name={name} required={required} defaultValue={defaultValue}>
          {options.map((o) => (
            <option key={o} value={o}>
              {label(o)}
            </option>
          ))}
        </select>
      ) : (
        <input
          name={name}
          type={type}
          required={required}
          defaultValue={defaultValue}
          value={value}
          min={
            type === "number"
              ? name.endsWith("Id") || name === "id"
                ? 1
                : 0
              : undefined
          }
          step={
            type === "number"
              ? name.endsWith("Id") || name === "id"
                ? "1"
                : "any"
              : undefined
          }
          autoComplete={
            type === "password"
              ? name === "password" || name === "currentPassword"
                ? "current-password"
                : "new-password"
              : name === "email"
                ? "email"
                : "off"
          }
        />
      )}
    </label>
  );
}
