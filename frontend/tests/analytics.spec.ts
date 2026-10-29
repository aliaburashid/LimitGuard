import { test, expect, type Page } from "@playwright/test";
import fixture from "./fixtures/analytics.json" with { type: "json" };
const paged = (content: unknown[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
});
async function setup(
  page: Page,
  options: { unconfigured?: boolean; legacy?: boolean } = {},
) {
  const data = structuredClone(fixture);
  const selected = data.requests.find(
    (r) =>
      r.status === "PENDING_APPROVAL" &&
      r.counterpartyName.endsWith("Arabian Renewable Energy"),
  )!;
  if (options.legacy) {
    selected.amount = 80000;
    const e = data.exposures.find(
      (e) => e.creditLimitId === selected.creditLimitId,
    )!;
    e.limitAmount = 1000000;
    e.usedAmount = 200000;
    e.reservedAmount = 75000;
    e.availableHeadroom = 725000;
  }
  let queued: { type: string; data: unknown }[] = [];
  const groupExposure = (groupId: number) =>
    data.exposures
      .filter((e) =>
        data.memberships.some(
          (m) =>
            m.corporateGroupId === groupId &&
            m.counterpartyId === e.counterpartyId,
        ),
      )
      .reduce((s, e) => s + e.usedAmount + e.reservedAmount, 0);
  await page.route("**/api/**", async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    if (!pathname.startsWith("/api/")) {
      await route.continue();
      return;
    }
    const path = pathname.replace(/^\/api/, "");
    const method = route.request().method();
    let response: unknown;
    if (path === "/auth/users/login")
      response = { message: "ui-contract-fixture-token" };
    else if (path === "/auth/users/profile")
      response = {
        id: 3,
        firstName: "Ahmed",
        lastName: "Al Mansoor",
        email: "burashid993@gmail.com",
        role: "RISK_OFFICER",
        financialInstitution: { id: 1, name: "Citi" },
        profilePicturePath: null,
      };
    else if (path === "/credit-requests/events") {
      const events = queued;
      queued = [];
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body:
          events
            .map((e) => `event:${e.type}\ndata:${JSON.stringify(e.data)}\n\n`)
            .join("") || ":connected\n\n",
      });
      return;
    } else if (
      path === "/credit-requests/search" ||
      path === "/credit-requests/pending"
    )
      response = paged(
        path.endsWith("pending")
          ? data.requests.filter((r) => r.status === "PENDING_APPROVAL")
          : data.requests,
      );
    else if (path === "/counterparties") response = paged(data.counterparties);
    else if (path === "/corporate-group-memberships")
      response = data.memberships;
    else if (path === "/group-credit-limits")
      response = paged(options.unconfigured ? [] : data.groups);
    else if (
      /^\/group-credit-limits\/groups\/\d+\/(exposure|headroom)$/.test(path)
    ) {
      const id = Number(path.split("/")[3]);
      const group = data.groups.find((g) => g.corporateGroupId === id)!;
      response = path.endsWith("headroom")
        ? group.amount - groupExposure(id)
        : groupExposure(id);
    } else if (/^\/credit-limits\/\d+\/exposure$/.test(path))
      response = data.exposures.find(
        (e) => e.creditLimitId === Number(path.split("/")[2]),
      );
    else if (/^\/counterparties\/\d+\/financial-profile$/.test(path))
      response = data.profiles.find(
        (p) => p.counterpartyId === Number(path.split("/")[2]),
      );
    else if (/^\/counterparties\/\d+\/risk-prediction$/.test(path))
      response = {
        probability: 0.019891539834388967,
        riskLevel: "LOW_RISK",
        modelVersion: "financial-risk-model-sklearn-1.6.1",
      };
    else if (
      /^\/credit-requests\/\d+\/(approve|reject)$/.test(path) &&
      method === "PATCH"
    ) {
      const r = data.requests.find((r) => r.id === Number(path.split("/")[2]))!;
      if (r.status !== "PENDING_APPROVAL") {
        await route.fulfill({
          status: 400,
          contentType: "application/json",
          body: JSON.stringify({
            message:
              "Only pending approval requests can be approved or rejected",
          }),
        });
        return;
      }
      r.status = path.endsWith("approve") ? "RESERVED" : "REJECTED";
      if (r.status === "RESERVED") {
        const e = data.exposures.find(
          (e) => e.creditLimitId === r.creditLimitId,
        )!;
        e.reservedAmount += r.amount;
        e.availableHeadroom -= r.amount;
      }
      response = r;
    } else if (/^\/credit-requests\/\d+$/.test(path))
      response = data.requests.find((r) => r.id === Number(path.split("/")[2]));
    else if (/^\/counterparties\/\d+$/.test(path))
      response = data.counterparties.find(
        (c) => c.id === Number(path.split("/")[2]),
      );
    if (response === undefined) {
      await route.fulfill({
        status: 404,
        contentType: "application/json",
        body: JSON.stringify({ message: "No fixture for this API path" }),
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
  await page.getByLabel("Work email").fill("burashid993@gmail.com");
  await page.getByLabel("Password", { exact: true }).fill("ui-fixture-only");
  await page.getByRole("button", { name: "Sign in securely" }).click();
  await expect(
    page.getByRole("heading", { name: "Credit oversight" }),
  ).toBeVisible();
  return {
    data,
    selected,
    emit: (type: string, eventData: unknown) =>
      queued.push({ type, data: eventData }),
  };
}
async function assess(page: Page, id: number) {
  await page
    .getByRole("navigation", { name: "Main navigation" })
    .getByRole("button", { name: "Risk Analytics", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Risk Analytics", exact: true }),
  ).toBeVisible();
  await page
    .getByLabel("Select credit request for assessment")
    .selectOption(String(id));
  await expect(
    page.getByRole("button", { name: "Run risk prediction", exact: true }),
  ).toBeEnabled();
}

test("Overview and full-page analytics render responsive real-value charts at desktop and laptop sizes", async ({
  page,
}) => {
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  const { selected } = await setup(page);
  await expect(page.locator(".overview-kpi")).toHaveCount(4);
  await expect(
    page.getByRole("group", {
      name: "Credit exposure composition chart",
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByRole("group", {
      name: "Corporate group exposure against approved credit limits",
      exact: true,
    }),
  ).toBeVisible();
  for (const [width, height] of [
    [1440, 1000],
    [1280, 800],
  ] as const) {
    await page.setViewportSize({ width, height });
    await expect
      .poll(() =>
        page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      )
      .toBe(true);
    await expect(page.locator(".overview-kpi").nth(1)).toContainText("11.4M");
    await page.mouse.move(5, 5);
  }
  await assess(page, selected.id);
  await page
    .getByRole("button", { name: "Run risk prediction", exact: true })
    .click();
  await expect(
    page.getByRole("group", {
      name: "Class 1 probability: 1.99 percent",
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.getByRole("main")).toContainText(
    "financial-risk-model-sklearn-1.6.1",
  );
  await expect(
    page.getByText("Received locally", { exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("group", {
      name: "Company contributions to committed group exposure",
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.getByRole("dialog")).toHaveCount(0);
  for (const [width, height] of [
    [1440, 1000],
    [1280, 800],
  ] as const) {
    await page.setViewportSize({ width, height });
    await page.evaluate(() => scrollTo(0, 0));
    await expect
      .poll(() =>
        page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
      )
      .toBe(true);
    await expect(
      page.getByRole("button", { name: "Approve & Reserve", exact: true }),
    ).toBeInViewport();
    await page.mouse.move(5, 5);
  }
  expect(errors).toEqual([]);
});

test("Chart tooltips show monetary data and group ordering is interactive", async ({
  page,
}) => {
  await setup(page);
  const chart = page.getByRole("group", {
    name: "Corporate group exposure against approved credit limits",
    exact: true,
  });
  await chart.locator(".recharts-bar-rectangle").first().hover();
  await expect(chart.locator(".recharts-tooltip-wrapper")).toContainText("BHD");
  await page.getByLabel("Group chart order").selectOption("exposure");
  await expect(page.getByLabel("Group chart order")).toHaveValue("exposure");
  const used = page.getByRole("button", { name: /Used: BHD/ });
  await used.focus();
  await expect(used).toBeFocused();
});

test("An unconfigured group is neutral and the historical 80k request is not silently repaired", async ({
  page,
}) => {
  const { selected } = await setup(page, { unconfigured: true, legacy: true });
  await assess(page, selected.id);
  await expect(
    page.getByText(/Group limit not configured for this institution/),
  ).toBeVisible();
  await expect(
    page.getByText(/This existing pending request is below the current/),
  ).toBeVisible();
  await expect(page.locator(".company-summary")).toContainText("BHD 80,000.00");
  await expect(page.locator(".company-summary")).toContainText(
    "BHD 275,000.00",
  );
  await expect(page.locator(".company-summary")).toContainText(
    "BHD 725,000.00",
  );
  await expect(page.locator(".capacity-values")).toContainText("BHD 75,000.00");
  await expect(page.locator(".approval-impact")).toContainText(
    "BHD 645,000.00",
  );
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("Rejection requires a reason and confirmation and refreshes the actual status", async ({
  page,
}) => {
  const { selected } = await setup(page);
  await assess(page, selected.id);
  await page
    .getByRole("button", { name: "Reject Request", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Confirm reject request", exact: true }),
  ).toBeDisabled();
  await page
    .getByLabel("Rejection reason", { exact: true })
    .fill("  Insufficient debt service capacity.  ");
  const mutation = page.waitForRequest(
    (r) => r.url().endsWith(`/${selected.id}/reject`) && r.method() === "PATCH",
  );
  await page
    .getByRole("button", { name: "Confirm reject request", exact: true })
    .click();
  expect((await mutation).postDataJSON()).toEqual({
    reason: "Insufficient debt service capacity.",
  });
  await expect(page.locator(".company-status")).toContainText("Rejected");
  await expect(
    page.getByRole("button", { name: "Approve & Reserve", exact: true }),
  ).toBeDisabled();
});

test("SSE status changes refresh overview counts and the exposure chart", async ({
  page,
}) => {
  const { data, selected, emit } = await setup(page);
  await expect(page.locator(".overview-kpi").first()).toContainText("3");
  selected.status = "RESERVED";
  const exposure = data.exposures.find(
    (e) => e.creditLimitId === selected.creditLimitId,
  )!;
  exposure.reservedAmount += selected.amount;
  exposure.availableHeadroom -= selected.amount;
  emit("credit-request-status", {
    creditRequestId: selected.id,
    status: "RESERVED",
  });
  await expect(
    page
      .getByRole("status")
      .filter({ hasText: `#${selected.id} reserved successfully` }),
  ).toBeVisible();
  await expect(
    page.locator(".overview-kpi").first().locator("strong"),
  ).toHaveText("2");
  await expect(page.locator(".overview-kpi").nth(1)).toContainText("12.15M");
});
