import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";

const paged = (content: unknown[]) => ({
  content,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  number: 0,
});
const timestamps = {
  createdAt: "2026-11-01T09:00:00",
  updatedAt: "2026-11-01T09:00:00",
};
async function signIn(
  page: Page,
  options: { shared?: boolean; capacityFailure?: boolean } = {},
) {
  const state = {
    companies: [
      {
        id: 12,
        name: "Synthetic Trading Limited",
        status: "ACTIVE",
        ...timestamps,
      },
    ],
    groups: [{ id: 3, name: "Synthetic Holdings", ...timestamps }],
    members: [] as {
      id: number;
      corporateGroupId: number;
      counterpartyId: number;
      corporateGroupName: string;
    }[],
    facilities: [
      {
        exposure: {
          creditLimitId: 8,
          counterpartyId: 12,
          limitAmount: 2000000,
          usedAmount: 850000,
          reservedAmount: 400000,
          availableHeadroom: 750000,
        },
        name: "Synthetic Trading Limited",
        status: "ACTIVE",
      },
    ],
    limits: [
      {
        id: 4,
        corporateGroupId: 3,
        corporateGroupName: "Synthetic Holdings",
        amount: 3000000,
      },
    ],
    writes: [] as {
      path: string;
      method: string;
      body: Record<string, unknown> | null;
    }[],
  };
  await page.route("**/api/**", async (route) => {
    const req = route.request();
    if (!new URL(req.url()).pathname.startsWith("/api/")) {
      await route.continue();
      return;
    }
    const path = new URL(req.url()).pathname.replace(/^\/api/, "");
    const method = req.method();
    if (process.env.LG_RO_DISPOSABLE === "1") {
      // The entire browser API surface is redirected to disposable services, never port 8080.
      await route.continue({
        url: `http://127.0.0.1:18080/api${path}${new URL(req.url()).search}`,
      });
      return;
    }
    const body = req.postData()
      ? (req.postDataJSON() as Record<string, unknown>)
      : null;
    if (method !== "GET") state.writes.push({ path, method, body });
    const json = (data: unknown, status = 200) =>
      route.fulfill({ status, json: data });
    if (path.endsWith("/login")) return json({ message: "fixture-only-token" });
    if (path.endsWith("/profile"))
      return json({
        id: 1,
        firstName: "Test",
        lastName: "Officer",
        email: "officer@example.test",
        role: "RISK_OFFICER",
        financialInstitution: { id: 3, name: "Synthetic Institution" },
      });
    if (path.endsWith("/events"))
      return route.fulfill({
        contentType: "text/event-stream",
        body: ": connected\n\n",
      });
    if (path === "/counterparties" && method === "GET")
      return json(paged(state.companies));
    if (path === "/corporate-groups" && method === "GET")
      return json(paged(state.groups));
    if (path === "/corporate-group-memberships" && method === "GET")
      return json(state.members);
    if (path === "/credit-limits" && method === "GET")
      return options.capacityFailure
        ? json({ message: "Unavailable" }, 503)
        : json(paged(state.facilities));
    if (path === "/group-credit-limits" && method === "GET")
      return json(paged(state.limits));
    if (/\/group-credit-limits\/groups\/\d+\/exposure$/.test(path))
      return json(1250000);
    if (/\/group-credit-limits\/groups\/\d+\/headroom$/.test(path))
      return json(1750000);
    if (path.includes("financial-profile"))
      return json({
        counterpartyId: 12,
        counterpartyName: state.companies[0].name,
        returnOnAssets: 9.2,
        profitMargin: 14.8,
        fixedAssetTurnover: 2.1,
        debtToTotalCapitalPercent: 28,
        interestCoverage: 8.4,
        totalDebtToEbitda: 1.6,
        financialYearEnd: "2025-12-31",
        updatedAt: timestamps.updatedAt,
      });
    if (
      options.shared &&
      method !== "GET" &&
      path.startsWith("/counterparties/")
    )
      return json({ message: "Shared record" }, 403);
    if (path === "/counterparties" && method === "POST") {
      const row = {
        id: 13,
        name: String(body!.name),
        status: "ACTIVE",
        ...timestamps,
      };
      state.companies.push(row);
      return json(row, 201);
    }
    if (/^\/counterparties\/\d+$/.test(path) && method === "PUT") {
      state.companies[0].name = String(body!.name);
      return json(state.companies[0]);
    }
    if (path.endsWith("/status")) {
      state.companies[0].status = String(body!.status);
      return json(state.companies[0]);
    }
    if (path === "/corporate-groups" && method === "POST") {
      const row = { id: 5, name: String(body!.name), ...timestamps };
      state.groups.push(row);
      return json(row, 201);
    }
    if (/^\/corporate-groups\/\d+$/.test(path) && method === "PUT") {
      const row = state.groups.find(
        (g) => g.id === Number(path.split("/").pop()),
      )!;
      row.name = String(body!.name);
      return json(row);
    }
    if (path === "/corporate-group-memberships" && method === "POST") {
      const row = {
        id: 9,
        corporateGroupId: Number(body!.corporateGroupId),
        counterpartyId: Number(body!.counterpartyId),
        corporateGroupName: state.groups.find(
          (g) => g.id === Number(body!.corporateGroupId),
        )!.name,
      };
      state.members.push(row);
      return json(row, 201);
    }
    if (
      path.startsWith("/corporate-group-memberships/") &&
      method === "DELETE"
    ) {
      state.members = [];
      return route.fulfill({ status: 204 });
    }
    if (path === "/credit-limits/8" && method === "PUT") {
      if (Number(body!.limitAmount) < 1250000)
        return json(
          {
            message:
              "Credit limit cannot be less than used and reserved exposure.",
          },
          400,
        );
      state.facilities[0].exposure.limitAmount = Number(body!.limitAmount);
      state.facilities[0].exposure.availableHeadroom =
        Number(body!.limitAmount) - 1250000;
      return json(state.facilities[0].exposure);
    }
    if (path.startsWith("/group-credit-limits/") && method === "PUT") {
      state.limits[0].amount = Number(body!.limitAmount);
      return json(state.limits[0]);
    }
    if (path.includes("credit-requests")) return json(paged([]));
    if (path.includes("exposure-history") || path.includes("notifications"))
      return json([]);
    return json({});
  });
  await page.goto("/login");
  let password = "fixture-only-password";
  if (process.env.LG_RO_DISPOSABLE === "1")
    password = JSON.parse(
      readFileSync(process.env.LG_RO_TEST_PASSWORD_FILE!, "utf8"),
    ).TEST_PASSWORD;
  await page.getByLabel("Work email").fill("officer@example.test");
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in securely" }).click();
  await expect(
    page.getByRole("heading", { name: "Credit Portfolio Overview" }),
  ).toBeVisible();
  await page
    .getByRole("navigation")
    .getByRole("button", { name: "Counterparties", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Counterparty Management" }),
  ).toBeVisible();
  return state;
}
const tab = (page: Page, name: string) =>
  page
    .getByRole("group", { name: "Counterparty views" })
    .getByRole("button", { name, exact: true });
async function confirm(
  page: Page,
  title: string,
  values: Record<string, string> = {},
) {
  const dialog = page.getByRole("dialog");
  for (const [label, value] of Object.entries(values))
    await dialog.getByLabel(label, { exact: true }).fill(value);
  await dialog
    .getByRole("button", {
      name: `Confirm ${title.toLowerCase()}`,
      exact: true,
    })
    .click();
  await expect(dialog).toBeHidden();
}

test("RO edits companies, manages explicit membership and centralizes limits", async ({
  page,
}) => {
  const state = await signIn(page);
  await page
    .getByRole("button", { name: "New counterparty", exact: true })
    .click();
  await confirm(page, "Create counterparty", {
    "Counterparty name": "New Synthetic Company",
  });
  await expect(
    page.getByRole("button", {
      name: "View New Synthetic Company",
      exact: true,
    }),
  ).toBeVisible();
  await page
    .getByRole("button", {
      name: "View Synthetic Trading Limited",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Edit name", exact: true }).click();
  await confirm(page, "Update counterparty", {
    "Counterparty name": "Renamed Synthetic Trading",
  });
  await expect(
    page.getByRole("heading", {
      name: "Renamed Synthetic Trading",
      exact: true,
    }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Change status", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByLabel("New status")
    .selectOption("FROZEN");
  await confirm(page, "Change counterparty status");
  await expect(page.getByText("Frozen", { exact: true }).first()).toBeVisible();
  await page
    .getByRole("button", { name: "Edit Financial Profile", exact: true })
    .click();
  await expect(
    page.getByRole("dialog").getByLabel("Financial year end", { exact: true }),
  ).toHaveValue("2025-12-31");
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "Save financial profile" })
    .click();
  await expect(page.getByRole("dialog")).toBeHidden();
  await tab(page, "Corporate Groups").click();
  await page
    .getByRole("button", { name: "Create corporate group", exact: true })
    .click();
  await confirm(page, "Create corporate group", {
    "Group name": "New Synthetic Holdings",
  });
  await page
    .getByRole("button", {
      name: "View members of New Synthetic Holdings",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Rename group", exact: true }).click();
  await confirm(page, "Rename corporate group", {
    "Group name": "Renamed Synthetic Holdings",
  });
  await page.getByRole("button", { name: "Add company", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByLabel("Existing company")
    .selectOption("13");
  await confirm(page, "Add company");
  await expect(
    page.getByRole("heading", { name: "New Synthetic Company", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", {
      name: /^(Create|Update) (credit facility|shared limit)$/,
    }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Remove company", exact: true })
    .click();
  await confirm(page, "Remove company");
  await expect(
    page.getByText("No member companies", { exact: true }),
  ).toBeVisible();
  await tab(page, "Credit Facilities").click();
  await page
    .getByRole("region", { name: "Individual Credit Facilities" })
    .getByLabel("Counterparty", { exact: true })
    .selectOption("12");
  await page
    .getByRole("button", { name: "Update credit facility", exact: true })
    .click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Approved limit (BHD)").fill("1000000");
  await dialog
    .getByRole("button", { name: "Confirm update credit facility" })
    .click();
  await expect(dialog).toContainText("Credit limit cannot be less");
  await confirm(page, "Update credit facility", {
    "Approved limit (BHD)": "2100000",
  });
  await page
    .getByRole("region", { name: "Corporate Group Shared Limits" })
    .getByLabel("Corporate group", { exact: true })
    .selectOption("3");
  await page
    .getByRole("button", { name: "Update shared limit", exact: true })
    .click();
  await confirm(page, "Update shared limit", {
    "Approved limit (BHD)": "3200000",
  });
  expect(
    state.writes.some(
      (row) =>
        row.path === "/credit-limits/8" && row.body?.limitAmount === "2100000",
    ),
  ).toBe(true);
  expect(state.members).toHaveLength(0);
  await expect(
    page.getByText(/Facility #|Counterparty #|Credit-limit ID/),
  ).toHaveCount(0);
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth + 1,
      ),
    ).toBe(true);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.mouse.click(1100, 130);
    await page.screenshot({
      path: `/private/tmp/lg-ro-facilities-${width}.png`,
      fullPage: true,
    });
  }
});

test("shared-record rejection stays visible and prevents repeated submissions", async ({
  page,
}) => {
  await signIn(page, { shared: true });
  await page
    .getByRole("button", {
      name: "View Synthetic Trading Limited",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Edit name", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByLabel("Counterparty name")
    .fill("Blocked change");
  await page
    .getByRole("button", { name: "Confirm update counterparty", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toContainText(
    "This record may be shared across institutions",
  );
  await expect(
    page.getByRole("button", {
      name: "Confirm update counterparty",
      exact: true,
    }),
  ).toBeDisabled();
});

test("capacity failure never offers an unsafe create operation", async ({
  page,
}) => {
  await signIn(page, { capacityFailure: true });
  await tab(page, "Credit Facilities").click();
  await page
    .getByRole("region", { name: "Individual Credit Facilities" })
    .getByLabel("Counterparty", { exact: true })
    .selectOption("12");
  await expect(
    page.getByRole("button", { name: "Create credit facility", exact: true }),
  ).toBeDisabled();
  await expect(page.getByText(/Credit capacity unavailable/)).toBeVisible();
});

test("disposable services accept company, profile, group and facility operations", async ({
  page,
}) => {
  test.skip(
    process.env.LG_RO_DISPOSABLE !== "1",
    "Requires the dedicated disposable services on port 18080.",
  );
  test.setTimeout(90000);
  if (!process.env.LG_RO_TEST_PASSWORD_FILE)
    throw new Error(
      "Set LG_RO_TEST_PASSWORD_FILE to a local disposable test credentials JSON file.",
    );
  const writes: { path: string; status: number }[] = [];
  page.on("response", (response) => {
    if (
      new URL(response.url()).pathname.startsWith("/api/") &&
      response.request().method() !== "GET"
    )
      writes.push({
        path: new URL(response.url()).pathname,
        status: response.status(),
      });
  });
  const suffix = Date.now().toString();
  const company = `Workspace Verification ${suffix}`;
  const renamed = `Workspace Verified ${suffix}`;
  const group = `Workspace Holdings ${suffix}`;
  await signIn(page);
  await page
    .getByRole("button", { name: "New counterparty", exact: true })
    .click();
  await confirm(page, "Create counterparty", { "Counterparty name": company });
  await page
    .getByRole("button", { name: `View ${company}`, exact: true })
    .click();
  await page.getByRole("button", { name: "Edit name", exact: true }).click();
  await confirm(page, "Update counterparty", { "Counterparty name": renamed });
  await page
    .getByRole("button", { name: "Change status", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByLabel("New status")
    .selectOption("FROZEN");
  await confirm(page, "Change counterparty status");
  await expect(page.getByText("Frozen", { exact: true }).first()).toBeVisible();
  await page
    .getByRole("button", { name: "Change status", exact: true })
    .click();
  await page
    .getByRole("dialog")
    .getByLabel("New status")
    .selectOption("ACTIVE");
  await confirm(page, "Change counterparty status");
  await page
    .getByRole("button", { name: "Edit Financial Profile", exact: true })
    .click();
  const profile = page.getByRole("dialog");
  for (const [name, value] of Object.entries({
    "Return on assets (%)": "6",
    "Profit margin (%)": "11",
    "Fixed asset turnover (×)": "1.8",
    "Debt to total capital (%)": "35",
    "Interest coverage (×)": "7",
    "Total debt / EBITDA (×)": "2.1",
    "Financial year end": "2025-12-31",
  }))
    await profile.getByLabel(name, { exact: true }).fill(value);
  await profile
    .getByRole("button", { name: "Save financial profile", exact: true })
    .click();
  await expect(profile).toBeHidden();
  await tab(page, "Credit Facilities").click();
  await page
    .getByRole("region", { name: "Individual Credit Facilities" })
    .getByLabel("Counterparty", { exact: true })
    .selectOption({ label: renamed });
  await page
    .getByRole("button", { name: "Create credit facility", exact: true })
    .click();
  await confirm(page, "Create credit facility", {
    "Approved limit (BHD)": "1000000",
  });
  await page
    .getByRole("button", { name: "Update credit facility", exact: true })
    .click();
  await confirm(page, "Update credit facility", {
    "Approved limit (BHD)": "1100000",
  });
  await tab(page, "Corporate Groups").click();
  await page
    .getByRole("button", { name: "Create corporate group", exact: true })
    .click();
  await confirm(page, "Create corporate group", { "Group name": group });
  await page
    .getByRole("button", { name: `View members of ${group}`, exact: true })
    .click();
  await page.getByRole("button", { name: "Rename group", exact: true }).click();
  await confirm(page, "Rename corporate group", {
    "Group name": `${group} Verified`,
  });
  await page.getByRole("button", { name: "Add company", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByLabel("Existing company")
    .selectOption({ label: renamed });
  await confirm(page, "Add company");
  await expect(
    page.getByRole("heading", { name: renamed, exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "View Credit Facilities", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Create shared limit", exact: true })
    .click();
  await confirm(page, "Create shared limit", {
    "Approved limit (BHD)": "2000000",
  });
  await page
    .getByRole("button", { name: "Update shared limit", exact: true })
    .click();
  await confirm(page, "Update shared limit", {
    "Approved limit (BHD)": "2200000",
  });
  await tab(page, "Corporate Groups").click();
  await page
    .getByRole("button", {
      name: `View members of ${group} Verified`,
      exact: true,
    })
    .click();
  await page
    .getByRole("button", { name: "Remove company", exact: true })
    .click();
  await confirm(page, "Remove company");
  await expect(
    page.getByText("No member companies", { exact: true }),
  ).toBeVisible();
  expect(writes.length).toBeGreaterThanOrEqual(13);
  expect(writes.every((row) => row.status >= 200 && row.status < 300)).toBe(
    true,
  );
});
