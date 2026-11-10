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
async function login(
  page: Page,
  role: string,
  setup?: () => Promise<void>,
  path = "/login",
) {
  let requestState = { ...request };
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith("/api/")) {
      await route.continue();
      return;
    }
    if (path.endsWith("/exposure-history")) return route.fulfill({ json: [] });
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
    } else if (path.endsWith("/credit-limits")) {
      response = paged([
        { exposure, name: counterparty.name, status: counterparty.status },
      ]);
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
  await setup?.();
  await page.goto(path);
  await page.getByLabel("Work email").fill("alex@example.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill("fixture-only-password");
  await page.getByRole("button", { name: "Sign in securely" }).click();
  if (path !== "/login") {
    await expect(
      page.getByRole("navigation", { name: "Main navigation" }),
    ).toBeVisible();
    return;
  }
  await expect(
    page.getByRole("heading", {
      name:
        role === "RELATIONSHIP_MANAGER"
          ? "Portfolio Overview"
          : "Credit oversight",
    }),
  ).toBeVisible();
}
test("company and institution branding uses initials only", async ({
  page,
}) => {
  const brandingRequests: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.includes("/branding/"))
      brandingRequests.push(request.url());
  });
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({
        json: paged([{ ...request, counterpartyName: "Visa Inc." }]),
      }),
    );
  });
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  const register = page.getByRole("table", { name: "My credit requests" });
  const company = register.getByRole("cell", {
    name: "Visa Inc.",
    exact: true,
  });
  await expect(company.locator(".company-logo")).toHaveText("VI");
  const institution = page.locator(".institution-identity");
  await expect(institution).toContainText("Test Institution");
  await expect(institution.locator(".company-logo")).toHaveText("TI");
  await expect(page.locator(".company-logo img")).toHaveCount(0);
  expect(brandingRequests).toEqual([]);

  await page.reload();
  await expect(register).toBeVisible();
  await page.route("**/api/auth/users/profile", (route) =>
    route.fulfill({ status: 401 }),
  );
  await page.reload();
  await expect(
    page.getByRole("button", { name: "Sign in securely" }),
  ).toBeVisible();
  expect(
    await page.evaluate(() => sessionStorage.getItem("limitguard.session")),
  ).toBeNull();
});

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
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "View details for CR-000042", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText("Test Requester");
  await expect(page.getByRole("dialog")).toContainText(counterparty.name);
  await expect(page.getByRole("dialog")).toContainText("CR-000042");
  await expect(page.getByRole("dialog")).toContainText("Test Institution");
  await page.getByRole("button", { name: "Close dialog" }).click();
  await page.getByRole("button", { name: "New request" }).click();
  await page
    .getByRole("combobox", { name: "Counterparty", exact: true })
    .selectOption({ label: counterparty.name });
  await page.getByLabel("Requested amount (BHD)").fill("100000.25");
  await page.getByRole("button", { name: "Check exposure" }).click();
  const projection = page.getByRole("img", { name: /^Individual facility\./ });
  await expect(projection).toHaveAccessibleName(
    /Used BD 850,000\. Reserved BD 400,000\. Proposed BD 100,000.25\. Available BD 649,999.75/,
  );
  await expect(projection.locator(".rm-segment-used")).toHaveAttribute(
    "style",
    "width: 42.5%;",
  );
  await expect(projection.locator(".rm-segment-reserved")).toHaveAttribute(
    "style",
    "width: 20%;",
  );
  const pending = page.waitForRequest(
    (r) => r.url().endsWith("/credit-requests") && r.method() === "POST",
  );
  await page.getByRole("button", { name: "Review request" }).click();
  await page
    .getByRole("button", { name: "Confirm create credit request" })
    .click();
  expect((await pending).postDataJSON()).toEqual({
    creditLimitId: 8,
    amount: "100000.25",
  });
});
test("personal request register fits long names and filters paginated personal requests", async ({
  page,
}) => {
  const statuses = [
    "RESERVED",
    "PENDING_APPROVAL",
    "USED",
    "REJECTED",
    "CANCELLED",
    "EXPIRED",
  ];
  const records = Array.from({ length: 12 }, (_, index) => ({
    ...request,
    id: 112 - index,
    requesterId: 1,
    amount: 2500000.25,
    counterpartyName:
      index === 0
        ? "International Industrial Manufacturing and Commercial Logistics Holdings Limited"
        : "Portfolio company " + index,
    status: statuses[index % statuses.length],
    createdAt: "2026-11-" + String(12 - index).padStart(2, "0") + "T10:30:00",
  }));
  const personalRequest = page.waitForRequest("**/api/credit-requests/my?*");
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({ json: paged(records) }),
    );
  });
  const params = new URL((await personalRequest).url()).searchParams;
  expect(params.get("sortBy")).toBe("createdAt");
  expect(params.get("direction")).toBe("DESC");
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  const register = page.getByRole("table", { name: "My credit requests" });
  await expect(register.getByRole("columnheader")).toHaveText([
    "Request",
    "Counterparty",
    "Amount",
    "Status",
    "Created",
    "Action",
  ]);
  await expect(register.getByRole("row")).toHaveCount(11);
  await expect(register.getByRole("row").nth(1)).toContainText(
    records[0].counterpartyName,
  );
  await expect(
    register.getByRole("cell", { name: "BD 2,500,000.25" }).first(),
  ).toHaveCSS("text-align", "right");
  await page.getByRole("button", { name: "Next", exact: true }).click();
  await expect(register.getByRole("row")).toHaveCount(3);
  await expect(register.getByRole("row").last()).toContainText("CR-000101");
  const search = page.getByRole("textbox", { name: "Search requests" });
  await search.fill("CR-000112");
  await expect(register.getByRole("row")).toHaveCount(2);
  await expect(
    register.getByRole("button", { name: "CR-000112", exact: true }),
  ).toBeVisible();
  await search.fill("unmatched company");
  await expect(
    page.getByRole("heading", { name: "No matching requests" }),
  ).toBeVisible();
  await page
    .locator(".rm-request-empty")
    .getByRole("button", { name: "Clear filters" })
    .click();
  await expect(register.getByRole("row")).toHaveCount(11);
  const from = page.getByLabel("Created from");
  await expect(from).toBeHidden();
  await page.getByRole("button", { name: "More Filters" }).click();
  await from.fill("2026-11-10");
  await page.getByLabel("Created through").fill("2026-11-11");
  await page.getByRole("combobox", { name: "Status" }).selectOption("USED");
  await expect(register.getByRole("row")).toHaveCount(2);
  await expect(
    register.getByRole("button", { name: "CR-000110", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Clear filters" }).click();
  await page.getByRole("button", { name: "More Filters" }).click();
  await page.getByRole("button", { name: "Dark mode" }).click();
  for (const width of [1024, 768, 320]) {
    await page.setViewportSize({ width, height: 900 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth),
    ).toBeLessThanOrEqual(width);
    expect(
      await register.evaluate((element) => element.scrollWidth),
    ).toBeLessThanOrEqual(
      await register.evaluate((element) => element.clientWidth),
    );
    const panelWidth = await page
      .locator(".rm-request-register")
      .evaluate((element) => element.clientWidth);
    expect(
      await register.evaluate((element) => element.clientWidth),
    ).toBeLessThanOrEqual(panelWidth);
    const action = register.getByRole("button", {
      name: "View details for CR-000112",
    });
    await expect(action).toBeInViewport();
  }
  await expect(register.getByRole("columnheader")).toHaveCount(0);
  await expect(register.getByRole("row").first()).toContainText(
    "BD 2,500,000.25",
  );
});

test("empty personal register offers request creation", async ({ page }) => {
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({ json: paged([]) }),
    );
  });
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "No credit requests yet" }),
  ).toBeVisible();
  await expect(
    page.getByText("Requests you submit will appear here."),
  ).toBeVisible();
  await page
    .locator(".rm-request-empty")
    .getByRole("button", { name: "New Request" })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
});

