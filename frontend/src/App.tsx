import { CompanyLogo } from "./components/CompanyLogo";
import { readWorkspaceState, saveWorkspaceState } from "./utils/workspaceState";
import { Avatar } from "./components/Avatar";
import { useEffect, useRef, useState, lazy, Suspense } from "react";
import {
  LayoutDashboard,
  BrainCircuit,
  Building2,
  FileText,
  ShieldCheck,
  Users,
  ScrollText,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  Menu,
  User,
  RefreshCw,
  Moon,
  Sun,
  Bell,
  Settings,
} from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useAuth } from "./context/Auth";
import Auth from "./pages/Auth";
import Landing from "./pages/Landing";
import {
  Dashboard,
  Counterparties,
  Requests,
  AuditPage,
  AdminPage,
  ProfilePage,
  MutationDialog,
  type Mutation,
} from "./pages/Workspace";
import { Loading } from "./components/ui";
import { CreateRequest, type RequestCompany } from "./components/CreateRequest";
import { WorkspaceSearch } from "./components/WorkspaceSearch";
import { RelationshipOverview } from "./pages/RelationshipOverview";
import { RelationshipCounterparties } from "./pages/RelationshipCounterparties";
import { RelationshipNotifications } from "./pages/RelationshipNotifications";
import { useRelationshipPortfolio } from "./hooks/useRelationshipPortfolio";
import "./styles/relationship-workspace.css";
import "./styles/workspace-dark.css";
import "./styles/workspace-controls.css";
const workspaceThemeKey = "limitguard.workspace.theme";

const RiskAnalytics = lazy(() =>
  import("./pages/RiskAnalytics").then((module) => ({
    default: module.RiskAnalytics,
  })),
);
import { stream } from "./api/client";
import { Notifications, type Notice } from "./components/Notifications";
import { notification, type Tone } from "./api/events";
import { label } from "./utils/format";
const nav = [
  {
    id: "dashboard",
    name: "Overview",
    icon: LayoutDashboard,
    roles: ["ADMIN", "RISK_OFFICER", "RELATIONSHIP_MANAGER"],
  },
  {
    id: "counterparties",
    name: "Counterparties",
    icon: Building2,
    roles: ["ADMIN", "RISK_OFFICER", "RELATIONSHIP_MANAGER"],
  },
  {
    id: "requests",
    name: "Credit requests",
    icon: FileText,
    roles: ["RELATIONSHIP_MANAGER"],
  },
  {
    id: "approvals",
    name: "Approval queue",
    icon: ShieldCheck,
    roles: ["RISK_OFFICER"],
  },
  {
    id: "analytics",
    name: "Risk Analytics",
    icon: BrainCircuit,
    roles: ["RISK_OFFICER"],
  },
  {
    id: "institutions",
    name: "Institutions",
    icon: Building2,
    roles: ["ADMIN"],
  },
  { id: "users", name: "User access", icon: Users, roles: ["ADMIN"] },
  {
    id: "notifications",
    name: "Notifications",
    icon: Bell,
    roles: ["RELATIONSHIP_MANAGER"],
  },
  {
    id: "profile",
    name: "Settings",
    icon: Settings,
    roles: ["RELATIONSHIP_MANAGER"],
  },
  { id: "audit", name: "Audit log", icon: ScrollText, roles: ["ADMIN"] },
];
function counterpartyEntry() {
  const url = new URL(window.location.href);
  const legacyPath = url.pathname.match(
    /^\/(?:exposure|limits-and-exposure|limits-exposure)(?:\/(\d+))?\/?$/,
  );
  const legacy =
    Boolean(legacyPath) || url.searchParams.get("page") === "exposure";
  const directory = /^\/counterparties\/?$/.test(url.pathname);
  if (!legacy && !directory) return { page: "dashboard", selection: null };

  const params = url.searchParams;
  const requestedFacility = Number(
    params.get("facilityId") ??
      params.get("creditLimitId") ??
      params.get("limitId") ??
      params.get("id") ??
      legacyPath?.[1],
  );
  const companyId = Number(params.get("counterpartyId"));
  const hasCompany = Number.isSafeInteger(companyId) && companyId > 0;
  const hasFacility =
    Number.isSafeInteger(requestedFacility) && requestedFacility > 0;
  const facility =
    hasFacility ||
    ((legacy || params.get("tab") === "facilities") && !hasCompany);
  const selection = facility
    ? hasFacility
      ? { id: requestedFacility }
      : null
    : hasCompany
      ? { id: companyId }
      : null;
  if (legacy) {
    url.pathname = "/counterparties";
    params.set("tab", facility ? "facilities" : "individual");
    if (hasFacility) params.set("facilityId", String(requestedFacility));
    if (params.get("page") === "exposure") params.delete("page");
  }
  return {
    page: facility ? "facilities" : "counterparties",
    selection,
    redirect: legacy ? url.pathname + url.search + url.hash : undefined,
  };
}

