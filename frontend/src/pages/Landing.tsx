import {
  ArrowRight,
  ArrowUpRight,
  ChartNoAxesCombined,
  Check,
  CreditCard,
  Globe2,
  ShieldCheck,
  Users,
} from "lucide-react";
import "../styles/landing.css";

const features = [
  {
    icon: Users,
    title: "Corporate Groups",
    description:
      "Manage complex group structures and shared limits across markets.",
  },
  {
    icon: CreditCard,
    title: "Credit Requests",
    description:
      "Create, track and approve credit requests with full controls.",
  },
  {
    icon: ChartNoAxesCombined,
    title: "Risk Analytics",
    description: "Get AI-powered risk insights and ratings.",
  },
  {
    icon: ShieldCheck,
    title: "Secure Approvals",
    description: "Enforce maker-checker and auditable workflows.",
  },
  {
    icon: Globe2,
    title: "Global & Scalable",
    description:
      "Designed for international banks and multi-country operations.",
  },
];
export default function Landing() {
  return (
    <div className="landing">
      <a className="skip-link" href="#landing-main">
        Skip to main content
      </a>
      <header className="landing-header landing-container">
        <a className="landing-brand" href="/" aria-label="LimitGuard home">
          <ShieldCheck aria-hidden="true" />
          <span>LimitGuard</span>
        </a>
        <a className="landing-signin" href="/login">
          Sign In <ArrowRight size={16} aria-hidden="true" />
        </a>
      </header>
      <main id="landing-main" tabIndex={-1}>
        <section
          className="landing-hero landing-container"
          aria-labelledby="landing-title"
        >
          <div className="landing-hero-copy">
            <span className="landing-badge">
              <span /> Enterprise Credit Platform
            </span>
            <h1 id="landing-title">
              Control exposure.
              <br />
              Manage risk.
              <br />
              <span>Across your global portfolio.</span>
            </h1>
            <p className="landing-intro">
              A secure, scalable platform for corporate credit limits,
              connected-group exposure, risk assessment, and approvals — built
              for global banking.
            </p>
            <div className="landing-actions">
              <a className="landing-primary" href="/login">
                Get Started <ArrowRight size={18} aria-hidden="true" />
              </a>
              <a className="landing-secondary" href="#features">
                Learn More <ArrowUpRight size={17} aria-hidden="true" />
              </a>
            </div>
            <ul className="landing-trust">
              {[
                "Multi-entity & global groups",
                "Real-time exposure",
                "Secure & compliant",
              ].map((text) => (
                <li key={text}>
                  <Check size={15} aria-hidden="true" />
                  {text}
                </li>
              ))}
            </ul>
          </div>
          <div className="landing-globe">
            <div className="landing-globe-art">
              <img
                src="/images/global-network.webp"
                width="1024"
                height="1024"
                alt="Earth with connections between international financial hubs"
                fetchPriority="high"
              />
            </div>
          </div>
        </section>
        <section
          id="features"
          className="landing-features"
          aria-labelledby="features-title"
        >
          <div className="landing-container">
            <p className="landing-eyebrow">BUILT FOR GLOBAL BANKING</p>
            <h2 id="features-title">
              A unified solution for corporate credit and risk
            </h2>
            <div className="landing-feature-grid">
              {features.map(({ icon: Icon, title, description }) => (
                <article key={title}>
                  <span className="landing-feature-icon">
                    <Icon size={25} strokeWidth={1.6} aria-hidden="true" />
                  </span>
                  <h3>{title}</h3>
                  <p>{description}</p>
                </article>
              ))}
            </div>
          </div>
        </section>
      </main>
      <footer className="landing-footer landing-container">
        <a className="landing-brand" href="/">
          <ShieldCheck size={22} aria-hidden="true" />
          <span>LimitGuard</span>
        </a>
        <span>Corporate credit. Connected.</span>
        <span>© {new Date().getFullYear()} LimitGuard</span>
      </footer>
    </div>
  );
}
