import {
  ArrowRight,
  ChartNoAxesCombined,
  Check,
  LockKeyhole,
  ShieldCheck,
} from "lucide-react";
import { useState, type FormEvent } from "react";
import { api } from "../api/client";
import { ErrorBox, Field } from "../components/ui";
import { useAuth } from "../context/Auth";
export default function Auth() {
  const params = new URLSearchParams(location.search);
  const [mode, setMode] = useState(
    location.pathname.includes("reset-password")
      ? "reset-password"
      : location.pathname.includes("verify-email")
        ? "verify-email"
        : "login",
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const { login } = useAuth();
  const titles: Record<string, string> = {
    login: "Welcome back",
    register: "Create your account",
    "forgot-password": "Recover your account",
    "reset-password": "Set a new password",
    "verify-email": "Verify your email",
  };
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    setSuccess("");
    const form = e.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    try {
      if (mode === "login")
        await login(String(data.email), String(data.password));
      else {
        if (mode === "register")
          data.financialInstitutionId = Number(
            data.financialInstitutionId,
          ) as unknown as string;
        const result = await api<unknown>(
          `/auth/users/${mode}${mode === "verify-email" ? `?token=${encodeURIComponent(String(data.token))}` : ""}`,
          mode === "verify-email" ? "GET" : "POST",
          mode === "verify-email" ? undefined : data,
        );
        setSuccess(
          typeof result === "string"
            ? result
            : "Account created. Check your email for your verification link.",
        );
        form.reset();
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function change(m: string) {
    setMode(m);
    setError("");
    setSuccess("");
  }
  return (
    <main className="auth">
      <section className="auth-story">
        <a className="brand" href="/">
          <ShieldCheck />
          <span>
            LimitGuard<small>CREDIT RISK & CONTROL</small>
          </span>
        </a>
        <div className="story-body">
          <span className="eyebrow">INSTITUTIONAL CREDIT MANAGEMENT</span>
          <h1>
            Confidence in
            <br />
            every credit
            <br />
            <em>decision.</em>
          </h1>
          <p>
            A clear view of counterparties, exposure and available capacity. One
            controlled workspace for your credit operations.
          </p>
          <div className="story-line" />
          <div className="story-features">
            <span>
              <ChartNoAxesCombined size={19} />
              Exposure visibility
            </span>
            <span>
              <ShieldCheck size={19} />
              Role-based control
            </span>
            <span>
              <Check size={19} />
              Auditable decisions
            </span>
          </div>
        </div>
        <footer>
          LIMITGUARD <span>Precision. Visibility. Control.</span>
        </footer>
      </section>
      <section className="auth-form">
        <div className="auth-form-inner">
          <span className="auth-lock">
            <LockKeyhole size={23} />
          </span>
          <span className="eyebrow">YOUR SECURE WORKSPACE</span>
          <h2>{titles[mode]}</h2>
          <p>
            {mode === "login"
              ? "Sign in with your institutional account to continue."
              : "Use your registered account information to continue."}
          </p>
          {error && <ErrorBox message={error} />}{" "}
          {success && (
            <div className="success" role="status">
              {success}
            </div>
          )}
          <form onSubmit={submit} key={mode}>
            {mode === "register" && (
              <div className="form-grid">
                <Field name="firstName" label="First name" />
                <Field name="lastName" label="Last name" />
              </div>
            )}
            {["login", "register", "forgot-password"].includes(mode) && (
              <Field name="email" label="Work email" type="email" />
            )}
            {["login", "register"].includes(mode) && (
              <Field name="password" label="Password" type="password" />
            )}
            {mode === "register" && (
              <>
                <Field
                  name="financialInstitutionId"
                  label="Financial institution ID"
                  type="number"
                />
                <small>
                  Ask your administrator for your institution ID. An institution
                  directory is not available.
                </small>
              </>
            )}
            {["verify-email", "reset-password"].includes(mode) && (
              <Field
                name="token"
                label="Email token"
                defaultValue={params.get("token") || ""}
              />
            )}{" "}
            {mode === "reset-password" && (
              <Field name="newPassword" label="New password" type="password" />
            )}
            {mode === "login" && (
              <button
                type="button"
                className="text-button forgot"
                onClick={() => change("forgot-password")}
              >
                Forgot password?
              </button>
            )}
            <button className="primary auth-submit" disabled={busy}>
              {busy
                ? "Please wait…"
                : mode === "login"
                  ? "Sign in securely"
                  : titles[mode]}
              <ArrowRight size={18} />
            </button>
          </form>
          <div className="auth-links">
            {mode !== "login" && (
              <button className="text-button" onClick={() => change("login")}>
                Back to sign in
              </button>
            )}
            {mode === "login" && (
              <>
                <span>New to LimitGuard?</span>
                <button
                  className="text-button"
                  onClick={() => change("register")}
                >
                  Create an account
                </button>
                <button
                  className="text-button"
                  onClick={() => change("verify-email")}
                >
                  Verify email
                </button>
              </>
            )}
          </div>
          <div className="auth-note">
            <ShieldCheck size={16} />
            Authorized institutional users only.
          </div>
        </div>
        <footer>Counterparty Credit Limit & Exposure Management</footer>
      </section>
    </main>
  );
}
