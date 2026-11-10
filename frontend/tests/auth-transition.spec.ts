import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/financial-institutions/directory", (route) =>
    route.fulfill({ json: [{ id: 3, name: "Test Institution" }] }),
  );
});

test("navy panel slides between existing login and registration forms", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "no-preference" });
  await page.goto("/login");
  const story = page.locator(".auth-story");
  const initial = await story.boundingBox();
  await story
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Create your account" }),
  ).toBeFocused();
  await expect(page.locator(".auth")).toHaveClass(/is-register/);
  await expect
    .poll(async () => Math.round((await story.boundingBox())!.x))
    .toBe(720);
  expect(initial!.x).toBe(0);
  const form = page.locator(".auth-form");
  expect((await form.boundingBox())!.x).toBeCloseTo(0, 0);
  await expect(page.getByLabel("First name")).toBeVisible();
  await expect(
    page.getByLabel("Financial institution", { exact: true }),
  ).toBeVisible();
  await story.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeFocused();
  await expect
    .poll(async () => Math.round((await story.boundingBox())!.x))
    .toBe(0);
  await page.getByRole("button", { name: "Forgot password?" }).click();
  await expect(
    page.getByRole("heading", { name: "Recover your account" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Back to sign in" }).click();
  await expect(
    page.getByRole("button", { name: "Verify email", exact: true }),
  ).toHaveCount(0);
});

test("registration preserves the existing payload and server confirmation", async ({
  page,
}) => {
  let payload: unknown;
  await page.route("**/api/auth/users/register", async (route) => {
    payload = route.request().postDataJSON();
    await route.fulfill({ json: "Check your email to verify your account." });
  });
  await page.goto("/login");
  await page
    .locator(".auth-story")
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await page.getByLabel("First name").fill("Alex");
  await page.getByLabel("Last name").fill("Morgan");
  await page.getByLabel("Work email").fill("alex@example.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill("fixture-only-password");
  await page
    .getByLabel("Financial institution", { exact: true })
    .selectOption("3");
  await page
    .getByRole("button", { name: "Create your account", exact: true })
    .click();
  await expect(page.locator(".success")).toHaveText(
    "Check your email to verify your account.",
  );
  expect(payload).toEqual({
    firstName: "Alex",
    lastName: "Morgan",
    email: "alex@example.test",
    password: "fixture-only-password",
    financialInstitutionId: 3,
  });
});

for (const width of [320, 390, 768]) {
  test(`auth panels fit ${width}px with reduced motion`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/login");
    await page
      .locator(".auth-story")
      .getByRole("button", { name: "Create an account", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Create your account" }),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    expect(
      await page
        .locator(".auth-story")
        .evaluate((el) => getComputedStyle(el).transitionDuration),
    ).toBe("0s");
    expect((await page.locator(".auth-story").boundingBox())!.x).toBe(0);
    expect((await page.locator(".auth-form").boundingBox())!.x).toBe(0);
  });
}

test("institution directory supports failure, retry and selection", async ({
  page,
}) => {
  let unavailable = true;
  await page.route("**/api/financial-institutions/directory", (route) => {
    return unavailable
      ? route.fulfill({ status: 503, json: { message: "Unavailable" } })
      : route.fulfill({ json: [{ id: 3, name: "Test Institution" }] });
  });
  await page.goto("/login");
  await page
    .locator(".auth-story")
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await expect(page.locator("#institution-directory-status")).toHaveAttribute(
    "role",
    "alert",
  );
  await expect(
    page.getByRole("button", { name: "Create your account", exact: true }),
  ).toBeDisabled();
  unavailable = false;
  await page
    .getByRole("button", { name: "Retry institution directory" })
    .click();
  await page
    .getByLabel("Financial institution", { exact: true })
    .selectOption("3");
  await expect(
    page.getByLabel("Financial institution", { exact: true }),
  ).toHaveValue("3");
  await expect(
    page.getByRole("button", { name: "Create your account", exact: true }),
  ).toBeEnabled();
});

test("empty institution directory prevents registration", async ({ page }) => {
  await page.route("**/api/financial-institutions/directory", (route) =>
    route.fulfill({ json: [] }),
  );
  await page.goto("/login");
  await page
    .locator(".auth-story")
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await expect(
    page.getByText(
      "No active institutions are available. Contact your administrator.",
    ),
  ).toBeVisible();
  await expect(
    page.getByLabel("Financial institution", { exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Create your account", exact: true }),
  ).toBeDisabled();
});