test("credit request details keep reservation actions visible and confirm once", async ({
  page,
}) => {
  const reservation = {
    ...request,
    id: 433,
    requesterId: 1,
    amount: 200000,
    status: "RESERVED",
    counterpartyName: "Amazon Data Services, Inc.",
    expiresAt: "2026-11-09T10:28:00",
  };
  let attempts = 0;
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({ json: paged([reservation]) }),
    );
    await page.route("**/api/credit-requests/433/use", async (route) => {
      expect(route.request().method()).toBe("PATCH");
      attempts++;
      if (attempts === 1)
        return route.fulfill({
          status: 409,
          json: { message: "The reservation changed. Please retry." },
        });
      return route.fulfill({ json: { ...reservation, status: "USED" } });
    });
  });
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  const trigger = page.getByRole("button", {
    name: "View details for CR-000433",
    exact: true,
  });
  await trigger.click();
  const details = page.getByRole("dialog", {
    name: "Credit request CR-000433",
  });
  await expect(details.getByText("BD 200,000", { exact: true })).toBeVisible();
  await expect(details.getByText("Reservation expires")).toBeVisible();
  await expect(
    details.getByRole("img", { name: /^Facility exposure/ }),
  ).toHaveAccessibleName(
    /Approved BD 2,000,000. Used BD 850,000. Reserved BD 400,000. Available BD 750,000/,
  );
  await page.keyboard.press("Escape");
  await expect(trigger).toBeFocused();
  await page.keyboard.press("Enter");
  await page.setViewportSize({ width: 360, height: 480 });
  await details.getByText("Additional details").click();
  await expect(details.getByText("Individual credit facility")).toBeVisible();
  await expect(details).not.toContainText("#8");
  const body = details.getByRole("region", { name: "Request details" });
  const header = details.getByRole("heading", {
    name: "Credit request CR-000433",
  });
  const use = details.getByRole("button", { name: "Mark as used" });
  const cancel = details.getByRole("button", { name: "Cancel reservation" });
  await expect(use).toBeEnabled();
  const top = (await header.boundingBox())?.y;
  const bottom = (await use.boundingBox())?.y;
  await body.evaluate((element) => element.scrollTo(0, element.scrollHeight));
  expect(await body.evaluate((element) => element.scrollTop)).toBeGreaterThan(
    0,
  );
  expect((await header.boundingBox())?.y).toBe(top);
  expect((await use.boundingBox())?.y).toBe(bottom);
  await expect(use).toBeInViewport();
  await expect(cancel).toBeInViewport();

  await use.click();
  const confirmation = page.getByRole("dialog", { name: "Use request" });
  await expect(confirmation).toBeVisible();
  expect(attempts).toBe(0);
  const confirm = confirmation.getByRole("button", {
    name: "Confirm use request",
  });
  await confirm.click();
  await expect(confirmation.getByRole("alert")).toContainText(
    "The reservation changed",
  );
  await confirm.click();
  await expect(confirmation).toHaveCount(0);
  expect(attempts).toBe(2);
  await expect(page.getByRole("status")).toContainText("Operation completed");
});

test("credit request details use recorded statuses and only show permitted actions", async ({
  page,
}) => {
  const states = [
    { status: "PENDING_APPROVAL", label: "Pending approval" },
    { status: "REJECTED", label: "Rejected" },
    { status: "USED", label: "Used" },
    { status: "CANCELLED", label: "Cancelled" },
    { status: "EXPIRED", label: "Expired" },
    { status: "RESERVED", label: "Reserved", otherOwner: true },
    { status: "RESERVED", label: "Reserved", missingExpiry: true },
  ];
  const requests = states.map((state, index) => ({
    ...request,
    id: 500 + index,
    requesterId: state.otherOwner ? 9 : 1,
    status: state.status,
    expiresAt:
      state.otherOwner || state.status === "EXPIRED"
        ? "2026-11-09T10:28:00"
        : null,
  }));
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({ json: paged(requests) }),
    );
  });
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  for (const [index, state] of states.entries()) {
    const reference = "CR-" + String(requests[index].id).padStart(6, "0");
    await page
      .getByRole("row")
      .filter({ hasText: reference })
      .getByRole("button", { name: "View details for " + reference })
      .click();
    const details = page.getByRole("dialog", {
      name: "Credit request " + reference,
    });
    await expect(
      details.getByText(state.label, { exact: true }).first(),
    ).toBeVisible();
    await expect(
      details.getByRole("button", { name: "Approve & reserve" }),
    ).toHaveCount(0);
    const mark = details.getByRole("button", { name: "Mark as used" });
    const cancel = details.getByRole("button", { name: "Cancel reservation" });
    if (state.missingExpiry) {
      await expect(mark).toBeDisabled();
      await expect(cancel).toBeEnabled();
      await expect(
        details.getByText("Unavailable", { exact: true }),
      ).toBeVisible();
      await cancel.click();
      await expect(
        page.getByRole("dialog", { name: "Cancel request" }),
      ).toBeVisible();
    } else {
      await expect(mark).toHaveCount(0);
      await expect(cancel).toHaveCount(0);
    }
    await page.keyboard.press("Escape");
  }
});

test("RM can find a relationship, start a prefilled request and navigate on smaller screens", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  const fitsViewport = () =>
    page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    );
  await expect(
    page.getByRole("button", { name: /Available Headroom/ }),
  ).toContainText("BD 750K");
  for (const width of [1440, 768, 320]) {
    await page.setViewportSize({ width, height: 950 });
    await expect.poll(fitsViewport).toBe(true);
  }
  await page.getByRole("button", { name: "Search workspace" }).click();
  await page.getByRole("searchbox").fill(counterparty.name);
  await page
    .getByRole("button", { name: `Counterparty ${counterparty.name}` })
    .click();
  await expect(
    page.getByRole("heading", { name: counterparty.name }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Request Credit" }).click();
  await expect(
    page.getByRole("combobox", { name: "Counterparty", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByRole("dialog")).not.toContainText(
    /Credit-limit ID|Facility #8/,
  );
  await expect(page.getByRole("dialog")).toContainText(counterparty.name);
  await expect.poll(fitsViewport).toBe(true);
  await page.keyboard.press("Escape");
  for (const [link, heading] of [
    ["Credit requests", "My Credit Requests"],
    ["Counterparties", "Counterparties"],
    ["Notifications", "Notifications"],
    ["Settings", "Your profile"],
  ]) {
    await page.getByRole("button", { name: "Open navigation" }).click();
    await page
      .getByRole("navigation", { name: "Main navigation" })
      .getByRole("button", { name: link, exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: heading, exact: true }),
    ).toBeVisible();
    await expect.poll(fitsViewport).toBe(true);
  }
});

test("Overview request search handles no facility and multiple named choices without manual IDs", async ({
  page,
}) => {
  const unfunded = { ...counterparty, id: 13, name: "Unfunded Company" };
  const secondFacility = {
    ...exposure,
    creditLimitId: 18,
    limitAmount: 3000000,
    availableHeadroom: 1750000,
  };
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/counterparties?*", (route) =>
      route.fulfill({ json: paged([counterparty, unfunded]) }),
    );
    await page.route("**/api/credit-limits?page=*", (route) =>
      route.fulfill({
        json: paged(
          [exposure, secondFacility].map((facility) => ({
            exposure: facility,
            name: counterparty.name,
            status: "ACTIVE",
          })),
        ),
      }),
    );
    await page.route("**/api/credit-limits/18/exposure", (route) =>
      route.fulfill({ json: secondFacility }),
    );
  });
  await page.getByRole("button", { name: "Create Credit Request" }).click();
  const dialog = page.getByRole("dialog");
  const company = dialog.getByRole("combobox", {
    name: "Counterparty",
    exact: true,
  });
  await dialog
    .getByRole("searchbox", { name: "Find a counterparty" })
    .fill("Unfunded");
  await company.selectOption({ label: unfunded.name });
  await expect(
    dialog.getByText(/No eligible individual credit facility/),
  ).toBeVisible();
  await expect(
    dialog.getByRole("button", { name: "Check exposure" }),
  ).toBeDisabled();

  await dialog
    .getByRole("searchbox", { name: "Find a counterparty" })
    .fill("Institutional");
  await company.selectOption({ label: counterparty.name });
  const choices = dialog.getByRole("combobox", { name: "Credit facility" });
  await expect(choices.getByRole("option")).toHaveCount(3);
  await choices.selectOption({
    label:
      "Individual credit facility 2 · BD 3,000,000 approved · BD 1,750,000 available",
  });
  await expect(dialog.getByText("BD 1,750,000", { exact: true })).toBeVisible();
  await expect(dialog).not.toContainText(/Credit-limit ID|Facility #/);
});

