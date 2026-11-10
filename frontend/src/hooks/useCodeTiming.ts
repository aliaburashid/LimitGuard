import { useEffect, useState } from "react";

// shares the server-provided expiry and resend countdown across both code screens
export function useCodeTiming(
  challenge: { expiresAt: string; resendAfter: string } | null,
) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  return {
    cooldown: challenge
      ? Math.max(0, Math.ceil((Date.parse(challenge.resendAfter) - now) / 1000))
      : 0,
    expired: challenge ? now >= Date.parse(challenge.expiresAt) : false,
  };
}
