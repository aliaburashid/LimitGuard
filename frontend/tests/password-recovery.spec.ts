import { test, expect, type Page } from "@playwright/test";

const challengeId = "a0b1c2d3-1234-4321-9876-a0b1c2d3e4f5";
function challenge(now = Date.now()) {
  return {
    challengeId,
    email: "alex@example.test",
    expiresAt: new Date(now + 600_000).toISOString(),
    resendAfter: new Date(now + 60_000).toISOString(),
  };
}
async function requestCode(page: Page, now = Date.now()) {
  await page.route("**/api/auth/users/password-reset-code/request", (route) =>
    route.fulfill({ json: challenge(now) }),
  );
  await page.goto("/login");
  await page.getByRole("button", { name: "Forgot password?" }).click();
  await page
    .getByLabel("Work email", { exact: true })
    .fill("alex@example.test");
  await page.getByRole("button", { name: "Send reset code" }).click();
  await expect(
    page.getByRole("heading", { name: "Check your inbox!" }),
  ).toBeVisible();
}

test("forgot password asks for OTP before showing the new-password form", async ({
  page,
}) => {
  let verificationRequest: unknown;
  let resetRequest: unknown;
  await page.route("**/api/auth/users/password-reset-code/verify", (route) => {
    verificationRequest = route.request().postDataJSON();
    return route.fulfill({ json: { token: "fixture-only-reset-token" } });
  });
  await page.route("**/api/auth/users/reset-password", (route) => {
    resetRequest = route.request().postDataJSON();
    return route.fulfill({ json: "Password reset successfully" });
  });
  await requestCode(page);
  await expect(page.getByLabel("New password", { exact: true })).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Verify Code" }),
  ).toBeDisabled();
  await page.getByLabel("Six-digit password reset code").fill("012345");
  await page.getByRole("button", { name: "Verify Code" }).click();
  expect(verificationRequest).toEqual({ challengeId, code: "012345" });
  await expect(
    page.getByRole("heading", { name: "Set a new password" }),
  ).toBeVisible();
  await expect(page.getByLabel("Email token")).toHaveCount(0);
  expect(
    await page.evaluate(() =>
      sessionStorage.getItem("limitguard.pending-password-recovery"),
    ),
  ).not.toContain("fixture-only-reset-token");
  await page
    .getByLabel("New password", { exact: true })
    .fill("Updated-Password2!");
  await page
    .getByLabel("Confirm new password", { exact: true })
    .fill("Updated-Password2!");
  await page
    .getByRole("button", { name: "Reset password", exact: true })
    .click();
  expect(resetRequest).toEqual({
    token: "fixture-only-reset-token",
    newPassword: "Updated-Password2!",
  });
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
  await expect(page.locator(".success")).toContainText(
    "Password reset successfully",
  );
  expect(
    await page.evaluate(() =>
      sessionStorage.getItem("limitguard.pending-password-recovery"),
    ),
  ).toBeNull();
});

test("wrong OTP keeps the new-password form closed", async ({ page }) => {
  await page.route("**/api/auth/users/password-reset-code/verify", (route) =>
    route.fulfill({
      status: 400,
      json: { message: "The code is incorrect or expired." },
    }),
  );
  await requestCode(page);
  await page.getByLabel("Six-digit password reset code").fill("111111");
  await page.getByRole("button", { name: "Verify Code" }).click();
  await expect(page.getByRole("alert")).toContainText("incorrect or expired");
  await expect(page.getByLabel("New password", { exact: true })).toHaveCount(0);
});

test("resend observes cooldown and replaces the pending code", async ({
  page,
}) => {
  const now = Date.now();
  await page.clock.install({ time: now });
  let resendRequest: unknown;
  await page.route("**/api/auth/users/password-reset-code/resend", (route) => {
    resendRequest = route.request().postDataJSON();
    return route.fulfill({ json: challenge(now + 61_000) });
  });
  await requestCode(page, now);
  await page.getByLabel("Six-digit password reset code").fill("123456");
  await expect(
    page.getByRole("button", { name: /Resend code in/ }),
  ).toBeDisabled();
  await page.clock.fastForward(61_000);
  await page.getByRole("button", { name: "Resend Code", exact: true }).click();
  expect(resendRequest).toEqual({ challengeId });
  await expect(page.getByLabel("Six-digit password reset code")).toHaveValue(
    "",
  );
});

test("expired code cannot be submitted and recovery survives reload", async ({
  page,
}) => {
  const now = Date.now();
  await page.clock.install({ time: now });
  await requestCode(page, now);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Check your inbox!" }),
  ).toBeVisible();
  await page.clock.fastForward(601_000);
  await page.getByLabel("Six-digit password reset code").fill("123456");
  await expect(
    page.getByRole("button", { name: "Verify Code" }),
  ).toBeDisabled();
  await expect(
    page.getByText("Your code has expired. Request a new one below."),
  ).toBeVisible();
});

test("mismatched passwords show an error before submitting", async ({
  page,
}) => {
  let resetRequests = 0;
  await page.route("**/api/auth/users/password-reset-code/verify", (route) =>
    route.fulfill({ json: { token: "fixture-only-reset-token" } }),
  );
  await page.route("**/api/auth/users/reset-password", (route) => {
    resetRequests++;
    return route.fulfill({ json: "Updated" });
  });
  await requestCode(page);
  await page.getByLabel("Six-digit password reset code").fill("123456");
  await page.getByRole("button", { name: "Verify Code" }).click();
  await page
    .getByLabel("New password", { exact: true })
    .fill("Updated-Password2!");
  await page.getByLabel("Confirm new password").fill("Different-Password3!");
  await page
    .getByRole("button", { name: "Reset password", exact: true })
    .click();
  await expect(page.getByRole("alert")).toHaveText("Passwords do not match.");
  expect(resetRequests).toBe(0);
});

for (const width of [320, 390, 768, 1440]) {
  test(`password recovery fits ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await requestCode(page);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  });
}