test("RM overview keeps current exposure separate when historical snapshots are unavailable", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  const trend = page.getByRole("region", { name: "Exposure Trend" });
  await expect(
    trend.getByRole("heading", {
      name: "Exposure history is not yet available",
    }),
  ).toBeVisible();
  await expect(
    trend.getByRole("group", { name: "Exposure trend date range" }),
  ).toHaveCount(0);
  await expect(trend.getByRole("application")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: /Available Headroom/ }),
  ).toContainText("BD 750K");
});

test("RM exposure trend tolerates the missing GET route on older servers", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER", () =>
    page.route(
      "**/api/credit-limits/exposure-history?includeCurrent=true",
      (route) => {
        expect(route.request().method()).toBe("GET");
        return route.fulfill({
          status: 405,
          json: { message: "Method 'GET' is not supported." },
        });
      },
    ),
  );
  const trend = page.getByRole("region", { name: "Exposure Trend" });
  await expect(
    trend.getByRole("heading", {
      name: "Exposure history is not yet available",
    }),
  ).toBeVisible();
  await expect(trend.getByRole("alert")).toHaveCount(0);
});

test("RM exposure trend reports genuine server errors separately from empty history", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER", () =>
    page.route(
      "**/api/credit-limits/exposure-history?includeCurrent=true",
      (route) => route.fulfill({ status: 500, json: {} }),
    ),
  );
  const trend = page.getByRole("region", { name: "Exposure Trend" });
  await expect(trend.getByRole("alert")).toContainText(
    "The service could not complete this operation",
  );
  await expect(trend.getByRole("application")).toHaveCount(0);
});

test("RM exposure trend charts recorded values and offers only supported ranges", async ({
  page,
}) => {
  await page.clock.setFixedTime(new Date("2026-11-07T12:00:00Z"));
  await login(page, "RELATIONSHIP_MANAGER", () =>
    page.route(
      "**/api/credit-limits/exposure-history?includeCurrent=true",
      (route) =>
        route.fulfill({
          json: [
            {
              capturedAt: "2026-09-25T14:00:00Z",
              usedAmount: "95000",
              reservedAmount: "25000",
              limitAmount: "200000",
            },
            {
              capturedAt: "2026-10-08T14:00:00Z",
              synthetic: true,
              usedAmount: "100000.25",
              reservedAmount: "25000.50",
              limitAmount: "200000.00",
            },
            {
              capturedAt: "2026-11-06T14:00:00Z",
              usedAmount: "125000.25",
              reservedAmount: "15000.50",
              limitAmount: "200000.00",
            },
            {
              capturedAt: "2026-11-07T00:00:00Z",
              usedAmount: "130000.25",
              reservedAmount: "17500.50",
              limitAmount: "200000.00",
            },
          ],
        }),
    ),
  );
  const trend = page.getByRole("region", { name: "Exposure Trend" });
  const ranges = trend.getByRole("group", {
    name: "Exposure trend date range",
  });
  await expect(ranges.getByRole("button")).toHaveText(["All", "7D", "30D"]);
  await ranges.getByRole("button", { name: "Last 30 days" }).click();
  await expect(
    ranges.getByRole("button", { name: "Last 30 days" }),
  ).toHaveAttribute("aria-pressed", "true");
  const chart = trend.getByRole("application");
  await expect(chart).toBeVisible();
  await chart.focus();
  await expect(trend.getByRole("status")).toContainText("BD 74,999.25");
  await expect(trend.getByRole("status")).not.toContainText(
    "Synthetic demo snapshot",
  );
  await expect(trend.getByText(/Citi demo data/)).toHaveCount(0);
  await chart.press("ArrowRight");
  await expect(trend.getByRole("status")).toContainText("BD 59,999.25");
  await trend.getByText("View snapshot values").click();
  const values = trend.getByRole("table", { name: "Exposure snapshots" });
  await expect(values.getByRole("row")).toHaveCount(4);
  await expect(
    values.getByRole("rowheader", { name: /synthetic demo/ }),
  ).toHaveCount(1);
  await expect(
    values.getByRole("cell", { name: "BD 52,499.25" }),
  ).toBeVisible();
  await ranges.getByRole("button", { name: "Last 7 days" }).click();
  await expect(values.getByRole("row")).toHaveCount(3);
  await ranges.getByRole("button", { name: "All recorded history" }).click();
  await expect(values.getByRole("row")).toHaveCount(5);
  await expect(chart.locator(".rm-trend-line")).toHaveAttribute(
    "d",
    /^M[^M]+M[^M]+M.+L/,
  );
  await page.setViewportSize({ width: 320, height: 850 });
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBe(true);
});

test("RM exposure trend draws a single emerald area and keeps the tooltip inside the chart", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER", () =>
    page.route(
      "**/api/credit-limits/exposure-history?includeCurrent=true",
      (route) =>
        route.fulfill({
          json: [26_490_000, 25_120_000, 27_300_000].map(
            (usedAmount, index) => ({
              capturedAt: `2026-11-0${index + 5}T12:00:00Z`,
              synthetic: true,
              usedAmount,
              reservedAmount: 9_245_000,
              limitAmount: 67_450_000,
            }),
          ),
        }),
    ),
  );
  const trend = page.getByRole("region", { name: "Exposure Trend" });
  const inspector = trend.locator(".rm-trend-inspector");
  const chart = trend.getByRole("application");
  const tooltip = trend.getByRole("status");

  await expect(chart.locator(".rm-trend-line")).toHaveAttribute(
    "d",
    /^M[^M]+L/,
  );
  await expect(chart.locator(".rm-trend-line")).toHaveCSS(
    "stroke",
    "rgb(0, 124, 103)",
  );
  await expect(chart.locator("linearGradient stop")).toHaveCount(2);
  await expect(trend.locator(".rm-trend-summary")).toContainText(
    "BD 67,450,000",
  );
  await expect(chart.locator(".recharts-line-curve")).toHaveCount(1);

  for (const width of [1440, 320]) {
    await page.setViewportSize({ width, height: 850 });
    await chart.focus();
    for (const key of ["ArrowLeft", "ArrowRight", "ArrowRight"]) {
      await chart.press(key);
      await expect(tooltip.getByText("Total committed exposure")).toBeVisible();
      await expect(tooltip.locator("dl > div")).toHaveCount(4);
      await expect(chart.locator(".rm-trend-guide")).toHaveCount(1);
      await expect
        .poll(async () => {
          const bounds = await inspector.boundingBox();
          const box = await tooltip.boundingBox();
          if (!bounds || !box) return false;
          return (
            box.x >= bounds.x &&
            box.y >= bounds.y &&
            box.x + box.width <= bounds.x + bounds.width &&
            box.y + box.height <= bounds.y + bounds.height
          );
        })
        .toBe(true);
      expect(
        await tooltip.locator("dl > div").evaluateAll((rows) =>
          rows.every((row) => {
            const [label, value] = Array.from(row.children);
            const left = label.getBoundingClientRect();
            const right = value.getBoundingClientRect();
            return (
              left.top === right.top &&
              left.right <= right.left &&
              getComputedStyle(value).whiteSpace === "nowrap"
            );
          }),
        ),
      ).toBe(true);
    }
  }
});

