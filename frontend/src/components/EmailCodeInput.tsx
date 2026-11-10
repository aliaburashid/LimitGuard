import type { RefObject } from "react";

export default function EmailCodeInput({
  id,
  label,
  value,
  onChange,
  inputRef,
  disabled,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  inputRef: RefObject<HTMLInputElement | null>;
  disabled: boolean;
}) {
  return (
    <>
      <label className="otp-sr-only" htmlFor={id}>
        {label}
      </label>
      <div className="otp-entry" onClick={() => inputRef.current?.focus()}>
        <input
          id={id}
          ref={inputRef}
          className="otp-native"
          value={value}
          onChange={(event) =>
            onChange(event.target.value.replace(/[^0-9]/g, "").slice(0, 6))
          }
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]{6}"
          maxLength={6}
          required
          disabled={disabled}
          aria-describedby={`${id}-timing`}
        />
        <div className="otp-digits" aria-hidden="true">
          {Array.from({ length: 6 }, (_, index) => (
            <span
              key={index}
              className={index === Math.min(value.length, 5) ? "current" : ""}
            >
              {value[index] || ""}
            </span>
          ))}
        </div>
      </div>
    </>
  );
}
