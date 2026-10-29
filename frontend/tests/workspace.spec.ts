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
  requesterName: "Test Requester",
  counterpartyName: counterparty.name,
  counterpartyStatus: "ACTIVE",
  financialInstitutionId: 3,
  financialInstitutionName: "Test Institution",
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
  let requestState = { ...request };
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
    else if (path.endsWith("/counterparties/12")) response = counterparty;
    else if (path.endsWith("/credit-requests/42")) response = requestState;
    else if (
      path.endsWith("/pending") ||
      path.endsWith("/my") ||
      path.endsWith("/search")
    )
      response = paged([requestState]);
    else if (path.endsWith("/42/approve")) {
      requestState = { ...requestState, status: "RESERVED" };
      response = requestState;
    } else if (path.endsWith("/42/reject")) {
      requestState = { ...requestState, status: "REJECTED" };
      response = requestState;
    } else if (path.endsWith("/group-credit-limits")) response = paged([]);
    else if (path.endsWith("/corporate-group-memberships")) response = [];
    else if (path.endsWith("/financial-profile"))
      response = {
        counterpartyId: 12,
        counterpartyName: counterparty.name,
        returnOnAssets: 9.2,
        profitMargin: 14.8,
        fixedAssetTurnover: 2.1,
        debtToTotalCapitalPercent: 28,
        interestCoverage: 8.4,
        totalDebtToEbitda: 1.6,
        financialYearEnd: "2025-12-31",
        updatedAt: request.updatedAt,
      };
    else if (path.endsWith("/credit-requests"))
      response = { ...request, status: "RESERVED" };
    else if (path.endsWith("/exposure")) response = exposure;
    else if (path.endsWith("/review"))
      response = {
        ...exposure,
        creditRequestId: 42,
        requestedAmount: 600000,
        status: "PENDING_APPROVAL",
        counterpartyName: counterparty.name,
        counterpartyStatus: "ACTIVE",
        financialInstitutionId: 3,
        financialInstitutionName: "Test Institution",
        expiresAt: null,
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
  await expect(
    page.getByRole("cell", { name: "CR-000042", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "BD 600,000", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Assess risk", exact: true }).click();
  await expect(page.getByRole("main")).toContainText("CR-000042");
  await expect(page.getByRole("main")).toContainText(counterparty.name);
  await expect(page.getByRole("main")).toContainText("Test Institution");
  await expect(page.getByRole("main")).toContainText("Test Requester");
  await expect(page.getByRole("main")).toContainText("BHD 750,000.00");
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
  await expect(
    page.getByRole("cell", { name: "Test Requester", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Details", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Test Requester");
  await expect(page.getByRole("dialog")).toContainText(counterparty.name);
  await expect(page.getByRole("dialog")).toContainText("CR-000042");
  await expect(page.getByRole("dialog")).toContainText("Test Institution");
  await page.getByRole("button", { name: "Close dialog" }).click();
  await page.getByRole("button", { name: "New request" }).click();
  await page.getByLabel("Credit-limit ID").fill("8");
  await page.getByLabel("Requested amount (BHD)").fill("100000.25");
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
  await expect(page.getByText("BD 750,000", { exact: true })).toBeVisible();
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
test("group headroom is explained when individual capacity is sufficient", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  await page.route("**/api/group-credit-limits?*", (route) =>
    route.fulfill({
      json: paged([
        {
          id: 9,
          corporateGroupId: 4,
          corporateGroupName: "Pearl Infrastructure Group",
          financialInstitutionId: 3,
          amount: 2000000,
        },
      ]),
    }),
  );
  await page.route("**/api/corporate-group-memberships", (route) =>
    route.fulfill({
      json: [
        {
          id: 10,
          corporateGroupId: 4,
          corporateGroupName: "Pearl Infrastructure Group",
          counterpartyId: 12,
        },
      ],
    }),
  );
  await page.route("**/api/group-credit-limits/groups/4/exposure", (route) =>
    route.fulfill({ json: 1600000 }),
  );
  await page.route("**/api/group-credit-limits/groups/4/headroom", (route) =>
    route.fulfill({ json: 400000 }),
  );
  await page.route("**/api/credit-requests", (route) =>
    route.fulfill({
      status: 400,
      json: {
        message: "Requested amount exceeds corporate group available headroom",
      },
    }),
  );
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  await page.getByRole("button", { name: "New request" }).click();
  await page.getByLabel("Company / credit facility").selectOption("8");
  await page.getByLabel("Requested amount (BHD)").fill("450000");
  await expect(
    page.getByText(/The company has sufficient individual headroom/),
  ).toContainText("The shared group limit prevents this reservation.");
  await expect(page.getByText("Fixture dataset", { exact: true })).toHaveCount(
    0,
  );
  await page
    .getByRole("button", { name: "Confirm create credit request" })
    .click();
  await expect(page.getByRole("alert")).toHaveText(
    "Requested amount exceeds corporate group available headroom",
  );
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("status")).toHaveCount(0);
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
  await page.getByLabel("Requested amount (BHD)").fill("900000");
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
  await page.getByLabel("Choose profile photo").setInputFiles({
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

test("Risk assessment runs the existing model contract without inventing confidence", async ({
  page,
}) => {
  await login(page, "RISK_OFFICER");
  await page.route("**/api/counterparties/12/risk-prediction", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        probability: 0.054284891687056774,
        riskLevel: "LOW_RISK",
        modelVersion: "financial-risk-model-sklearn-1.6.1",
      }),
    }),
  );
  await page
    .getByRole("button", { name: "Approval queue", exact: true })
    .click();
  await page.getByRole("button", { name: "Assess risk", exact: true }).click();
  await expect(
    page.getByText("Return on assets", { exact: true }).first(),
  ).toBeVisible();
  await expect(page.getByText("28%", { exact: true })).toBeVisible();
  const prediction = page.waitForRequest(
    (r) => r.url().endsWith("/12/risk-prediction") && r.method() === "POST",
  );
  await page.getByRole("button", { name: "Run risk prediction" }).click();
  expect((await prediction).postData()).toBeNull();
  await expect(page.getByText("5.43%", { exact: true })).toBeVisible();
  await expect(page.getByRole("main")).toContainText(
    "financial-risk-model-sklearn-1.6.1",
  );
  await page.screenshot({
    path: "tests/artifacts/risk-assessment.png",
    fullPage: true,
  });
});

test("All four SSE events show real typed toasts across pages and refresh the directory", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  let refreshes = 0;
  page.on("request", (r) => {
    if (new URL(r.url()).pathname === "/api/counterparties") refreshes++;
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  const before = refreshes;
  let deliveries = 0;
  await page.route("**/api/credit-requests/events", async (route) => {
    const events =
      deliveries++ === 0
        ? [
            [
              "credit-request-status",
              { creditRequestId: 125, status: "RESERVED" },
            ],
            [
              "credit-request-status",
              { creditRequestId: 125, status: "RESERVED" },
            ],
            ["counterparty-status", { counterpartyId: 12, status: "FROZEN" }],
            [
              "credit-limit-updated",
              {
                creditLimitId: 8,
                oldLimitAmount: 2000000,
                newLimitAmount: 2500000,
              },
            ],
            [
              "group-credit-limit-updated",
              {
                groupCreditLimitId: 4,
                corporateGroupId: 9,
                oldLimitAmount: 5000000,
                newLimitAmount: 6000000,
              },
            ],
          ]
        : [];
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body:
        events
          .map(
            ([type, data]) => `event:${type}\ndata:${JSON.stringify(data)}\n\n`,
          )
          .join("") || ":connected\n\n",
    });
  });
  await expect(page.getByRole("status")).toHaveCount(4);
  await expect(
    page.getByRole("status").filter({ hasText: "#125 reserved successfully" }),
  ).toHaveCount(1);
  await expect(
    page.getByRole("status").filter({ hasText: "FROZEN" }),
  ).toHaveClass(/toast-warning/);
  await expect(
    page.getByRole("status").filter({ hasText: "group #9" }),
  ).toHaveClass(/toast-info/);
  await expect.poll(() => refreshes).toBeGreaterThan(before);
  await page.getByRole("button", { name: "Notification history" }).click();
  await expect(
    page.getByRole("region", { name: "Recent notifications" }),
  ).toContainText("group #9");
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("status")).toHaveCount(0);
});

test("A competing Risk Officer decision displays the backend conflict", async ({
  page,
}) => {
  await login(page, "RISK_OFFICER");
  await page.route("**/api/credit-requests/42/approve", (route) =>
    route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({
        message: "Credit request is no longer pending approval",
      }),
    }),
  );
  await page
    .getByRole("button", { name: "Approval queue", exact: true })
    .click();
  await page.getByRole("button", { name: "Assess risk", exact: true }).click();
  await page.getByRole("button", { name: "Approve & reserve" }).click();
  await page.getByRole("button", { name: "Confirm approve request" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "no longer pending approval",
  );
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(page.getByRole("status")).toHaveCount(0);
});

test("Incoming decision toasts remain above an open risk review dialog", async ({
  page,
}) => {
  await login(page, "RISK_OFFICER");
  await page
    .getByRole("button", { name: "Approval queue", exact: true })
    .click();
  await page.getByRole("button", { name: "Assess risk", exact: true }).click();
  await page
    .getByRole("button", { name: "Approve & Reserve", exact: true })
    .click();
  let delivered = false;
  await page.route("**/api/credit-requests/events", async (route) => {
    const body = delivered
      ? ":connected\n\n"
      : 'event:credit-request-status\ndata:{"creditRequestId":125,"status":"REJECTED"}\n\n';
    delivered = true;
    await route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      body,
    });
  });
  const toast = page.getByRole("status").filter({ hasText: "#125 rejected" });
  await expect(toast).toBeVisible();
  await expect(page.getByRole("dialog")).toBeVisible();
  await expect(toast).toHaveClass(/toast-danger/);
  const unoccluded = await toast.evaluate((node) => {
    const r = node.getBoundingClientRect();
    return node.contains(
      document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2),
    );
  });
  expect(unoccluded).toBe(true);
});