test("RM overview refreshes committed exposure and opens its KPI destinations", async ({
  page,
}) => {
  let live = { usedAmount: 850000, reservedAmount: 400000 };
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route(
      "**/api/credit-limits/exposure-history?includeCurrent=true",
      (route) =>
        route.fulfill({
          json: [
            {
              capturedAt: "2026-11-06T10:00:00Z",
              usedAmount: 800000,
              reservedAmount: 300000,
              limitAmount: 2000000,
              synthetic: true,
            },
            {
              capturedAt: "2026-11-07T10:00:00Z",
              ...live,
              limitAmount: 2000000,
              current: true,
            },
          ],
        }),
    );
    await page.route("**/api/credit-limits?page=*", (route) =>
      route.fulfill({
        json: paged([
          {
            name: counterparty.name,
            status: counterparty.status,
            exposure: {
              ...exposure,
              ...live,
              availableHeadroom:
                2000000 - live.usedAmount - live.reservedAmount,
            },
          },
        ]),
      }),
    );
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({
        json: paged([request, { ...request, id: 43, status: "USED" }]),
      }),
    );
  });
  const trend = page.getByRole("region", { name: "Exposure Trend" });
  const committed = page.getByRole("button", { name: /Committed Exposure/ });
  const headroom = page.getByRole("button", { name: /Available Headroom/ });
  await expect(committed).toHaveAccessibleName(
    "Committed Exposure, BD 1,250,000",
  );
  await expect(trend.getByText(/Live balance · BD 750,000/)).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Current portfolio exposure" }),
  ).toHaveCount(0);

  live = { usedAmount: 950000, reservedAmount: 350000 };
  await page.evaluate(() =>
    window.dispatchEvent(new Event("workspace-refresh")),
  );
  await expect(committed).toContainText("BD 1.3M");
  await expect(headroom).toContainText("BD 700K");
  await expect(trend.getByText(/Live balance · BD 700,000/)).toBeVisible();
  await trend.getByText("View snapshot values").click();
  const table = trend.getByRole("table");
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(
    table.getByRole("rowheader", { name: /synthetic demo/ }),
  ).toHaveCount(1);
  await expect(
    table.getByRole("rowheader", { name: /live reading/ }),
  ).toHaveCount(1);

  await page.getByRole("button", { name: /My Pending Requests/ }).click();
  await expect(
    page.getByRole("combobox", { name: "Status", exact: true }),
  ).toHaveValue("PENDING_APPROVAL");
  await expect(page.getByRole("row", { name: /CR-000043/ })).toHaveCount(0);
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Overview" })
    .click();
  await page.getByRole("button", { name: /Total Approved Credit/ }).click();
  await expect(
    page.getByRole("table", { name: "All Facilities" }),
  ).toBeVisible();
});

test("RM overview utility controls refresh and open their destinations", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  const refresh = page.waitForRequest((request) =>
    request
      .url()
      .includes("/credit-limits/exposure-history?includeCurrent=true"),
  );
  await page.getByRole("button", { name: "Refresh workspace" }).click();
  await refresh;
  await page.getByRole("button", { name: "Notification history" }).click();
  await expect(
    page.getByRole("heading", { name: "Notifications", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Open profile" }).click();
  await expect(
    page.getByRole("heading", { name: "Your profile" }),
  ).toBeVisible();
});

test("workspace dark mode is accessible and persists after reload", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  const toggle = page.getByRole("button", { name: "Dark mode" });
  await expect(toggle).toHaveAttribute("aria-pressed", "false");
  await toggle.focus();
  await toggle.press("Enter");
  await expect(toggle).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator(".app")).toHaveAttribute("data-theme", "dark");
  await expect(page.getByRole("banner")).toHaveCSS(
    "background-color",
    "rgb(22, 35, 53)",
  );

  await page.reload();
  await expect(toggle).toHaveAttribute("aria-pressed", "true");
  await toggle.click();
  await expect(page.locator(".app")).toHaveAttribute("data-theme", "light");
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
test("exposure tracks keep proportional segments flush at narrow and wide widths", async ({
  page,
}) => {
  const cases = [
    {
      name: "Three segments",
      used: 3333.33,
      reserved: 3333.33,
      available: 3333.34,
    },
    { name: "No reservation", used: 6500, reserved: 0, available: 3500 },
    {
      name: "Very small headroom",
      used: 9000,
      reserved: 999.99,
      available: 0.01,
    },
    { name: "Fully utilized", used: 10000, reserved: 0, available: 0 },
    { name: "Unused facility", used: 0, reserved: 0, available: 10000 },
  ];
  const companies = cases.map(({ name }, index) => ({
    ...counterparty,
    id: 120 + index,
    name,
  }));
  const facilities = cases.map(({ used, reserved, available }, index) => ({
    name: companies[index].name,
    status: "ACTIVE",
    exposure: {
      creditLimitId: 200 + index,
      counterpartyId: companies[index].id,
      limitAmount: 10000,
      usedAmount: used,
      reservedAmount: reserved,
      availableHeadroom: available,
    },
  }));
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/counterparties?*", (route) =>
      route.fulfill({ json: paged(companies) }),
    );
    await page.route("**/api/credit-limits?*", (route) =>
      route.fulfill({ json: paged(facilities) }),
    );
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  for (const [width, dark] of [
    [1440, false],
    [320, true],
  ] as const) {
    if (dark) await page.getByRole("button", { name: "Dark mode" }).click();
    await page.setViewportSize({ width, height: 900 });
    for (const { name, used, reserved, available } of cases) {
      await page.getByRole("button", { name, exact: true }).click();
      const track = page.getByRole("img", {
        name: /^Individual facility\. Approved BD 10,000/,
      });
      const segments = [used, reserved, available].filter(
        (amount) => amount > 0,
      );
      await expect(track.locator(":scope > span")).toHaveCount(segments.length);
      const geometry = await track.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return {
          left: bounds.left,
          right: bounds.right,
          width: bounds.width,
          height: bounds.height,
          parts: Array.from(element.children, (child) => {
            const rect = child.getBoundingClientRect();
            const style = getComputedStyle(child);
            return {
              left: rect.left,
              right: rect.right,
              width: rect.width,
              radius: style.borderRadius,
              shadow: style.boxShadow,
            };
          }),
        };
      });
      expect(geometry.height).toBe(13);
      let edge = geometry.left;
      for (const [index, part] of geometry.parts.entries()) {
        expect(part.left).toBe(edge);
        expect(
          Math.abs(part.width - (geometry.width * segments[index]) / 10000),
        ).toBeLessThan(0.05);
        expect(part.radius).toBe("0px");
        expect(part.shadow).toBe("none");
        edge = part.right;
      }
      expect(edge).toBeCloseTo(geometry.right, 1);
      await page
        .getByRole("button", { name: "Back to counterparties" })
        .click();
    }
  }
});

