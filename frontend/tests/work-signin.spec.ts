import { test, expect } from "@playwright/test";

test("unconfigured providers stay disabled and email login remains available", async ({
  page,
}) => {
  await page.route("**/api/auth/sso/providers", (route) =>
    route.fulfill({ json: { google: false, microsoft: false } }),
  );
  await page.goto("/login");
  await expect(
    page.getByRole("button", { name: "Continue with Microsoft" }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Continue with Google" }),
  ).toBeDisabled();
  await expect(
    page.getByText("Work account sign-in is awaiting administrator setup."),
  ).toHaveCount(0);
  const emailButton = await page
    .getByRole("button", { name: "Sign in securely" })
    .boundingBox();
  const providerButtons = await page.locator(".work-signin").boundingBox();
  expect(providerButtons!.y).toBeGreaterThan(
    emailButton!.y + emailButton!.height,
  );
  await expect(page.getByLabel("Work email")).toBeEditable();
  await expect(
    page.getByRole("button", { name: "Sign in securely" }),
  ).toBeEnabled();
});

for (const provider of ["Google", "Microsoft"]) {
  test(`configured ${provider} opens its backend authorization flow`, async ({
    page,
  }) => {
    await page.route("**/api/auth/sso/providers", (route) =>
      route.fulfill({ json: { google: true, microsoft: true } }),
    );
    await page.route(
      `**/api/auth/sso/authorize/${provider.toLowerCase()}`,
      (route) =>
        route.fulfill({
          contentType: "text/html",
          body: "<h1>Provider authorization fixture</h1>",
        }),
    );
    await page.goto("/login");
    await page
      .getByRole("button", { name: `Continue with ${provider}` })
      .click();
    await expect(page).toHaveURL(
      new RegExp(`/api/auth/sso/authorize/${provider.toLowerCase()}$`),
    );
  });
}

test("failed callback clears the marker and keeps email login usable", async ({
  page,
}) => {
  await page.goto("/login#sso=error");
  await expect(page.getByRole("alert")).toContainText(
    "Work account sign-in could not be completed",
  );
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByLabel("Work email")).toBeEditable();
});

test("callback exchange runs once and failure leaves no login token in the URL", async ({
  page,
}) => {
  let exchanges = 0;
  await page.route("**/api/auth/sso/exchange", (route) => {
    exchanges++;
    return route.fulfill({
      status: 401,
      json: { message: "No completed login" },
    });
  });
  await page.goto("/login#sso=complete");
  await expect(page.getByRole("alert")).toContainText(
    "Work account sign-in could not be completed",
  );
  expect(exchanges).toBe(1);
  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("button", { name: "Sign in securely" }),
  ).toBeEnabled();
});

test("provider buttons appear only on login", async ({ page }) => {
  await page.goto("/login");
  await expect(
    page.getByRole("button", { name: "Continue with Google" }),
  ).toBeVisible();
  await page
    .locator(".auth-story")
    .getByRole("button", { name: "Create an account", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Create your account" }),
  ).toBeVisible();
  await expect(page.locator(".work-signin")).toHaveCount(0);
  await page.getByRole("button", { name: "Back to sign in" }).click();
  await expect(
    page.getByRole("button", { name: "Continue with Microsoft" }),
  ).toBeVisible();
});
