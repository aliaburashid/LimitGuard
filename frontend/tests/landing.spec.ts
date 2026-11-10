import { test, expect } from "@playwright/test";

test("public welcome page links to existing authentication and real sections", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { level: 1 })).toContainText(
    "Control exposure.",
  );
  await expect(page.locator(".landing-feature-grid article")).toHaveCount(5);
  await expect(page.locator(".landing-globe img")).toHaveJSProperty(
    "naturalWidth",
    1024,
  );
  await page.getByRole("link", { name: "Learn More" }).click();
  await expect(page).toHaveURL(/#features$/);
  await expect(
    page
      .getByRole("banner")
      .getByRole("link", { name: "Features", exact: true }),
  ).toHaveCount(0);
  await expect(page.locator("#features")).toBeInViewport();
  await expect(page.locator("#about, #security, #contact")).toHaveCount(0);
  await page.getByRole("link", { name: "Get Started" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
  await page.goBack();
  await page.locator(".landing-signin").click();
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
});

for (const width of [320, 390, 768, 1440, 1920]) {
  test(`landing fits ${width}px viewport`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  });
}

for (const path of [
  "/reset-password?token=test-token",
  "/verify-email?token=test-token",
]) {
  test(`preserves authentication entry ${path}`, async ({ page }) => {
    await page.goto(path);
    await expect(
      page.getByRole("heading", {
        name: path.includes("reset-password")
          ? "Set a new password"
          : "Verify your email",
      }),
    ).toBeVisible();
  });
}
