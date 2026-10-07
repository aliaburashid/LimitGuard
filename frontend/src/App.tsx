import { Avatar } from "./components/Avatar";
import { useEffect, useState } from "react";
import {
  LayoutDashboard,
  Building2,
  ChartNoAxesCombined,
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
  X,
  CheckCircle2,
} from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useAuth } from "./context/Auth";
import Auth from "./pages/Auth";
import {
  Dashboard,
  Counterparties,
  ExposurePage,
  Requests,
  AuditPage,
  AdminPage,
  ProfilePage,
  MutationDialog,
  type Mutation,
} from "./pages/Workspace";
import { stream } from "./api/client";
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
    id: "exposure",
    name: "Limits & exposure",
    icon: ChartNoAxesCombined,
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
    id: "institutions",
    name: "Institutions",
    icon: Building2,
    roles: ["ADMIN"],
  },
  { id: "users", name: "User access", icon: Users, roles: ["ADMIN"] },
  { id: "audit", name: "Audit log", icon: ScrollText, roles: ["ADMIN"] },
];
export default function App() {
  const { user, logout, refresh } = useAuth();
  const [page, setPage] = useState("dashboard");
  const [collapsed, setCollapsed] = useState(false);
  const [mobile, setMobile] = useState(false);
  const [version, setVersion] = useState(0);
  const [action, setAction] = useState<Mutation | null>(null);
  const [toast, setToast] = useState("");
  const [live, setLive] = useState("Connecting");
  const reduced = useReducedMotion();
  function navigate(p: string) {
    setPage(p);
    setMobile(false);
  }
  function notify(message: string) {
    setToast(message);
  }
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 5000);
    return () => clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    if (!user) return;
    const controller = new AbortController();
    void stream(
      controller.signal,
      () => {
        setVersion((v) => v + 1);
        notify("Credit request updated. Your workspace has been refreshed.");
      },
      setLive,
    );
    return () => controller.abort();
  }, [user?.id]);
  useEffect(() => {
    setPage("dashboard");
    setAction(null);
    setToast("");
  }, [user?.id]);
  if (!user) return <Auth />;
  const allowed = nav.filter((n) => n.roles.includes(user.role));
  const valid = page === "profile" || allowed.some((n) => n.id === page);
  const active = valid ? page : "dashboard";
  return (
    <div className={`app ${collapsed ? "collapsed" : ""}`}>
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
          Institutional workspace
        </div>
        <span className="nav-caption">WORKSPACE</span>
        <nav aria-label="Main navigation">
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
          <div className="control-card">
            <ShieldCheck size={21} />
            <strong>Controlled by design</strong>
            <small>
              Role-based access.
              <br />
              Backend-validated decisions.
            </small>
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
        </div>
      </aside>
      <div className="app-body">
        <header className="topbar">
          <div>
            <button
              className="icon-button mobile-menu"
              onClick={() => setMobile(!mobile)}
              aria-label="Open navigation"
            >
              <Menu size={21} />
            </button>
            <span className="breadcrumb">
              Workspace <span>/</span>{" "}
              <strong>
                {active === "profile"
                  ? "Profile"
                  : allowed.find((n) => n.id === active)?.name}
              </strong>
            </span>
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
            <span className="divider" />
            <button
              className="header-profile"
              onClick={() => navigate("profile")}
            >
              <Avatar />
              <span>
                {user.firstName}
                <small>{label(user.role)}</small>
              </span>
            </button>
            <button
              className="icon-button"
              onClick={logout}
              aria-label="Sign out"
              title="Sign out"
            >
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main id="main" tabIndex={-1}>
          <AnimatePresence mode="wait">
            <motion.div
              key={active}
              initial={reduced ? false : { opacity: 0, y: 7 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduced ? undefined : { opacity: 0 }}
              transition={{ duration: 0.16 }}
            >
              {active === "dashboard" && (
                <Dashboard navigate={navigate} version={version} />
              )}{" "}
              {active === "counterparties" && (
                <Counterparties version={version} mutate={setAction} />
              )}{" "}
              {active === "exposure" && (
                <ExposurePage version={version} mutate={setAction} />
              )}{" "}
              {["requests", "approvals"].includes(active) && (
                <Requests
                  key={active}
                  approval={active === "approvals"}
                  version={version}
                  mutate={setAction}
                />
              )}{" "}
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
      <AnimatePresence>
        {toast && (
          <motion.div
            className="toast"
            role="status"
            initial={reduced ? false : { opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
          >
            <CheckCircle2 size={19} />
            {toast}
            <button
              className="icon-button"
              onClick={() => setToast("")}
              aria-label="Dismiss notification"
            >
              <X size={16} />
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
