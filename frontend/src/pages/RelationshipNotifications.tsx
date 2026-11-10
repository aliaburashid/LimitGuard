import { Bell, CheckCheck, Clock3 } from "lucide-react";
import { useState } from "react";
import { Empty } from "../components/ui";
import type { Notice } from "../components/Notifications";

export function RelationshipNotifications({
  notices,
  read,
  markRead,
}: {
  notices: Notice[];
  read: Set<number>;
  markRead: (ids: number[]) => void;
}) {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const unread = notices.filter((notice) => !read.has(notice.id));
  const visible = unreadOnly ? unread : notices;
  return (
    <div className="rm-page">
      <div className="page-heading">
        <div>
          <span className="eyebrow">LIVE ACTIVITY</span>
          <h1>Notifications</h1>
          <p>Credit and workspace updates received in this session.</p>
        </div>
        <button
          className="primary"
          disabled={!unread.length}
          onClick={() => markRead(unread.map((notice) => notice.id))}
        >
          <CheckCheck size={17} />
          Mark all as read
        </button>
      </div>
      <section className="panel">
        <div className="section-head">
          <h2>Activity inbox</h2>
          <span className="rm-scope-pill">{unread.length} unread</span>
        </div>
        <div className="rm-inbox-filter" aria-label="Notification filters">
          <button
            aria-pressed={!unreadOnly}
            onClick={() => setUnreadOnly(false)}
          >
            All activity
          </button>
          <button aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)}>
            Unread
          </button>
        </div>
        {visible.length ? (
          <div className="rm-inbox">
            {visible.map((notice) => (
              <article
                key={notice.id}
                className={read.has(notice.id) ? "" : "rm-unread"}
              >
                <span className={`rm-inbox-icon notice-${notice.tone}`}>
                  <Bell size={17} />
                </span>
                <div>
                  <p>{notice.message}</p>
                  <small>
                    <Clock3 size={12} />
                    {notice.time}
                  </small>
                </div>
                {!read.has(notice.id) && (
                  <button
                    className="text-button"
                    onClick={() => markRead([notice.id])}
                  >
                    Mark as read
                  </button>
                )}
              </article>
            ))}
          </div>
        ) : (
          <Empty
            title={unreadOnly ? "You’re caught up" : "No notifications yet"}
          >
            New credit and exposure updates will appear here.
          </Empty>
        )}
      </section>
    </div>
  );
}
