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
    else if (path === "/corporate-groups")
      response = paged(
        data.groups.map((g) => ({
          id: g.corporateGroupId,
          name: g.corporateGroupName,
        })),
      );
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
  await page.goto("/login");
  await page.getByLabel("Work email").fill("burashid993@gmail.com");
  await page.getByLabel("Password", { exact: true }).fill("ui-fixture-only");
  await page.getByRole("button", { name: "Sign in securely" }).click();
  await expect(
    page.getByRole("heading", { name: "Credit Portfolio Overview" }),
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
  const picker = page.getByLabel("Select credit request for assessment");
  await expect(picker).toHaveValue(/^\d+$/);
  await picker.selectOption(String(id));
  await expect(picker).toHaveValue(String(id));
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
  await expect(page.locator(".overview-kpi")).toHaveCount(3);
  await expect(
    page.getByRole("group", {
      name: "Credit exposure composition chart",
      exact: true,
    }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("group", {
      name: "Corporate group utilization",
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
    page.getByRole("region", {
      name: "Company group risk assessment",
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
      page.getByRole("region", { name: "Credit decision" }),
    ).toHaveCSS("position", "static");
    await page
      .getByRole("button", { name: "Approve & Reserve", exact: true })
      .scrollIntoViewIfNeeded();
    await expect(
      page.getByRole("button", { name: "Approve & Reserve", exact: true }),
    ).toBeInViewport();
    await page.mouse.move(5, 5);
  }
  expect(errors).toEqual([]);
});

test("Compact group exposure bars show ranked utilization and retain financial details", async ({
  page,
}) => {
  await setup(page);
  const chart = page.getByRole("group", {
    name: "Corporate group utilization",
    exact: true,
  });
  await expect(chart.locator(".ro-group-row")).toHaveCount(5);
  await expect(chart.locator(".ro-group-row").first()).toHaveAttribute(
    "aria-label",
    /% utilized\. Committed BHD .*approved limit BHD/,
  );
  const percentages = await chart.locator(".ro-group-value").allTextContents();
  expect(percentages.map(parseFloat)).toEqual(
    percentages.map(parseFloat).sort((a, b) => b - a),
  );
  await page
    .getByRole("button", { name: "View all groups", exact: true })
    .click();
  await expect(
    page
      .getByRole("group", { name: "Counterparty views" })
      .getByRole("button", { name: "Corporate Groups", exact: true }),
  ).toHaveAttribute("aria-pressed", "true");
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
  await expect(page.locator(".ra-company-header")).toContainText(
    "BHD 80,000.00",
  );
  await expect(page.locator(".ra-capacity-card")).toContainText(
    "BHD 725,000.00",
  );
  await expect(page.locator(".ra-company-header")).not.toContainText(
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
  await expect(page.locator(".ra-badges")).toContainText("Rejected");
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

test("Group assessment uses one selected-group POST and displays independent successes and failures", async ({
  page,
}) => {
  const { data, selected } = await setup(page);
  let calls = 0;
  let requestedPath = "";
  await page.route(
    "**/api/corporate-groups/*/risk-assessment",
    async (route) => {
      calls++;
      requestedPath = new URL(route.request().url()).pathname;
      expect(route.request().method()).toBe("POST");
      await route.fulfill({
        json: data.counterparties.slice(0, 4).map((c, i) => ({
          counterpartyId: c.id,
          counterpartyName: c.name,
          successful: i !== 3,
          probability: i === 3 ? null : 0.02,
          riskLevel: i === 3 ? null : "LOW_RISK",
          errorMessage: i === 3 ? "Risk assessment failed" : null,
        })),
      });
    },
  );
  await assess(page, selected.id);
  const requestGroup = data.memberships.find(
    (m) => m.counterpartyId === selected.counterpartyId,
  )?.corporateGroupId;
  const group = data.groups.find((g) => g.corporateGroupId !== requestGroup)!;
  await page
    .getByText("Other portfolio group assessments", { exact: true })
    .click();
  await page
    .getByLabel("Corporate group to assess")
    .selectOption(String(group.corporateGroupId));
  await page
    .getByRole("region", {
      name: "Parallel group risk assessment",
      exact: true,
    })
    .getByRole("button", { name: "Run group assessment", exact: true })
    .click();
  const section = page.getByRole("region", {
    name: "Parallel group risk assessment",
  });
  await expect(section.getByRole("status")).toContainText(
    "3 of 4 companies assessed successfully",
  );
  await expect(section.getByRole("table").getByRole("row")).toHaveCount(5);
  await expect(
    section.getByText("Assessment failed", { exact: true }),
  ).toBeVisible();
  await expect(section.getByText("Unavailable", { exact: true })).toBeVisible();
  expect(calls).toBe(1);
  expect(requestedPath).toBe(
    `/api/corporate-groups/${group.corporateGroupId}/risk-assessment`,
  );
  await expect(
    page.getByLabel("Select credit request for assessment"),
  ).toHaveValue(String(selected.id));
  await expect(
    page.getByRole("button", { name: "Run risk prediction", exact: true }),
  ).toBeEnabled();
});

test("A group assessment API failure offers retry without inventing results", async ({
  page,
}) => {
  const { selected } = await setup(page);
  await page.route("**/api/corporate-groups/*/risk-assessment", (route) =>
    route.fulfill({
      status: 502,
      json: { message: "Inference unavailable" },
    }),
  );
  await assess(page, selected.id);
  await page
    .getByText("Other portfolio group assessments", { exact: true })
    .click();
  await page
    .getByRole("region", {
      name: "Parallel group risk assessment",
      exact: true,
    })
    .getByRole("button", { name: "Run group assessment", exact: true })
    .click();
  const section = page.getByRole("region", {
    name: "Parallel group risk assessment",
  });
  await expect(section.getByRole("alert")).toContainText(
    "The service could not complete this operation",
  );
  await expect(section.getByRole("table")).toHaveCount(0);
  await expect(
    page
      .getByRole("region", {
        name: "Parallel group risk assessment",
        exact: true,
      })
      .getByRole("button", { name: "Run group assessment", exact: true }),
  ).toBeEnabled();
});

test("Overview keeps three priority requests and no redundant widgets", async ({
  page,
}) => {
  const { data, selected } = await setup(page);
  data.requests.push({
    ...selected,
    id: 9999,
    amount: 1000000,
    counterpartyName: "Visa Inc.",
  });
  await page
    .getByRole("button", { name: "Refresh workspace", exact: true })
    .click();
  const decisions = page.locator(".ro-decision");
  await expect(decisions).toHaveCount(3);
  await expect(decisions.first()).toContainText("Visa Inc.");
  await expect(decisions.first().locator(".company-logo")).toBeVisible();
  await expect(page.locator(".ro-decision-amount").first()).toContainText(
    "BD 1,000,000",
  );
  await expect(page.locator(".request-status-summary")).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "Portfolio watch", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("heading", {
      name: "Credit exposure composition",
      exact: true,
    }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "Risk alerts", exact: true }),
  ).toHaveCount(0);
  await expect(page.locator(".ro-overview-scope")).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "Recent credit activity", exact: true }),
  ).toBeVisible();
  await decisions.first().click();
  await expect(
    page.getByRole("heading", { name: "Risk Analytics", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByLabel("Select credit request for assessment"),
  ).toHaveValue("9999");
});

test("Recent activity uses the four latest recorded updates without duplicating pending decisions", async ({
  page,
}) => {
  const { data, selected } = await setup(page);
  ["RESERVED", "REJECTED", "USED", "CANCELLED", "EXPIRED"].forEach(
    (status, index) => {
      data.requests.push({
        ...selected,
        id: 9100 + index,
        status,
        updatedAt: `2026-11-18T00:0${index}:00`,
        counterpartyName: "Visa Inc.",
      });
    },
  );
  await page
    .getByRole("button", { name: "Refresh workspace", exact: true })
    .click();
  const activity = page.locator(".ro-activity-row");
  await expect(activity).toHaveCount(4);
  await expect(activity.first()).toContainText("CR-009104");
  await expect(activity.last()).toContainText("CR-009101");
  await expect(activity.first().locator("time")).toHaveAttribute(
    "datetime",
    "2026-11-18T00:04:00",
  );
  await expect(activity.first().locator(".company-logo")).toBeVisible();
  await expect(activity.first()).toContainText("Expired");
  await expect(activity.last()).toContainText("Rejected");
  await expect(
    activity.getByText("Pending approval", { exact: true }),
  ).toHaveCount(0);
  const pendingRefs = await page
    .locator(".ro-decision-company small")
    .allTextContents();
  const recentText = await page.locator(".ro-activity-list").innerText();
  pendingRefs.forEach((reference) =>
    expect(recentText).not.toContain(reference),
  );
  await page.getByRole("button", { name: "View all", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Approval queue", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("combobox", { name: "Status", exact: true }),
  ).toHaveValue("");
  await page
    .getByRole("textbox", { name: "Search requests", exact: true })
    .fill("9104");
  await expect(page.getByRole("main")).toContainText("CR-009104");
});

test("Recent credit activity shows an honest empty state when only pending requests exist", async ({
  page,
}) => {
  const { data } = await setup(page);
  data.requests = data.requests.filter(
    (request) => request.status === "PENDING_APPROVAL",
  );
  await page
    .getByRole("button", { name: "Refresh workspace", exact: true })
    .click();
  await expect(
    page.getByRole("heading", {
      name: "No recent credit activity",
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.locator(".ro-activity-row")).toHaveCount(0);
  await expect(page.locator(".ro-decision")).toHaveCount(3);
});

test("Assessment uses one financial section, scoped group results and compact activity", async ({
  page,
}) => {
  const { data, selected } = await setup(page);
  for (let i = 0; i < 6; i++)
    data.requests.push({
      ...selected,
      id: 9200 + i,
      status: "USED",
      createdAt: `2026-11-01T10:0${i}:00`,
    });
  await page
    .getByRole("button", { name: "Refresh workspace", exact: true })
    .click();
  await assess(page, selected.id);
  const header = page.getByRole("region", {
    name: "Company and request summary",
  });
  await expect(header).toContainText(selected.counterpartyName);
  await expect(header.locator(".company-logo")).toHaveCount(1);
  await expect(
    page.getByRole("heading", { name: "Financial Health", exact: true }),
  ).toHaveCount(1);
  await expect(page.locator(".ra-financial-categories dl > div")).toHaveCount(
    6,
  );
  const profile = data.profiles.find(
    (p) => p.counterpartyId === selected.counterpartyId,
  )!;
  await expect(
    page.getByRole("region", { name: "Profitability", exact: true }),
  ).toContainText(`${profile.returnOnAssets}%`);
  await expect(
    page.getByRole("region", { name: "Capital Structure", exact: true }),
  ).toContainText(`${profile.debtToTotalCapitalPercent}%`);
  await expect(
    page.locator(
      ".group-history-grid, .financial-analytics-grid, .assessment-summary-values",
    ),
  ).toHaveCount(0);
  await expect(page.locator(".ra-recent-card .ra-activity-row")).toHaveCount(3);
  await expect(page.locator(".ra-recent-card")).not.toContainText(
    `CR-${String(selected.id).padStart(6, "0")}`,
  );
  await page
    .getByRole("button", { name: "View complete history", exact: true })
    .click();
  await expect(page.locator(".ra-recent-card .ra-activity-row")).toHaveCount(
    data.requests.filter((r) => r.counterpartyId === selected.counterpartyId)
      .length,
  );
  await page.getByRole("button", { name: "Show less", exact: true }).click();
  const membership = data.memberships.find(
    (m) => m.counterpartyId === selected.counterpartyId,
  )!;
  let requestedGroup = "";
  await page.route("**/api/corporate-groups/*/risk-assessment", (route) => {
    requestedGroup = new URL(route.request().url()).pathname;
    return route.fulfill({ json: [] });
  });
  await page
    .getByRole("region", { name: "Company group risk assessment", exact: true })
    .getByRole("button", { name: "Run group assessment", exact: true })
    .click();
  await expect
    .poll(() => requestedGroup)
    .toBe(
      `/api/corporate-groups/${membership.corporateGroupId}/risk-assessment`,
    );
  await expect(
    page
      .getByRole("region", {
        name: "Company group risk assessment",
        exact: true,
      })
      .getByRole("combobox"),
  ).toHaveCount(0);
  await expect(
    page.getByLabel("Select credit request for assessment"),
  ).toHaveValue(String(selected.id));
  await page
    .getByRole("button", { name: "Run risk prediction", exact: true })
    .click();
  for (const width of [1440, 1280, 768, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
    ).toBe(true);
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({
      path: `/private/tmp/lg-risk-analytics-${width}.png`,
      fullPage: true,
    });
  }
});

test("Standalone assessment omits the empty group card and maker cannot decide", async ({
  page,
}) => {
  const { data, selected } = await setup(page);
  data.memberships = data.memberships.filter(
    (m) => m.counterpartyId !== selected.counterpartyId,
  );
  selected.requesterId = 3;
  await page
    .getByRole("button", { name: "Refresh workspace", exact: true })
    .click();
  await assess(page, selected.id);
  await expect(
    page.getByRole("region", { name: "Company and request summary" }),
  ).toContainText("Standalone company");
  await expect(page.locator(".ra-company-group")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Approve & Reserve", exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole("button", { name: "Reject Request", exact: true }),
  ).toBeDisabled();
});

test("Prediction loading, failure, retry and invalid probabilities never fabricate results", async ({
  page,
}) => {
  const { selected } = await setup(page);
  let release: () => void = () => {};
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  let attempt = 0;
  await page.route("**/api/counterparties/*/risk-prediction", async (route) => {
    attempt++;
    if (attempt === 1) {
      await gate;
      return route.fulfill({ status: 503, json: { message: "Unavailable" } });
    }
    return route.fulfill({
      json: {
        probability: attempt === 2 ? null : 0.019891539834388967,
        riskLevel: "LOW_RISK",
        modelVersion: "financial-risk-model-sklearn-1.6.1",
      },
    });
  });
  await assess(page, selected.id);
  await page
    .getByRole("button", { name: "Run risk prediction", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Running model…", exact: true }),
  ).toBeDisabled();
  await expect(page.locator(".probability-plot")).toHaveAttribute(
    "data-risk",
    "unavailable",
  );
  await expect(page.locator(".probability-center strong")).toHaveText("—");
  release();
  await expect(page.locator(".ra-model-card").getByRole("alert")).toContainText(
    "could not complete",
  );
  await page
    .getByRole("button", { name: "Run risk prediction", exact: true })
    .click();
  await expect(page.locator(".ra-model-card").getByRole("alert")).toContainText(
    "unsupported prediction",
  );
  await expect(
    page.getByRole("group", { name: "Model not yet run", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Run risk prediction", exact: true })
    .click();
  await expect(
    page.getByRole("group", {
      name: "Class 1 probability: 1.99 percent",
      exact: true,
    }),
  ).toBeVisible();
  await expect(page.locator(".ra-model-card")).toContainText(
    "not a verified probability of default",
  );
});

for (const [classification, tone, color] of [
  ["LOW_RISK", "low", "rgb(20, 125, 104)"],
  ["MEDIUM_RISK", "medium", "rgb(156, 107, 33)"],
  ["HIGH_RISK", "high", "rgb(169, 80, 93)"],
] as const) {
  test(`ML gauge uses returned ${classification} styling without changing probability`, async ({
    page,
  }) => {
    const { selected } = await setup(page);
    const probability = 0.019891539834388967;
    // Identical probabilities prove that color follows the API classification, not a frontend threshold.
    await page.route("**/api/counterparties/*/risk-prediction", (route) =>
      route.fulfill({
        json: {
          probability,
          riskLevel: classification,
          modelVersion: "fixture-model-version",
        },
      }),
    );
    await assess(page, selected.id);
    await page
      .getByRole("button", { name: "Run risk prediction", exact: true })
      .click();
    const gauge = page.getByRole("group", {
      name: "Class 1 probability: 1.99 percent",
      exact: true,
    });
    await expect(gauge).toHaveAttribute("data-risk", tone);
    await expect(gauge).toHaveAttribute(
      "data-probability",
      String(probability),
    );
    await expect(gauge).toHaveAttribute(
      "title",
      `Model-returned class-1 probability: ${probability}`,
    );
    await expect(gauge.locator(".probability-center strong")).toHaveText(
      "1.99%",
    );
    await expect(gauge.locator(".probability-center strong")).toHaveCSS(
      "color",
      color,
    );
    const badge = page.locator(
      `.ra-model-output .badge.${classification.toLowerCase()}`,
    );
    await expect(badge).toBeVisible();
    const badgeStyle = await badge.evaluate((node) => ({
      background: getComputedStyle(node).backgroundColor,
      border: getComputedStyle(node).borderColor,
    }));
    expect(badgeStyle.background).not.toBe("rgba(0, 0, 0, 0)");
    expect(badgeStyle.border).not.toBe("rgba(0, 0, 0, 0)");
    await expect(page.locator(".ra-model-card")).toContainText(
      "not a verified probability of default",
    );
    await expect(page.locator(".ra-model-metadata")).toContainText(
      "fixture-model-version",
    );
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 1000 });
      await expect(gauge).toBeVisible();
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth + 1,
        ),
      ).toBe(true);
      await page
        .locator(".ra-model-card")
        .screenshot({ path: `/private/tmp/lg-ml-card-${tone}-${width}.png` });
    }
  });
}