test("RM capacity workspace compares separate limits and requests credit without visible IDs", async ({
  page,
}) => {
  const skoda = { ...counterparty, id: 14, name: "Škoda Auto a.s." };
  const unfunded = { ...counterparty, id: 15, name: "Unfunded Company" };
  const skodaExposure = {
    creditLimitId: 40,
    counterpartyId: 14,
    limitAmount: 1700000,
    usedAmount: 650000,
    reservedAmount: 250000,
    availableHeadroom: 800000,
  };
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/counterparties?*", (route) =>
      route.fulfill({ json: paged([counterparty, skoda, unfunded]) }),
    );
    await page.route("**/api/counterparties/14", (route) =>
      route.fulfill({ json: skoda }),
    );
    await page.route("**/api/credit-limits?page=*", (route) =>
      route.fulfill({
        json: paged([
          { name: counterparty.name, status: "ACTIVE", exposure },
          { name: skoda.name, status: "ACTIVE", exposure: skodaExposure },
        ]),
      }),
    );
    await page.route("**/api/credit-limits/40/exposure", (route) =>
      route.fulfill({ json: skodaExposure }),
    );
    await page.route("**/api/corporate-group-memberships", (route) =>
      route.fulfill({
        json: [
          {
            id: 1,
            counterpartyId: 14,
            corporateGroupId: 6,
            corporateGroupName: "Volkswagen Group",
          },
        ],
      }),
    );
    await page.route("**/api/group-credit-limits?page=*", (route) =>
      route.fulfill({
        json: paged([
          {
            id: 1,
            corporateGroupId: 6,
            corporateGroupName: "Volkswagen Group",
            financialInstitutionId: 3,
            amount: 4600000,
          },
        ]),
      }),
    );
    await page.route("**/api/group-credit-limits/groups/6/*", (route) => {
      const path = route.request().url();
      return route.fulfill({
        json: path.endsWith("headroom")
          ? 200000
          : path.endsWith("/exposure")
            ? 4400000
            : {
                limitAmount: 4600000,
                usedAmount: 3250000,
                reservedAmount: 1150000,
                availableHeadroom: 200000,
                facilities: [skodaExposure],
              },
      });
    });
  });
  const navigation = page.getByRole("navigation", { name: "Main navigation" });
  await expect(
    navigation.getByRole("button", { name: "Limits & exposure" }),
  ).toHaveCount(0);
  await navigation
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  const search = page.getByRole("textbox", {
    name: "Search counterparties by name",
  });
  await search.fill("Škoda");
  await page.getByRole("button", { name: skoda.name, exact: true }).click();
  const details = page.locator(".rm-counterparty-details");
  await expect(
    details.getByRole("img", { name: /^Individual facility/ }),
  ).toHaveAccessibleName(/Available BD 800,000/);
  await expect(
    details.getByRole("img", { name: /^Volkswagen Group/ }),
  ).toHaveAccessibleName(/Available BD 200,000/);
  await expect(details.getByLabel("Available credit capacity")).toContainText(
    "BD 200,000",
  );
  await expect(
    details.getByText("Limited by shared group headroom"),
  ).toBeVisible();
  await expect(
    details.locator(".rm-capacity-comparison .rm-headroom-embedded"),
  ).toHaveCount(2);
  const exposureColors = {
    used: "rgb(23, 52, 92)",
    reserved: "rgb(201, 164, 92)",
    available: "rgb(203, 213, 225)",
  };
  const theme = page.getByRole("button", { name: "Dark mode" });
  for (const dark of [false, true]) {
    if (dark) await theme.click();
    for (const [segment, color] of Object.entries(exposureColors)) {
      await expect(
        details.locator(`.rm-capacity-track .rm-segment-${segment}`).first(),
      ).toHaveCSS("background-color", color);
      await expect(
        details.locator(`.rm-capacity-legend i.rm-segment-${segment}`).first(),
      ).toHaveCSS("background-color", color);
    }
  }
  const track = details.locator(".rm-capacity-track").first();
  await expect(track).toHaveCSS("height", "13px");
  await expect(track).toHaveCSS("outline-style", "solid");
  await expect(track.locator(".rm-segment-used")).toHaveCSS(
    "box-shadow",
    "none",
  );
  await expect(track.locator(".rm-segment-used")).toHaveCSS(
    "border-radius",
    "0px",
  );
  await theme.click();
  await details
    .getByRole("button", { name: "Request Credit", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText(skoda.name);
  await expect(dialog).not.toContainText(/Credit-limit ID|Facility #/);
  await dialog.getByLabel("Requested amount (BHD)").fill("100000");
  await dialog.getByRole("button", { name: "Check exposure" }).click();
  await dialog.getByRole("button", { name: "Review request" }).click();
  const submission = page.waitForRequest(
    (request) =>
      request.method() === "POST" && request.url().endsWith("/credit-requests"),
  );
  await dialog
    .getByRole("button", { name: "Confirm create credit request" })
    .click();
  expect((await submission).postDataJSON()).toEqual({
    creditLimitId: 40,
    amount: "100000",
  });
  await expect(dialog).toHaveCount(0);

  await page.getByRole("button", { name: "Back to counterparties" }).click();
  await search.fill("Unfunded");
  await page.getByRole("button", { name: unfunded.name, exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "No credit facility" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Request Credit", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Back to counterparties" }).click();
  await page.getByRole("tab", { name: "All Facilities" }).click();
  const table = page.getByRole("table", { name: "All Facilities" });
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(search).toBeHidden();
  await page
    .getByRole("combobox", { name: "Sort facilities" })
    .selectOption("headroom");
  await expect(table.getByRole("row").nth(1)).toContainText(skoda.name);
  await table
    .getByRole("button", { name: "View exposure for " + counterparty.name })
    .click();
  await expect(
    page.getByRole("heading", { name: counterparty.name, exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Available credit capacity")).toContainText(
    "BD 750,000",
  );
  await expect(
    page.getByRole("img", { name: /shared group limit/i }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Back to facilities" }).click();
  await expect(table).toBeVisible();
  await page.setViewportSize({ width: 320, height: 850 });
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth))
    .toBeLessThanOrEqual(320);
});

test("legacy exposure URLs preserve the selected facility and additional query parameters", async ({
  page,
}) => {
  await login(
    page,
    "RELATIONSHIP_MANAGER",
    undefined,
    "/exposure?creditLimitId=8&source=bookmark&page=2",
  );
  await expect(page).toHaveURL(
    /\/counterparties\?.*source=bookmark.*page=2.*tab=facilities.*facilityId=8/,
  );
  await expect(
    page.getByRole("heading", { name: counterparty.name, exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel("Available credit capacity")).toContainText(
    "BD 750,000",
  );
  await page
    .getByRole("button", { name: "Request Credit", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText(counterparty.name);
});

test("Risk Officer legacy facility links open scoped exposure and management controls", async ({
  page,
}) => {
  await login(page, "RISK_OFFICER", undefined, "/limits-and-exposure/8");
  await expect(page).toHaveURL(/\/counterparties\?tab=facilities&facilityId=8/);
  await expect(page.getByLabel("Credit-limit ID")).toHaveValue("8");
  await expect(page.getByText("BD 750,000", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Update limit", exact: true }),
  ).toBeVisible();
});

test("Exposure lookup submits with keyboard and uses backend values", async ({
  page,
}) => {
  await login(page, "RISK_OFFICER");
  await expect(
    page
      .getByRole("navigation")
      .getByRole("button", { name: "Limits & exposure" }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await page.getByRole("button", { name: "Facilities & Groups" }).click();
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
  await page.goto("/login");
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
async function setupSharedGroup(page: Page) {
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
  await page.route(
    "**/api/group-credit-limits/groups/4/exposure-breakdown",
    (route) =>
      route.fulfill({
        json: {
          limitAmount: 2000000,
          usedAmount: 1150000,
          reservedAmount: 450000,
          availableHeadroom: 400000,
          facilities: [exposure],
        },
      }),
  );
}

test("RM counterparty details keep limits separate and open the selected facility and company register", async ({
  page,
}) => {
  const secondFacility = {
    ...exposure,
    creditLimitId: 19,
    limitAmount: 900000,
    usedAmount: 200000,
    reservedAmount: 200000,
    availableHeadroom: 500000,
  };
  const other = {
    ...request,
    id: 43,
    counterpartyId: 13,
    counterpartyName: "Other Company",
  };
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await setupSharedGroup(page);
    await page.route("**/api/credit-limits?*", (route) =>
      route.fulfill({
        json: paged(
          [exposure, secondFacility].map((facility) => ({
            exposure: facility,
            name: counterparty.name,
            status: counterparty.status,
          })),
        ),
      }),
    );
    await page.route("**/api/credit-limits/19/exposure", (route) =>
      route.fulfill({ json: secondFacility }),
    );
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({ json: paged([other, request]) }),
    );
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await page
    .getByRole("button", { name: counterparty.name, exact: true })
    .click();
  const summary = page.getByLabel("Available credit capacity");
  const requestCredit = page.getByRole("button", {
    name: "Request Credit",
    exact: true,
  });
  await expect(requestCredit).toBeDisabled();
  await page
    .getByRole("combobox", { name: "Individual facility" })
    .selectOption({
      label: "Facility 2 · BD 900,000 approved",
    });
  await expect(summary).toContainText("BD 400,000");
  await expect(summary).toContainText("BD 500,000");
  await expect(summary).toContainText("Limited by shared group headroom");
  await expect(
    page.getByRole("img", {
      name: /^Pearl Infrastructure Group · Shared group limit/,
    }),
  ).toHaveAccessibleName(
    /Used BD 1,150,000. Reserved BD 450,000. Available BD 400,000/,
  );
  await page.getByText("Additional details", { exact: true }).click();
  await expect(page.getByText("Created", { exact: true })).toBeVisible();
  const selectedFacility = page.waitForRequest(
    "**/api/credit-limits/19/exposure",
  );
  await requestCredit.click();
  await selectedFacility;
  await expect(page.getByRole("dialog")).toContainText(counterparty.name);
  await page.keyboard.press("Escape");
  const history = page
    .getByRole("region", { name: "Recent credit requests" })
    .first();
  await expect(history.getByRole("row")).toHaveCount(2);
  await expect(
    history.getByRole("columnheader", { name: "Last updated" }),
  ).toBeVisible();
  await expect(history.locator(".rm-counterparty-updated time")).toHaveText(
    "06 Oct 2026, 10:00",
  );
  const view = history.getByRole("button", {
    name: "View details",
    exact: true,
  });
  await view.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toContainText("CR-000042");
  await page.keyboard.press("Escape");
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await page
    .getByRole("button", { name: counterparty.name, exact: true })
    .click();
  await page.getByRole("button", { name: "View all requests" }).click();
  await expect(
    page.getByRole("cell", { name: "CR-000042", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "CR-000043", exact: true }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Clear company filter" }).click();
  await expect(
    page.getByRole("cell", { name: "CR-000043", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await page
    .getByRole("button", { name: counterparty.name, exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "View facility details" }),
  ).toHaveCount(0);
  await page
    .getByRole("combobox", { name: "Individual facility" })
    .selectOption({
      label: "Facility 2 · BD 900,000 approved",
    });
  await expect(
    page.getByRole("region", { name: "Credit exposure", exact: true }),
  ).toContainText("BD 900,000");
});

test("counterparty details show recent requests after the RM submits their first request", async ({
  page,
}) => {
  let submitted = false;
  const first = {
    ...request,
    requesterId: 1,
    amount: 100000,
    status: "RESERVED",
  };
  const other = { ...request, id: 43, counterpartyId: 13 };
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/credit-requests/my?*", (route) =>
      route.fulfill({ json: paged(submitted ? [first, other] : [other]) }),
    );
    await page.route("**/api/credit-requests", (route) => {
      expect(route.request().method()).toBe("POST");
      submitted = true;
      return route.fulfill({ status: 201, json: first });
    });
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await page
    .getByRole("button", { name: counterparty.name, exact: true })
    .click();
  const history = page.getByRole("heading", { name: "Recent credit requests" });
  await expect(history).toHaveCount(0);
  await page
    .getByRole("button", { name: "Request Credit", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Requested amount (BHD)").fill("100000");
  await dialog.getByRole("button", { name: "Check exposure" }).click();
  await dialog.getByRole("button", { name: "Review request" }).click();
  await dialog
    .getByRole("button", { name: "Confirm create credit request" })
    .click();
  await expect(history).toBeVisible();
  const table = page.getByRole("table");
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table.getByRole("cell", { name: "BD 100,000" })).toBeVisible();
});

test("recent requests preserve recorded timestamps and status across the responsive table", async ({
  page,
}) => {
  const recent = [
    {
      ...request,
      id: 433,
      amount: 200000,
      status: "RESERVED",
      updatedAt: "2026-10-06T11:30:00",
    },
    { ...request, id: 434, amount: 100000, status: "USED", updatedAt: null },
    { ...request, id: 435, amount: 50000, status: "PENDING_APPROVAL" },
  ];
  let releaseHistory = () => {};
  const historyReady = new Promise<void>((resolve) => {
    releaseHistory = resolve;
  });
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/credit-requests/my?*", async (route) => {
      await historyReady;
      return route.fulfill({ json: paged(recent) });
    });
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await page
    .getByRole("button", { name: counterparty.name, exact: true })
    .click();
  const section = page
    .getByRole("region", { name: "Recent credit requests" })
    .first();
  await expect(section).toHaveCount(0);
  releaseHistory();
  const table = section.getByRole("table");
  const reserved = table.getByRole("row").filter({ hasText: "BD 200,000" });
  await expect(reserved).toContainText("Reserved");
  await expect(
    reserved.locator('time[datetime="2026-10-06T11:30:00"]:visible'),
  ).toHaveText("06 Oct 2026, 11:30");
  await expect(table.getByLabel("Last updated time unavailable")).toHaveText(
    "—",
  );
  await expect(
    table
      .getByRole("row")
      .filter({ hasText: "BD 50,000" })
      .locator(".rm-counterparty-updated time"),
  ).toHaveText("06 Oct 2026, 10:00");
  await expect(table.getByRole("columnheader", { name: "Amount" })).toHaveCSS(
    "text-align",
    "left",
  );
  await expect(reserved.getByRole("cell", { name: "BD 200,000" })).toHaveCSS(
    "text-align",
    "left",
  );
  await expect(
    table.getByRole("columnheader", { name: "Submitted" }),
  ).toHaveCSS("text-align", "left");
  await expect(reserved).toHaveCSS("height", "50px");
  await page.getByRole("button", { name: "Dark mode" }).click();
  await expect(reserved.getByText("Reserved")).toHaveCSS(
    "color",
    "rgb(228, 200, 144)",
  );
  await page.setViewportSize({ width: 320, height: 850 });
  await expect(
    table.getByRole("columnheader", { name: "Last updated" }),
  ).toHaveCount(0);
  await reserved.scrollIntoViewIfNeeded();
  await expect(
    reserved.getByRole("button", { name: "View details" }),
  ).toBeInViewport();
  await expect(reserved.getByLabel(/^Last updated /)).toHaveText(
    "Updated 11:30",
  );
  expect(
    await table.evaluate((element) => element.getBoundingClientRect().right),
  ).toBeLessThanOrEqual(320);
});

test("RM group tab separates shared balances from member facilities and preserves request creation", async ({
  page,
}) => {
  const subsidiary = { ...counterparty, id: 13, name: "Pearl Subsidiary" };
  const subsidiaryFacility = {
    creditLimitId: 9,
    counterpartyId: subsidiary.id,
    limitAmount: 500000,
    usedAmount: 300000,
    reservedAmount: 50000,
    availableHeadroom: 150000,
  };
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await setupSharedGroup(page);
    await page.route("**/api/counterparties?*", (route) =>
      route.fulfill({ json: paged([counterparty, subsidiary]) }),
    );
    await page.route("**/api/credit-limits?*", (route) =>
      route.fulfill({
        json: paged([
          { exposure, name: counterparty.name, status: counterparty.status },
          {
            exposure: subsidiaryFacility,
            name: subsidiary.name,
            status: subsidiary.status,
          },
        ]),
      }),
    );
    await page.route("**/api/counterparties/13", (route) =>
      route.fulfill({ json: subsidiary }),
    );
    await page.route("**/api/credit-limits/9/exposure", (route) =>
      route.fulfill({ json: subsidiaryFacility }),
    );
    await page.route(
      "**/api/group-credit-limits/groups/4/exposure-breakdown",
      (route) => {
        expect(route.request().method()).toBe("GET");
        return route.fulfill({
          json: {
            limitAmount: 2000000,
            usedAmount: 1150000,
            reservedAmount: 450000,
            availableHeadroom: 400000,
            facilities: [exposure, subsidiaryFacility],
          },
        });
      },
    );
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  const individual = page.getByRole("tab", {
    name: "Individual Counterparties",
  });
  const groups = page.getByRole("tab", { name: "Corporate Groups" });
  await expect(individual).toHaveAttribute("aria-selected", "true");
  const table = page.getByRole("table", { name: "Counterparty portfolio" });
  await expect(table.getByRole("columnheader")).toHaveText([
    "Company",
    "Corporate Group",
    "Credit Facility",
    "Utilization",
    "Status",
    "Action",
  ]);
  const companyRow = table
    .getByRole("row")
    .filter({ hasText: counterparty.name });
  await expect(companyRow).toContainText("BD 2M approved");
  await expect(companyRow).toContainText("Pearl Infrastructure Group");
  await expect(companyRow).not.toContainText("#8");
  const subsidiaryRow = table
    .getByRole("row")
    .filter({ hasText: subsidiary.name });
  await expect(
    subsidiaryRow.getByRole("cell", { name: /approved/ }),
  ).toBeVisible();
  await expect(subsidiaryRow.getByRole("cell")).toHaveCount(5);
  await companyRow
    .getByRole("button", { name: counterparty.name, exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: counterparty.name, exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("main")).not.toContainText(
    /CP-12|Facility #8|CR-000042/,
  );
  await page.getByRole("button", { name: "Back to counterparties" }).click();
  await page
    .getByRole("textbox", { name: "Search counterparties by name" })
    .fill("Institutional");
  await individual.focus();
  await individual.press("ArrowRight");
  await expect(groups).toBeFocused();
  const panel = page.getByRole("tabpanel", { name: "Corporate Groups" });
  const shared = panel.getByRole("img", {
    name: /^Pearl Infrastructure Group\./,
  });
  await expect(shared).toHaveAccessibleName(
    /Approved BD 2,000,000. Used BD 1,150,000. Reserved BD 450,000. Available BD 400,000/,
  );
  await expect(panel.getByText("2 member companies")).toBeVisible();
  await expect(panel.getByText(subsidiary.name, { exact: true })).toHaveCount(
    0,
  );
  await expect(panel.getByText("Shared approved credit limit")).toContainText(
    "BD 2,000,000",
  );
  await panel
    .getByRole("textbox", { name: "Search corporate groups" })
    .fill(subsidiary.name);
  await expect(shared).toBeVisible();
  await panel.getByLabel("Filter groups by headroom").selectOption("limited");
  await expect(
    panel.getByRole("heading", { name: "No matching corporate groups" }),
  ).toBeVisible();
  await panel.getByLabel("Filter groups by headroom").selectOption("");
  const viewMembers = panel.getByRole("button", {
    name: "View members of Pearl Infrastructure Group",
  });
  await viewMembers.focus();
  await viewMembers.press("Enter");
  await expect(
    panel.getByRole("heading", {
      name: "Pearl Infrastructure Group",
      exact: true,
      level: 2,
    }),
  ).toBeFocused();
  await expect(
    panel.getByRole("heading", { name: subsidiary.name }),
  ).toBeVisible();
  await expect(
    panel.getByRole("img", {
      name: /^Individual facility\. Approved BD 500,000\./,
    }),
  ).toHaveAccessibleName(
    /Approved BD 500,000. Used BD 300,000. Reserved BD 50,000. Available BD 150,000/,
  );
  await expect(panel).not.toContainText(/CP-1[23]|Facility #[89]/);
  await panel
    .getByRole("button", { name: `Request credit for ${subsidiary.name}` })
    .click();
  await expect(page.getByRole("dialog")).not.toContainText(
    /Credit-limit ID|Facility #9/,
  );
  await expect(page.getByRole("dialog")).toContainText(subsidiary.name);
  await page.getByLabel("Requested amount (BHD)").fill("100000");
  await expect(
    page.getByRole("button", { name: "Check exposure" }),
  ).toBeEnabled();
  await page.keyboard.press("Escape");
  await expect(
    panel.getByRole("heading", { name: "Connected corporate groups" }),
  ).toHaveCount(0);
  await panel.getByRole("button", { name: "Back to corporate groups" }).click();
  await expect(viewMembers).toBeFocused();
  await page.setViewportSize({ width: 320, height: 850 });
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBe(true);
  await individual.click();
  await expect(
    page.getByRole("textbox", { name: "Search counterparties by name" }),
  ).toHaveValue("Institutional");
  await page.getByRole("tab", { name: "Corporate Groups" }).click();
  await viewMembers.click();
  await page.reload();
  await expect(
    panel.getByRole("heading", { name: subsidiary.name }),
  ).toBeVisible();
});

test("RM corporate directory filters and pages confirmed institution facilities", async ({
  page,
}) => {
  const companies = Array.from({ length: 12 }, (_, index) => ({
    ...counterparty,
    id: 100 + index,
    name: `Example Company ${String(index + 1).padStart(2, "0")}`,
    status: index === 11 ? "FROZEN" : "ACTIVE",
  }));
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/counterparties?*", (route) => {
      const number = Number(
        new URL(route.request().url()).searchParams.get("page"),
      );
      return route.fulfill({
        json: {
          content: companies.slice(number * 6, number * 6 + 6),
          totalElements: 12,
          totalPages: 2,
          number,
        },
      });
    });
    await page.route("**/api/credit-limits?page=*", (route) =>
      route.fulfill({
        json: paged([
          {
            name: companies[0].name,
            status: "ACTIVE",
            exposure: { ...exposure, counterpartyId: 100 },
          },
          {
            name: companies[11].name,
            status: "FROZEN",
            exposure: {
              ...exposure,
              counterpartyId: 111,
              creditLimitId: 9,
              usedAmount: 1900000,
              reservedAmount: 0,
              availableHeadroom: 100000,
            },
          },
        ]),
      }),
    );
    await page.route("**/api/corporate-group-memberships", (route) =>
      route.fulfill({
        json: [
          {
            id: 1,
            counterpartyId: 111,
            corporateGroupId: 4,
            corporateGroupName: "Example Holdings",
          },
        ],
      }),
    );
  });
  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  const table = page.getByRole("table", { name: "Counterparty portfolio" });
  await expect(
    page.getByText("12 counterparties", { exact: true }),
  ).toBeVisible();
  await expect(table.getByRole("row")).toHaveCount(11);
  await page.getByRole("button", { name: "Next" }).click();
  await expect(table.getByRole("row")).toHaveCount(3);
  await expect(table.getByText("Example Holdings")).toBeVisible();

  await page.getByRole("button", { name: "Filters", exact: true }).click();
  await page
    .getByRole("combobox", { name: "Facility availability" })
    .selectOption("without");
  await expect(
    page.getByText("10 of 12 counterparties", { exact: true }),
  ).toBeVisible();
  await expect(
    table.getByRole("cell", { name: "No facility", exact: true }),
  ).toHaveCount(10);
  await page.getByRole("button", { name: "Reset filters" }).click();
  await page
    .getByRole("combobox", { name: "Corporate group" })
    .selectOption("4");
  await page
    .getByRole("combobox", { name: "Counterparty status" })
    .selectOption("FROZEN");
  await expect(table.getByRole("row")).toHaveCount(2);
  await expect(table.getByText("95.0%")).toBeVisible();
  await expect(table.locator('[data-level="critical"]')).toHaveCount(1);
  await table
    .getByRole("button", { name: /View details for Example Company 12/ })
    .click();
  await expect(
    page.getByRole("heading", { name: "Example Company 12", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("region", { name: "Credit exposure" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Request Credit" }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "Recent credit requests" }),
  ).toHaveCount(0);

  await page.getByRole("button", { name: "Back to counterparties" }).click();
  await page.getByRole("button", { name: "Reset filters" }).click();
  await page
    .getByRole("combobox", { name: "Sort counterparties" })
    .selectOption("utilization");
  await expect(table.getByRole("row").nth(1)).toContainText(
    "Example Company 12",
  );
  await page
    .getByRole("textbox", { name: "Search counterparties by name" })
    .fill("Example Company 02");
  await table
    .getByRole("button", { name: "Example Company 02", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "No credit facility", exact: true }),
  ).toBeVisible();
});

test("Citi only lists confirmed Citi relationships and the two unfunded demo companies", async ({
  page,
}) => {
  const extraCompanies = [
    { id: 70, name: "Legacy Other Institution" },
    { id: 71, name: "North Quay Services W.L.L. (fictional)" },
    { id: 72, name: "Orchid Quay Trading W.L.L. (fictional)" },
    { id: 73, name: "Citi Shared Limit Member" },
  ].map((company) => ({ ...counterparty, ...company }));
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/auth/users/profile", (route) =>
      route.fulfill({
        json: {
          id: 15,
          firstName: "Alia",
          lastName: "RM",
          email: "alex@example.test",
          role: "RELATIONSHIP_MANAGER",
          financialInstitution: { id: 5, name: "Citi" },
        },
      }),
    );
    await page.route("**/api/counterparties?*", (route) =>
      route.fulfill({ json: paged([counterparty, ...extraCompanies]) }),
    );
    await page.route("**/api/corporate-group-memberships", (route) =>
      route.fulfill({
        json: [
          {
            id: 1,
            counterpartyId: 70,
            corporateGroupId: 9,
            corporateGroupName: "Other Group",
          },
          {
            id: 2,
            counterpartyId: 73,
            corporateGroupId: 10,
            corporateGroupName: "Citi Group",
          },
        ],
      }),
    );
    await page.route("**/api/group-credit-limits?page=*", (route) =>
      route.fulfill({
        json: paged([
          {
            id: 1,
            corporateGroupId: 10,
            corporateGroupName: "Citi Group",
            financialInstitutionId: 5,
            amount: 500000,
          },
        ]),
      }),
    );
    await page.route("**/api/group-credit-limits/groups/10/*", (route) =>
      route.fulfill({ json: 100000 }),
    );
  });

  await page
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await expect(
    page.getByText("4 counterparties", { exact: true }),
  ).toBeVisible();
  const table = page.getByRole("table", { name: "Counterparty portfolio" });
  const companyNames = [
    "Citi Shared Limit Member",
    counterparty.name,
    "North Quay Services W.L.L. (fictional)",
    "Orchid Quay Trading W.L.L. (fictional)",
  ];
  await expect(table.getByRole("rowheader")).toHaveCount(companyNames.length);
  for (const [index, name] of companyNames.entries()) {
    await expect(table.getByRole("rowheader").nth(index)).toHaveAccessibleName(
      name,
    );
  }
  await page.getByRole("button", { name: "Search workspace" }).click();
  await page.getByRole("searchbox").fill("Legacy Other Institution");
  await expect(
    page.getByRole("button", { name: /Counterparty Legacy Other Institution/ }),
  ).toHaveCount(0);
});

test("group headroom is explained when individual capacity is sufficient", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await setupSharedGroup(page);
    await page.route("**/api/credit-requests", (route) =>
      route.fulfill({
        status: 400,
        json: {
          message:
            "Requested amount exceeds corporate group available headroom",
        },
      }),
    );
  });
  await page
    .getByRole("button", { name: "Credit requests", exact: true })
    .click();
  await page.getByRole("button", { name: "New request" }).click();
  await page
    .getByRole("combobox", { name: "Counterparty", exact: true })
    .selectOption({ label: counterparty.name });
  await page.getByLabel("Requested amount (BHD)").fill("450000");
  await page.getByRole("button", { name: "Check exposure" }).click();
  await expect(
    page.getByRole("img", { name: /^Shared group/ }),
  ).toHaveAccessibleName(
    /Committed \(used \+ reserved\) BD 1,600,000.*Available BD 0\. Exceeded by BD 50,000/,
  );
  await expect(
    page.getByText(/The company has sufficient individual headroom/),
  ).toContainText("The shared group limit prevents this reservation.");
  await expect(page.getByText("Fixture dataset", { exact: true })).toHaveCount(
    0,
  );
  await expect(
    page.getByRole("button", { name: "Review request" }),
  ).toBeDisabled();
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
  await page
    .getByRole("combobox", { name: "Counterparty", exact: true })
    .selectOption({ label: counterparty.name });
  await page.getByLabel("Requested amount (BHD)").fill("450000");
  await page.getByRole("button", { name: "Check exposure" }).click();
  await page.getByRole("button", { name: "Review request" }).click();
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
  const profile = {
    id: 1,
    firstName: "Alex",
    lastName: "Morgan",
    email: "alex@example.test",
    role: "RELATIONSHIP_MANAGER",
    financialInstitution: { id: 3, name: "Test Institution" },
    profilePicturePath: "",
  };
  await login(page, "RELATIONSHIP_MANAGER", async () => {
    await page.route("**/api/auth/users/profile", (route) =>
      route.fulfill({ json: profile }),
    );
    await page.route("**/api/auth/users/profile-picture", async (route) => {
      if (route.request().method() === "PATCH") {
        profile.profilePicturePath = "uploads/profile-test.jpg";
        return route.fulfill({ json: profile });
      }
      expect(route.request().headers().authorization).toBe(
        "Bearer fixture-only-token",
      );
      return route.fulfill({
        contentType: "image/png",
        body: Buffer.from(
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jP1sAAAAASUVORK5CYII=",
          "base64",
        ),
      });
    });
  });
  await page.getByRole("button", { name: "Open profile", exact: true }).click();
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
  await page.reload();
  await expect(page.locator(".avatar img")).toHaveCount(3);
  await page.getByRole("button", { name: "Overview", exact: true }).click();
  await expect(page.locator(".avatar img")).toHaveCount(2);
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.locator(".avatar img")).toHaveCount(0);
  await page.getByLabel("Work email").fill("alex@example.test");
  await page
    .getByLabel("Password", { exact: true })
    .fill("fixture-only-password");
  await page.getByRole("button", { name: "Sign in securely" }).click();
  await expect(page.locator(".avatar img")).toHaveCount(2);
});
test("failed photo upload keeps the editor open and leaves avatars unchanged", async ({
  page,
}) => {
  await login(page, "RELATIONSHIP_MANAGER");
  await page.route("**/api/auth/users/profile-picture", async (route) =>
    route.fulfill({ status: 500, contentType: "application/json", body: "{}" }),
  );
  await page.getByRole("button", { name: "Open profile", exact: true }).click();
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
  await expect(page.locator(".toast[role=status]")).toHaveCount(4);
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
    page.getByRole("heading", { name: "Notifications" }),
  ).toBeVisible();
  await expect(page.getByRole("main")).toContainText("group #9");
  await expect(page.getByRole("main")).toContainText("4 unread");
  await page.getByRole("button", { name: "Mark all as read" }).click();
  await page.getByRole("button", { name: "Unread", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "You’re caught up" }),
  ).toBeVisible();
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
