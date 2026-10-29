import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Bell, CheckCircle2, AlertTriangle, Info, X } from "lucide-react";
import type { Tone } from "../api/events";
export interface Notice {
  id: number;
  message: string;
  tone: Tone;
  time: string;
}
function Toast({
  notice,
  dismiss,
}: {
  notice: Notice;
  dismiss: (id: number) => void;
}) {
  useEffect(() => {
    const timer = setTimeout(() => dismiss(notice.id), 8000);
    return () => clearTimeout(timer);
  }, [notice.id]);
  const Icon =
    notice.tone === "success"
      ? CheckCircle2
      : ["danger", "warning"].includes(notice.tone)
        ? AlertTriangle
        : Info;
  return (
    <div className={`toast toast-${notice.tone}`} role="status">
      <Icon size={21} />
      <span>{notice.message}</span>
      <button
        className="icon-button"
        aria-label="Dismiss notification"
        onClick={() => dismiss(notice.id)}
      >
        <X size={16} />
      </button>
    </div>
  );
}
export function Notifications({
  notices,
  toasts,
  dismiss,
}: {
  notices: Notice[];
  toasts: Notice[];
  dismiss: (id: number) => void;
}) {
  const [open, setOpen] = useState(false);
  const toastLayer = useRef<HTMLDivElement>(null);
  const [toastParent, setToastParent] = useState<HTMLElement>(
    () => document.body,
  );
  useEffect(() => {
    const update = () => {
      const target =
        document.querySelector<HTMLElement>("dialog[open]") ?? document.body;
      setToastParent((previous) => (previous === target ? previous : target));
    };
    const observer = new MutationObserver(update);
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["open"],
    });
    update();
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const layer = toastLayer.current;
    if (!layer?.isConnected || !layer.showPopover) return;
    // Native dialogs occupy the browser's top layer. Put incoming toasts there
    // as well so concurrent decisions remain visible during an open review.
    if (layer.matches(":popover-open")) layer.hidePopover();
    if (toasts.length) layer.showPopover();
    return () => {
      if (layer.matches(":popover-open")) layer.hidePopover();
    };
  }, [toasts, toastParent]);
  return (
    <>
      <div className="notification-control">
        <button
          className="icon-button"
          aria-label="Notification history"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <Bell size={19} />
          {notices.length > 0 && (
            <span className="notification-count">{notices.length}</span>
          )}
        </button>
        {open && (
          <section
            className="notification-history"
            aria-label="Recent notifications"
          >
            <h3>Live activity</h3>
            <p className="small">Received during this session</p>
            {!notices.length && <p className="muted">No notifications yet.</p>}
            {notices.map((n) => (
              <article key={n.id} className={`notice-${n.tone}`}>
                <p>{n.message}</p>
                <time className="small">{n.time}</time>
              </article>
            ))}
          </section>
        )}
      </div>
      {createPortal(
        <div
          ref={toastLayer}
          popover="manual"
          className="toast-stack"
          aria-label="Notifications"
        >
          {toasts.slice(-5).map((n) => (
            <Toast key={n.id} notice={n} dismiss={dismiss} />
          ))}
        </div>,
        toastParent,
      )}
    </>
  );
}
