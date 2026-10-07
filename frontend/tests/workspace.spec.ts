import { test, expect, type Page } from "@playwright/test";
const counterparty = {
  id: 12,
  name: "Institutional Trading Limited",
  status: "ACTIVE",
  createdAt: "2026-10-01T09:00:00",
  updatedAt: "2026-10-06T14:00:00",
};
const request = {
  id: 42,
  amount: 600000,
  status: "PENDING_APPROVAL",
  creditLimitId: 8,
  counterpartyId: 12,
  requesterId: 7,
  createdAt: "2026-10-06T10:00:00",
  updatedAt: "2026-10-06T10:00:00",
  expiresAt: null,
};
const exposure = {
  creditLimitId: 8,
  counterpartyId: 12,
  limitAmount: 2000000,
  usedAmount: 850000,
  reservedAmount: 400000,
  availableHeadroom: 750000,
};
const paged = (content: unknown[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
});
async function login(page: Page, role: string) {
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) {
      await route.continue();
      return;
    }
    let response: unknown = {};
    if (path.endsWith("/login")) response = { message: "fixture-only-token" };
    else if (path.endsWith("/profile"))
      response = {
        id: 1,
        firstName: "Alex",
        lastName: "Morgan",
        email: "alex@example.test",
        role,
        financialInstitution: { id: 3, name: "Test Institution" },
        profilePicturePath: null,
      };
    else if (path.endsWith("/counterparties")) response = paged([counterparty]);
    else if (path.endsWith("/pending") || path.endsWith("/my"))
      response = paged([request]);
    else if (path.endsWith("/exposure")) response = exposure;
    else if (path.endsWith("/review"))
      response = {
        ...exposure,
        creditRequestId: 42,
        requestedAmount: 600000,
        status: "PENDING_APPROVAL",
        counterpartyName: counterparty.name,
        requesterId: 7,
        requesterName: "Test Requester",
        createdAt: request.createdAt,
      };
    else if (path.endsWith("/events")) {
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: ": connected\n\n",
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(response),
    });
  });
  await page.goto("/");
  await page.getByLabel("Work email").fill("alex@example.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill("fixture-only-password");
  await page.getByRole("button", { name: "Sign in securely" }).click();
  await expect(
    page.getByRole("heading", { name: "Credit oversight" }),
  ).toBeVisible();
}
test("Risk Officer can review real contracts and confirm an approval", async ({
  page,
}) => {
  await login(page, "RISK_OFFICER");
  await expect(
    page.getByRole("button", { name: "User access", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Approval queue", exact: true })
    .click();
  await page.getByRole("button", { name: "Review", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Test Requester");
  await expect(page.getByRole("dialog")).toContainText("£750,000.00");
  await page.getByRole("button", { name: "Approve & reserve" }).click();
  await expect(page.getByRole("dialog")).toContainText(
    "Approval reserves the requested capacity",
  );
  const requestPromise = page.waitForRequest(
    (r) => r.url().endsWith("/42/approve") && r.method() === "PATCH",
  );
  await page.getByRole("button", { name: "Confirm approve request" }).click();
  await requestPromise;
  await expect(page.getByRole("status")).toContainText("Operation completed");
});
test("RM sees request creation and no risk or admin actions", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  await expect(
    page.getByRole("button", { name: "Approval queue", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  await page.getByRole("button", { name: "New request" }).click();
  await page.getByLabel("Credit-limit ID").fill("8");
  await page.getByLabel("Requested amount (£)").fill("100000.25");
  const pending = page.waitForRequest(
    (r) => r.url().endsWith("/credit-requests") && r.method() === "POST",
  );
  await page
    .getByRole("button", { name: "Confirm create credit request" })
    .click();
  expect((await pending).postDataJSON()).toEqual({
    creditLimitId: 8,
    amount: "100000.25",
  });
});
test("Admin sees administration without risk powers", async ({ page }) => {
  await login(page, "ADMIN");
  await expect(
    page.getByRole("button", { name: "Approval queue", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Credit requests", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "User access", exact: true }).click();
  await expect(
    page.getByText("User listing and lookup endpoints are not available", {
      exact: false,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Change role", exact: true }),
  ).toBeDisabled();
  await page.getByLabel("User ID").fill("7");
  await page.getByRole("button", { name: "Change role", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("user #7");
});
test("Exposure lookup submits with keyboard and uses backend values", async ({
  page,
}) => {
  await login(page, "RISK_OFFICER");
  await page
    .getByRole("button", { name: "Limits & exposure", exact: true })
    .click();
  await page.getByLabel("Credit-limit ID").fill("8");
  await page.getByLabel("Credit-limit ID").press("Enter");
  await expect(page.getByText("62.5% utilized")).toBeVisible();
  await expect(page.getByText("£750,000.00", { exact: true })).toBeVisible();
  await page.screenshot({
    path: "tests/artifacts/exposure-desktop.png",
    fullPage: true,
  });
});
test("Login and mobile workspace have no page overflow", async ({ page }) => {
  await page.goto("/");
  await page.screenshot({
    path: "tests/artifacts/login-desktop.png",
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(
    page.getByRole("button", { name: "Sign in securely" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await login(page, "RELATIONSHIP_MANAGER");
  await page.getByRole("button", { name: "Open navigation" }).click();
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Counterparties" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({
    path: "tests/artifacts/mobile-register.png",
    fullPage: true,
  });
});

test("desktop overview and keyboard dialog dismissal", async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 1080 });
  await login(page, "RISK_OFFICER");
  await page.screenshot({
    path: "tests/artifacts/dashboard-1920.png",
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await page.getByRole("button", { name: "New counterparty" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "New counterparty" }),
  ).toBeFocused();
});
test("backend headroom errors remain visible without fake success", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  await page.route("**/api/credit-requests", async (route) =>
    route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Insufficient available headroom for this request.",
      }),
    }),
  );
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  await page.getByRole("button", { name: "New request" }).click();
  await page.getByLabel("Credit-limit ID").fill("8");
  await page.getByLabel("Requested amount (£)").fill("900000");
  await page
    .getByRole("button", { name: "Confirm create credit request" })
    .click();
  await expect(page.getByRole("alert")).toHaveText(
    "Insufficient available headroom for this request.",
  );
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("status")).toHaveCount(0);
});

async function choosePhoto(page: Page) {
  const encoded = await page.evaluate(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 400;
    canvas.height = 200;
    const ctx = canvas.getContext("2d")!;
    ctx.fillStyle = "#ef2222";
    ctx.fillRect(0, 0, 200, 200);
    ctx.fillStyle = "#2233ef";
    ctx.fillRect(200, 0, 200, 200);
    return canvas.toDataURL("image/png").split(",")[1];
  });
  await page
    .getByLabel("Choose profile photo")
    .setInputFiles({
      name: "fixture-photo.png",
      mimeType: "image/png",
      buffer: Buffer.from(encoded, "base64"),
    });
  await expect(
    page.getByRole("button", { name: "Save photo", exact: true }),
  ).toBeEnabled();
}
test("circular photo editor saves the positioned crop and updates every avatar", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  await page.route("**/api/auth/users/profile-picture", async (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: 1,
        firstName: "Alex",
        lastName: "Morgan",
        email: "alex@example.test",
        role: "RELATIONSHIP_MANAGER",
        financialInstitution: { id: 3, name: "Test Institution" },
        profilePicturePath: "uploads/profile-test.jpg",
      }),
    }),
  );
  await page.getByRole("button", { name: "Alex Relationship manager" }).click();
  await choosePhoto(page);
  await page.getByRole("slider", { name: "Photo zoom" }).fill("2");
  const crop = page.getByRole("group", { name: "Photo position" });
  const rect = (await crop.boundingBox())!;
  await page.mouse.move(rect.x + 130, rect.y + 130);
  await page.mouse.down();
  await page.mouse.move(rect.x + 210, rect.y + 130, { steps: 5 });
  await page.mouse.up();
  await page.screenshot({
    path: "tests/artifacts/profile-photo-editor.png",
    fullPage: true,
  });
  const upload = page.waitForRequest(
    (r) => r.url().endsWith("/profile-picture") && r.method() === "PATCH",
  );
  await page.getByRole("button", { name: "Save photo", exact: true }).click();
  expect((await upload).headers()["content-type"]).toContain(
    "multipart/form-data",
  );
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(page.locator(".avatar img")).toHaveCount(3);
  const pixel = await page
    .locator(".profile-photo-button img")
    .evaluate(async (node) => {
      const img = node as HTMLImageElement;
      await img.decode();
      const c = document.createElement("canvas");
      c.width = c.height = 1;
      const ctx = c.getContext("2d")!;
      ctx.drawImage(img, 256, 256, 1, 1, 0, 0, 1, 1);
      return [...ctx.getImageData(0, 0, 1, 1).data];
    });
  expect(pixel[0]).toBeGreaterThan(200);
  expect(pixel[2]).toBeLessThan(80);
  await page.getByRole("button", { name: "Overview", exact: true }).click();
  await expect(page.locator(".avatar img")).toHaveCount(2);
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.locator(".avatar img")).toHaveCount(0);
});
test("failed photo upload keeps the editor open and leaves avatars unchanged", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  await page.route("**/api/auth/users/profile-picture", async (route) =>
    route.fulfill({ status: 500, contentType: "application/json", body: "{}" }),
  );
  await page.getByRole("button", { name: "Alex Relationship manager" }).click();
  await choosePhoto(page);
  await page.getByRole("button", { name: "Save photo", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("could not complete");
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.locator(".avatar img")).toHaveCount(0);
  await page.getByRole("button", { name: "Cancel", exact: true }).click();
  await expect(page.getByRole("dialog")).toHaveCount(0);
});