type SavedWorkspace = {
  url: string;
  page: string;
  selection: { id: number } | null;
  requestStatus: string;
  requestCompany: { id: number; name: string } | null;
  assessmentId: number | null;
};

export default function App() {
  const { user, restoring } = useAuth();
  if (restoring)
    return (
      <main className="workspace-session-loading" role="status">
        Restoring your workspace…
      </main>
    );
  return <WorkspaceApp key={user?.id ?? "signed-out"} />;
}
function WorkspaceApp() {
  const { user, logout, refresh } = useAuth();
  const relationshipManager = user?.role === "RELATIONSHIP_MANAGER";
  const [entry] = useState(counterpartyEntry);
  const workspaceKey = user
    ? user.id + ":" + user.financialInstitution.id
    : null;
  const [saved] = useState(() => {
    const state =
      workspaceKey && !entry.redirect
        ? readWorkspaceState<SavedWorkspace>(workspaceKey)
        : null;
    return state?.url === window.location.href ? state : null;
  });
  const [selection, setSelection] = useState(
    saved ? saved.selection : entry.selection,
  );
  const [requestDraft, setRequestDraft] = useState<
    RequestCompany | null | undefined
  >();
  const [readNotices, setReadNotices] = useState(new Set<number>());
  const [restoreCounterparties, setRestoreCounterparties] = useState(
    Boolean(saved),
  );
  const [page, setPage] = useState(saved?.page ?? entry.page);
  const [requestStatus, setRequestStatus] = useState(
    saved?.requestStatus ?? "",
  );
  const [requestCompany, setRequestCompany] = useState<{
    id: number;
    name: string;
  } | null>(saved?.requestCompany ?? null);
  const [assessmentId, setAssessmentId] = useState<number | null>(
    saved?.assessmentId ?? null,
  );
  const [collapsed, setCollapsed] = useState(false);
  const [mobile, setMobile] = useState(false);
  const [version, setVersion] = useState(0);
  const [darkMode, setDarkMode] = useState(() => {
    try {
      return localStorage.getItem(workspaceThemeKey) === "dark";
    } catch {
      return false;
    }
  });
  const [action, setAction] = useState<Mutation | null>(null);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [toasts, setToasts] = useState<Notice[]>([]);
  const nextNotice = useRef(0);
  const [live, setLive] = useState("Connecting");
  const reduced = useReducedMotion();
  const portfolio = useRelationshipPortfolio(version, relationshipManager);
  function toggleDarkMode() {
    const next = !darkMode;
    setDarkMode(next);
    try {
      localStorage.setItem(workspaceThemeKey, next ? "dark" : "light");
    } catch {
      // The workspace can still switch themes when browser storage is unavailable.
    }
  }
  function assess(id: number) {
    setAssessmentId(id);
    navigate("analytics");
  }
  function navigate(p: string, recordId?: number, status = "") {
    setRestoreCounterparties(false);
    setRequestStatus(status);
    setRequestCompany(null);
    setSelection(recordId === undefined ? null : { id: recordId });
    setPage(p);
    setMobile(false);
  }
  function notify(message: string, tone: Tone = "success") {
    const notice = {
      id: ++nextNotice.current,
      message,
      tone,
      time: new Date().toLocaleTimeString("en-GB"),
    };
    setNotices((previous) => [notice, ...previous].slice(0, 40));
    setToasts((previous) => [...previous, notice].slice(-5));
  }
  function dismiss(id: number) {
    setToasts((previous) => previous.filter((n) => n.id !== id));
  }
  useEffect(() => {
    if (!user) return;
    const controller = new AbortController();
    const seen = new Map<string, number>();
    let refreshTimer: ReturnType<typeof setTimeout>;
    void stream(
      controller.signal,
      (event) => {
        if (controller.signal.aborted) return;
        const key = JSON.stringify(event);
        const now = Date.now();
        if (now - (seen.get(key) ?? 0) < 1500) return;
        seen.set(key, now);
        for (const [k, time] of seen) if (now - time > 10000) seen.delete(k);
        const notice = notification(event);
        notify(notice.message, notice.tone);
        clearTimeout(refreshTimer);
        // Coalesce notifications when several requests change in one operation.
        refreshTimer = setTimeout(
          () => window.dispatchEvent(new Event("workspace-refresh")),
          250,
        );
      },
      (state) => {
        if (!controller.signal.aborted) {
          setLive(state);
          if (state === "Live")
            window.dispatchEvent(new Event("workspace-refresh"));
        }
      },
    );
    const reconcile = setInterval(() => {
      if (!document.hidden)
        window.dispatchEvent(new Event("workspace-refresh"));
    }, 30000);
    return () => {
      controller.abort();
      clearTimeout(refreshTimer);
      clearInterval(reconcile);
    };
  }, [user?.id]);
  useEffect(() => {
    const refreshWorkspace = () => setVersion((v) => v + 1);
    window.addEventListener("workspace-refresh", refreshWorkspace);
    return () =>
      window.removeEventListener("workspace-refresh", refreshWorkspace);
  }, []);
  useEffect(() => {
    if (user && entry.redirect)
      history.replaceState(history.state, "", entry.redirect);
  }, [user?.id, entry.redirect]);
  useEffect(() => {
    if (workspaceKey)
      saveWorkspaceState<SavedWorkspace>(workspaceKey, {
        url: window.location.href,
        page,
        selection,
        requestStatus,
        requestCompany,
        assessmentId,
      });
  }, [
    workspaceKey,
    page,
    selection,
    requestStatus,
    requestCompany,
    assessmentId,
  ]);
  if (!user)
    return location.pathname === "/" && !entry.redirect ? (
      <Landing />
    ) : (
      <Auth />
    );
  const allowed = nav.filter((n) => n.roles.includes(user.role));
  const valid = page === "profile" || allowed.some((n) => n.id === page);
  const groupDirectory = relationshipManager && page === "corporate-groups";
  const facilityDirectory = page === "facilities";
  const active =
    groupDirectory || facilityDirectory
      ? "counterparties"
      : valid
        ? page
        : "dashboard";
  return (
    <div
      className={`app ${collapsed ? "collapsed" : ""}${relationshipManager ? " rm-workspace" : ""}`}
      data-theme={darkMode ? "dark" : "light"}
    >
      <a href="#main" className="skip-link">
        Skip to main content
      </a>
      {mobile && (
        <button
          className="sidebar-overlay"
          aria-label="Close navigation"
          onClick={() => setMobile(false)}
        />
      )}
      <aside className={`sidebar ${mobile ? "mobile-open" : ""}`}>
        <a
          className="brand"
          href="#"
          onClick={(e) => {
            e.preventDefault();
            navigate("dashboard");
          }}
        >
          <ShieldCheck size={30} />
          <span>
            LimitGuard<small>CREDIT RISK & CONTROL</small>
          </span>
        </a>
        <div className="workspace-tag">
          <span className="tiny-dot" />
          {relationshipManager
            ? "Relationship Manager"
            : "Institutional workspace"}
        </div>
        <span className="nav-caption">WORKSPACE</span>
        <nav id="workspace-navigation" aria-label="Main navigation">
          {allowed.map((n) => (
            <button
              key={n.id}
              className={active === n.id ? "active" : ""}
              onClick={() => navigate(n.id)}
              title={collapsed ? n.name : undefined}
              aria-current={active === n.id ? "page" : undefined}
            >
              <n.icon size={19} />
              <span>{n.name}</span>
              {active === n.id && <i />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="control-card institution-identity">
            <CompanyLogo name={user.financialInstitution.name} />
            <div>
              <strong>{user.financialInstitution.name}</strong>
              <small>Financial institution</small>
            </div>
          </div>
          <button
            className="collapse-button"
            onClick={() => setCollapsed(!collapsed)}
          >
            {collapsed ? (
              <PanelLeftOpen size={18} />
            ) : (
              <PanelLeftClose size={18} />
            )}
            <span>Collapse navigation</span>
          </button>
          <button
            className="sidebar-profile"
            onClick={() => navigate("profile")}
          >
            <Avatar />
            <span>
              <strong>
                {user.firstName} {user.lastName}
              </strong>
              <small>{label(user.role)}</small>
            </span>
          </button>
          {relationshipManager && (
            <button
              className="sidebar-signout"
              aria-label="Sign out"
              title="Sign out"
              onClick={logout}
            >
              <LogOut size={17} />
              <span>Sign out</span>
            </button>
          )}
        </div>
      </aside>
      <div className="app-body">
        <header className="topbar">
          <div>
            <button
              className="icon-button mobile-menu"
              onClick={() => setMobile(!mobile)}
              aria-label="Open navigation"
              aria-expanded={mobile}
              aria-controls="workspace-navigation"
            >
              <Menu size={21} />
            </button>
            {relationshipManager ? (
              <WorkspaceSearch portfolio={portfolio} navigate={navigate} />
            ) : (
              <span className="breadcrumb">
                Workspace <span>/</span>{" "}
                <strong>
                  {active === "profile"
                    ? "Profile"
                    : allowed.find((n) => n.id === active)?.name}
                </strong>
              </span>
            )}
          </div>
          <div className="topbar-right">
            <span
              className={`live-state ${live === "Live" ? "connected" : ""}`}
            >
              <i />
              {live === "Live" ? "Live updates" : live}
            </span>
            <button
              className="icon-button"
              title="Refresh workspace"
              aria-label="Refresh workspace"
              onClick={() => setVersion((v) => v + 1)}
            >
              <RefreshCw size={17} />
            </button>
            <button
              type="button"
              className="icon-button workspace-theme-toggle"
              title={darkMode ? "Switch to light mode" : "Switch to dark mode"}
              aria-label="Dark mode"
              aria-pressed={darkMode}
              onClick={toggleDarkMode}
            >
              {darkMode ? (
                <Sun size={18} aria-hidden="true" />
              ) : (
                <Moon size={18} aria-hidden="true" />
              )}
            </button>
            <Notifications
              notices={notices}
              toasts={toasts}
              dismiss={dismiss}
              unreadCount={
                relationshipManager
                  ? notices.filter((notice) => !readNotices.has(notice.id))
                      .length
                  : undefined
              }
              openInbox={
                relationshipManager
                  ? () => navigate("notifications")
                  : undefined
              }
            />
            <span className="divider" />
            <button
              className="header-profile"
              aria-label={relationshipManager ? "Open profile" : undefined}
              onClick={() => navigate("profile")}
            >
              <Avatar />
              <span>
                {relationshipManager
                  ? `${user.firstName} ${user.lastName}`
                  : user.firstName}
                <small>{label(user.role)}</small>
              </span>
            </button>
            {!relationshipManager && (
              <button
                className="icon-button"
                onClick={logout}
                aria-label="Sign out"
                title="Sign out"
              >
                <LogOut size={18} />
              </button>
            )}
          </div>
        </header>
        <main id="main" tabIndex={-1}>
          {import.meta.env.DEV &&
            import.meta.env.VITE_CITI_PANEL_DEMO === "true" &&
            user.financialInstitution.id === 5 && (
              <p className="scope-note">
                Fictional demo data · Citi presentation
              </p>
            )}
          <AnimatePresence mode="wait">
            <motion.div
              key={active}
              initial={reduced ? false : { opacity: 0, y: 7 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduced ? undefined : { opacity: 0 }}
              transition={{ duration: 0.16 }}
            >
              {active === "dashboard" &&
                (relationshipManager ? (
                  <RelationshipOverview
                    portfolio={portfolio}
                    navigate={navigate}
                    version={version}
                    openPendingRequests={() =>
                      navigate("requests", undefined, "PENDING_APPROVAL")
                    }
                    createRequest={() => setRequestDraft(null)}
                  />
                ) : (
                  <Dashboard
                    navigate={navigate}
                    version={version}
                    assess={assess}
                  />
                ))}
              {active === "counterparties" &&
                (relationshipManager ? (
                  <RelationshipCounterparties
                    portfolio={portfolio}
                    restoreView={restoreCounterparties}
                    selection={
                      groupDirectory || facilityDirectory ? null : selection
                    }
                    groupView={groupDirectory}
                    groupSelection={groupDirectory ? selection : null}
                    createRequest={setRequestDraft}
                    facilitiesView={facilityDirectory}
                    facilitySelection={
                      facilityDirectory ? selection?.id : undefined
                    }
                    openRequest={(id) => navigate("requests", id)}
                    openCompanyRequests={({ id, name }) => {
                      navigate("requests");
                      setRequestCompany({ id, name });
                    }}
                  />
                ) : (
                  <Counterparties
                    version={version}
                    mutate={setAction}
                    facilitiesView={facilityDirectory}
                    facilitySelection={
                      facilityDirectory ? selection?.id : undefined
                    }
                    changeView={(facilities) =>
                      navigate(facilities ? "facilities" : "counterparties")
                    }
                  />
                ))}
              {["requests", "approvals"].includes(active) && (
                <Requests
                  key={`${active}-${requestStatus}`}
                  approval={active === "approvals"}
                  initialStatus={requestStatus}
                  companyFilter={relationshipManager ? requestCompany : null}
                  clearCompanyFilter={() => setRequestCompany(null)}
                  version={version}
                  mutate={setAction}
                  assess={assess}
                  portfolio={relationshipManager ? portfolio : undefined}
                  selection={relationshipManager ? selection : null}
                  createRequest={
                    relationshipManager
                      ? () => setRequestDraft(null)
                      : undefined
                  }
                />
              )}
              {relationshipManager && active === "notifications" && (
                <RelationshipNotifications
                  notices={notices}
                  read={readNotices}
                  markRead={(ids) =>
                    setReadNotices((previous) => new Set([...previous, ...ids]))
                  }
                />
              )}
              {active === "analytics" && (
                <Suspense fallback={<Loading />}>
                  <RiskAnalytics
                    requestId={assessmentId}
                    selectRequest={setAssessmentId}
                    version={version}
                    back={() => navigate("approvals")}
                    changed={() => setVersion((v) => v + 1)}
                  />
                </Suspense>
              )}
              {active === "audit" && <AuditPage version={version} />}{" "}
              {["institutions", "users"].includes(active) && (
                <AdminPage
                  institutions={active === "institutions"}
                  mutate={setAction}
                />
              )}{" "}
              {active === "profile" && (
                <ProfilePage mutate={setAction} notify={notify} />
              )}
            </motion.div>
          </AnimatePresence>
          <footer className="workspace-footer">
            <span>
              LimitGuard <span> / </span> Credit risk & control
            </span>
            <span>Institutional operations</span>
          </footer>
        </main>
      </div>
      {relationshipManager && requestDraft !== undefined && (
        <CreateRequest
          initialCompany={requestDraft ?? undefined}
          portfolio={portfolio}
          close={() => setRequestDraft(undefined)}
          done={(message) => {
            notify(message);
            window.dispatchEvent(new Event("workspace-refresh"));
          }}
        />
      )}
      {action && (
        <MutationDialog
          action={action}
          onClose={() => setAction(null)}
          onDone={() => {
            setVersion((v) => v + 1);
            notify("Operation completed successfully.");
            if (action.path === "/auth/users/profile")
              void refresh().catch(() =>
                notify(
                  "Profile saved. Sign in again if your session has changed.",
                ),
              );
          }}
        />
      )}
    </div>
  );
}
