import { test, expect, type Page } from "@playwright/test";
const id = "a0b1c2d3-1234-4321-9876-a0b1c2d3e4f5";
function pending(now = Date.now()) {
  return {
    challengeId: id,
    email: "alex@example.test",
    expiresAt: new Date(now + 600_000).toISOString(),
    resendAfter: new Date(now + 60_000).toISOString(),
  };
}
async function begin(page: Page, value = pending()) {
  await page.route("**/api/financial-institutions/directory", (route) =>
    route.fulfill({ json: [{ id: 3, name: "Test Institution" }] }),
  );
  await page.route("**/api/auth/users/register", (route) =>
    route.fulfill({
      json: {
        id: 7,
        email: value.email,
        emailVerified: false,
        verification: value,
      },
    }),
  );
  await page.goto("/login");
  await page
    .locator(".auth-story")
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page.getByLabel("First name").fill("Alex");
  await page.getByLabel("Last name").fill("Morgan");
  await page.getByLabel("Work email").fill(value.email);
  await page.getByLabel("Password", { exact: true }).fill("Fixture-Password1!");
  await page
    .getByLabel("Financial institution", { exact: true })
    .selectOption("3");
  await page
    .getByRole("button", { name: "Create your account", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Check your inbox!" }),
  ).toBeVisible();
}

test("registration opens the six-digit screen, supports paste, and verifies without logging in", async ({
  page,
}) => {
  let submitted: unknown;
  await page.route("**/api/auth/users/verification-code/verify", (route) => {
    submitted = route.request().postDataJSON();
    return route.fulfill({ json: { message: "Verified" } });
  });
  await begin(page);
  await expect(
    page.getByRole("button", { name: "Verify Email", exact: true }),
  ).toBeDisabled();
  await page.getByLabel("Six-digit verification code").fill("012345");
  await expect(page.locator(".otp-digits")).toHaveText("012345");
  await page.getByRole("button", { name: "Verify Email", exact: true }).click();
  expect(submitted).toEqual({ challengeId: id, code: "012345" });
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
  await expect(page.locator(".success")).toContainText("Email verified");
  expect(
    await page.evaluate(() =>
      sessionStorage.getItem("limitguard.pending-verification"),
    ),
  ).toBeNull();
});

test("incorrect code stays on the screen and pending verification survives reload", async ({
  page,
}) => {
  await page.route("**/api/auth/users/verification-code/verify", (route) =>
    route.fulfill({
      status: 400,
      json: { message: "The code is incorrect or expired." },
    }),
  );
  await begin(page);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Check your inbox!" }),
  ).toBeVisible();
  await page.getByLabel("Six-digit verification code").fill("123456");
  await page.getByRole("button", { name: "Verify Email", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("incorrect or expired");
  await expect(
    page.getByRole("heading", { name: "Check your inbox!" }),
  ).toBeVisible();
});

test("resend waits for cooldown and sends only the challenge identifier", async ({
  page,
}) => {
  const now = Date.now();
  await page.clock.install({ time: now });
  let submitted: unknown;
  await page.route("**/api/auth/users/verification-code/resend", (route) => {
    submitted = route.request().postDataJSON();
    return route.fulfill({ json: pending(now + 61_000) });
  });
  await begin(page, pending(now));
  await expect(
    page.getByRole("button", { name: /Resend code in/ }),
  ).toBeDisabled();
  await page.clock.fastForward(61_000);
  await page.getByRole("button", { name: "Resend Code", exact: true }).click();
  expect(submitted).toEqual({ challengeId: id });
  await expect(
    page.getByText("A new code has been sent. Use the latest email."),
  ).toBeVisible();
});

test("change email updates only the pending challenge", async ({ page }) => {
  const now = Date.now();
  await page.clock.install({ time: now });
  let submitted: unknown;
  await page.route(
    "**/api/auth/users/verification-code/change-email",
    (route) => {
      submitted = route.request().postDataJSON();
      return route.fulfill({
        json: {
          ...pending(now + 61_000),
          challengeId: "b0b1c2d3-1234-4321-9876-a0b1c2d3e4f5",
          email: "corrected@example.test",
        },
      });
    },
  );
  await begin(page, pending(now));
  await page.clock.fastForward(61_000);
  await page.getByRole("button", { name: "Change email address" }).click();
  await page.getByLabel("New work email").fill("corrected@example.test");
  await page.getByRole("button", { name: "Send new code" }).click();
  expect(submitted).toEqual({
    challengeId: id,
    email: "corrected@example.test",
  });
  await expect(page.locator(".email-code-screen strong")).toHaveText(
    "corrected@example.test",
  );
});

for (const width of [320, 390, 768, 1440]) {
  test(`verification screen fits ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await begin(page);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await expect(page.getByLabel("Six-digit verification code")).toBeVisible();
  });
}

test("returning unverified users reopen code screen after password validation", async ({
  page,
}) => {
  await page.route("**/api/auth/users/login", (route) =>
    route.fulfill({
      status: 403,
      json: { message: "Please verify your email" },
    }),
  );
  await page.route("**/api/auth/users/verification-code/request", (route) =>
    route.fulfill({ json: pending() }),
  );
  await page.goto("/login");
  await page.getByLabel("Work email").fill("alex@example.test");
  await page.getByLabel("Password", { exact: true }).fill("Fixture-Password1!");
  await page.getByRole("button", { name: "Sign in securely" }).click();
  await expect(
    page.getByRole("heading", { name: "Check your inbox!" }),
  ).toBeVisible();
});
