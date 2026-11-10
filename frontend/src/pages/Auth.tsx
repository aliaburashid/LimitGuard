import { ArrowRight, LockKeyhole, ShieldCheck } from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import "../styles/auth-transition.css";
import { api, ApiError } from "../api/client";
import { ErrorBox, Field } from "../components/ui";
import EmailCodeVerification, {
  readPendingVerification,
  savePendingVerification,
  type VerificationChallenge,
} from "../components/EmailCodeVerification";
import PasswordRecovery, {
  readPendingPasswordRecovery,
} from "../components/PasswordRecovery";
import WorkSignIn from "../components/WorkSignIn";
import InstitutionSelect from "../components/InstitutionSelect";
import { useAuth } from "../context/Auth";
export default function Auth() {
  const heading = useRef<HTMLHeadingElement>(null);
  const firstRender = useRef(true);
  const [challenge, setChallenge] = useState<VerificationChallenge | null>(
    readPendingVerification,
  );
  const params = new URLSearchParams(location.search);
  const [mode, setMode] = useState(
    location.pathname.includes("reset-password")
      ? "reset-password"
      : location.pathname.includes("verify-email")
        ? "verify-email"
        : readPendingPasswordRecovery()
          ? "forgot-password"
          : challenge
            ? "verify-code"
            : "login",
  );
  const [directoryReady, setDirectoryReady] = useState(false);
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
    if (busy || (mode === "register" && !directoryReady)) return;
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
        if (
          mode === "register" &&
          result &&
          typeof result === "object" &&
          "verification" in result &&
          result.verification
        ) {
          const pending = result.verification as VerificationChallenge;
          savePendingVerification(pending);
          setChallenge(pending);
          setMode("verify-code");
          setSuccess("");
          setDirectoryReady(false);
          form.reset();
          return;
        }
        setSuccess(
          typeof result === "string"
            ? result
            : "Account created. Check your email for your verification code.",
        );
        form.reset();
      }
    } catch (e) {
      // correct credentials can reopen the code screen for an unverified account
      if (mode === "login" && e instanceof ApiError && e.status === 403) {
        try {
          const pending = await api<VerificationChallenge>(
            "/auth/users/verification-code/request",
            "POST",
            { email: data.email, password: data.password },
          );
          savePendingVerification(pending);
          setChallenge(pending);
          setMode("verify-code");
          setError("");
          return;
        } catch {
          /* keeps the original sign-in error if pending verification cannot be resumed */
        }
      }
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    heading.current?.focus({ preventScroll: true });
  }, [mode]);
  function change(m: string) {
    if (busy) return;
    setDirectoryReady(false);
    setMode(m);
    setError("");
    setSuccess("");
  }
  return (
    <main
      className={`auth auth-sliding ${mode === "register" ? "is-register" : ""}`}
    >
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
          <div className="auth-panel-switch">
            <p>
              {mode === "register"
                ? "Already have an institutional account?"
                : "New to LimitGuard?"}
            </p>
            <button
              type="button"
              disabled={busy}
              onClick={() => change(mode === "register" ? "login" : "register")}
            >
              {mode === "register" ? "Sign In" : "Create an account"}
              <ArrowRight size={17} aria-hidden="true" />
            </button>
          </div>
        </div>
      </section>
      <section className="auth-form">
        <div className="auth-form-inner">
          {mode === "forgot-password" ? (
            <PasswordRecovery
              onBack={() => change("login")}
              onComplete={() => {
                change("login");
                setSuccess(
                  "Password reset successfully. Sign in with your new password.",
                );
              }}
            />
          ) : mode === "verify-code" && challenge ? (
            <EmailCodeVerification
              challenge={challenge}
              onUpdate={(value) => {
                setChallenge(value);
                savePendingVerification(value);
              }}
              onVerified={() => {
                savePendingVerification(null);
                setChallenge(null);
                change("login");
                setSuccess("Email verified. You can now sign in.");
              }}
              onBack={() => {
                savePendingVerification(null);
                setChallenge(null);
                change("login");
              }}
            />
          ) : (
            <>
              <span className="auth-lock">
                <LockKeyhole size={23} />
              </span>
              <span className="eyebrow">YOUR SECURE WORKSPACE</span>
              <h2 ref={heading} tabIndex={-1}>
                {titles[mode]}
              </h2>
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
                  <InstitutionSelect onAvailabilityChange={setDirectoryReady} />
                )}
                {["verify-email", "reset-password"].includes(mode) && (
                  <Field
                    name="token"
                    label="Email token"
                    defaultValue={params.get("token") || ""}
                  />
                )}{" "}
                {mode === "reset-password" && (
                  <Field
                    name="newPassword"
                    label="New password"
                    type="password"
                  />
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
                <button
                  className="primary auth-submit"
                  disabled={busy || (mode === "register" && !directoryReady)}
                >
                  {busy
                    ? "Please wait…"
                    : mode === "login"
                      ? "Sign in securely"
                      : titles[mode]}
                  <ArrowRight size={18} />
                </button>
              </form>
              {mode === "login" && (
                <WorkSignIn
                  busy={busy}
                  onBusyChange={setBusy}
                  onError={setError}
                />
              )}
              <div className="auth-links">
                {mode !== "login" && (
                  <button
                    className="text-button"
                    onClick={() => change("login")}
                  >
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
                  </>
                )}
              </div>
              <div className="auth-note">
                <ShieldCheck size={16} />
                Authorized institutional users only.
              </div>
            </>
          )}
        </div>
        <footer>Counterparty Credit Limit & Exposure Management</footer>
      </section>
    </main>
  );
}
