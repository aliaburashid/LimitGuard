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
  large = false,
}: {
  name: string;
  large?: boolean;
}) {
  return (
    <span
      aria-hidden="true"
      className={`company-logo${large ? " company-logo-large" : ""}`}
    >
      {initials(name)}
    </span>
  );
}
