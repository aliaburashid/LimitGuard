import { useState } from "react";
import { corporateGroupLogoDomain } from "../config/verifiedLogoDomains";
import "../styles/company-logo.css";

function initials(name: string) {
  const words =
    name.replace(/\s*\([^)]*\)\s*$/u, "").match(/[\p{L}\p{N}]+/gu) ?? [];
  if (
    words.length < 2 ||
    /^(inc|llc|ltd|ag|plc|se|corporation)$/i.test(words[1])
  )
    return Array.from(words[0] ?? "•")
      .slice(0, 2)
      .join("")
      .toUpperCase();
  return words
    .slice(0, 2)
    .map((word) => Array.from(word)[0])
    .join("")
    .toUpperCase();
}

export function CompanyLogo({
  name,
  domain,
  size,
  variant,
  large = false,
}: {
  name: string;
  domain?: string;
  variant?: "logo";
  size?: "small" | "large";
  large?: boolean;
}) {
  const [failedSrc, setFailedSrc] = useState<string>();
  const clientId = import.meta.env.VITE_BRANDFETCH_CLIENT_ID?.trim();
  const isLarge = size ? size === "large" : large;
  const pixels = isLarge ? 88 : 68;
  const verifiedHost = domain?.trim().toLowerCase();
  const validHost =
    verifiedHost &&
    /^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}$/.test(verifiedHost);
  const src =
    clientId && validHost
      ? `https://cdn.brandfetch.io/domain/${verifiedHost}/w/${pixels}/h/${pixels}/fallback/404${variant ? `/${variant}` : ""}?c=${encodeURIComponent(clientId)}`
      : undefined;

  return (
    <span
      aria-hidden="true"
      className={`company-logo${isLarge ? " company-logo-large" : ""}`}
    >
      {src && failedSrc !== src ? (
        <img
          src={src}
          alt=""
          decoding="async"
          onError={() => setFailedSrc(src)}
        />
      ) : (
        initials(name)
      )}
    </span>
  );
}

export function CorporateGroupLogo({ name }: { name: string }) {
  // Brandfetch currently returns the airline mark for the Group's domain.
  const domain =
    name === "Lufthansa Group" ? undefined : corporateGroupLogoDomain(name);
  return (
    <CompanyLogo
      name={name}
      domain={domain}
      variant={name === "The Walt Disney Company" ? "logo" : undefined}
    />
  );
}
