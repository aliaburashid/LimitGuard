import { test, expect, type Browser, type Page } from "@playwright/test";

type Role = "ADMIN" | "RISK_OFFICER" | "RELATIONSHIP_MANAGER";
const paged = (content: unknown[]) => ({
  content,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
  number: 0,
});
function groupServer() {
  const companies = [
    { id: 3, name: "Gulf Meridian Logistics W.L.L.", status: "ACTIVE" },
    { id: 4, name: "Pearl Coast Shipping Co.", status: "FROZEN" },
    { id: 5, name: "Falcon Freight Services", status: "ACTIVE" },
    { id: 6, name: "Bahrain Gateway Warehousing", status: "ACTIVE" },
    { id: 8, name: "Unassigned Technology", status: "ACTIVE" },
  ];
  const groups = [
    { id: 3, name: "Gulf Investment Group" },
    { id: 4, name: "Empty Holdings" },
  ];
  const members = companies.slice(0, 4).map((c, i) => ({
    id: i + 1,
    corporateGroupId: 3,
    corporateGroupName: "Gulf Investment Group",
    counterpartyId: c.id,
  }));
  const limits = [
    {
      id: 2,
      corporateGroupId: 3,
      corporateGroupName: "Gulf Investment Group",
      financialInstitutionId: 5,
      amount: 2000000,
    },
  ];
  const facilities = [
    {
      creditLimitId: 37,
      counterpartyId: 3,
      limitAmount: 750000,
      usedAmount: 0,
      reservedAmount: 675000,
    },
    {
      creditLimitId: 39,
      counterpartyId: 4,
      limitAmount: 1250000,
      usedAmount: 0,
      reservedAmount: 0,
    },
    {
      creditLimitId: 79,
      counterpartyId: 5,
      limitAmount: 1000000,
      usedAmount: 300000,
      reservedAmount: 0,
    },
    {
      creditLimitId: 80,
      counterpartyId: 6,
      limitAmount: 1000000,
      usedAmount: 225000,
      reservedAmount: 0,
    },
  ];
  const history = facilities.map((f, i) => {
    const c = companies.find((x) => x.id === f.counterpartyId)!;
    return {
      id: 400 + i,
      amount: f.usedAmount || 600000,
      status:
        c.status === "FROZEN"
          ? "REJECTED"
          : f.reservedAmount
            ? "RESERVED"
            : "USED",
      creditLimitId: f.creditLimitId,
      counterpartyId: c.id,
      counterpartyName: c.name,
      counterpartyStatus: c.status,
      requesterId: c.id === 6 ? 58 : 15,
      requesterName: "Relationship Manager",
      financialInstitutionId: 5,
      financialInstitutionName: "Citi",
      createdAt: "2026-10-22T10:00:00",
      updatedAt: "2026-10-22T12:00:00",
      expiresAt: null,
    };
  });
  const writes: { path: string; body: Record<string, unknown>; role: Role }[] =
    [];
  const forbiddenSearches: Role[] = [];
  const outcomes: {
    id: number;
    amount: number;
    status: string;
    creditLimitId: number;
  }[] = [];
  const queues = new Map<string, string[]>();
  const exposure = (id: number) =>
    facilities
      .filter((f) =>
        members.some(
          (m) =>
            m.corporateGroupId === id && m.counterpartyId === f.counterpartyId,
        ),
      )
      .reduce((sum, f) => sum + f.usedAmount + f.reservedAmount, 0);
  async function login(browser: Browser, role: Role, manager: 15 | 58 = 15) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const token = `test-${role}-${manager}`;
    queues.set(token, []);
    await page.route("**/api/**", async (route) => {
      const path = new URL(route.request().url()).pathname.replace(
        /^\/api/,
        "",
      );
      const method = route.request().method();
      const response = async (json: unknown, status = 200) =>
        route.fulfill({ json, status });
      if (path === "/auth/users/login") return response({ message: token });
      if (path === "/auth/users/profile")
        return response({
          id:
            role === "RELATIONSHIP_MANAGER"
              ? manager
              : role === "ADMIN"
                ? 17
                : 23,
          firstName: "Test",
          lastName: "Employee",
          email: "employee@example.test",
          role,
          financialInstitution: { id: 5, name: "Citi" },
          profilePicturePath: null,
        });
      if (path === "/credit-requests/events") {
        const events = queues.get(token)!.splice(0);
        return route.fulfill({
          contentType: "text/event-stream",
          body: ":connected\n\n" + events.join(""),
        });
      }
      if (method === "GET") {
        if (path === "/corporate-groups") return response(paged(groups));
        if (path === "/counterparties") return response(paged(companies));
        if (/^\/counterparties\/\d+$/.test(path))
          return response(
            companies.find((c) => c.id === Number(path.split("/")[2])),
          );
        if (path === "/corporate-group-memberships") return response(members);
        if (path === "/group-credit-limits") return response(paged(limits));
        if (
          /^\/group-credit-limits\/groups\/\d+\/(headroom|exposure)$/.test(path)
        ) {
          const id = Number(path.split("/")[3]);
          const limit = limits.find((g) => g.corporateGroupId === id)!;
          return response(
            path.endsWith("headroom")
              ? limit.amount - exposure(id)
              : exposure(id),
          );
        }
        if (/^\/credit-limits\/\d+\/exposure$/.test(path)) {
          const f = facilities.find(
            (x) => x.creditLimitId === Number(path.split("/")[2]),
          );
          return f
            ? response({
                ...f,
                availableHeadroom:
                  f.limitAmount - f.usedAmount - f.reservedAmount,
              })
            : response({ message: "Credit limit not found" }, 404);
        }
        if (path === "/credit-requests/search") {
          if (role !== "RISK_OFFICER") {
            forbiddenSearches.push(role);
            return response({ message: "Forbidden" }, 403);
          }
          return response(paged(history));
        }
        if (path === "/credit-requests/my")
          return response(
            paged(history.filter((r) => r.requesterId === manager)),
          );
        if (path === "/credit-requests/pending") return response(paged([]));
        return response({ message: "No fixture for this read" }, 404);
      }
      const body = route.request().postDataJSON() as Record<string, unknown>;
      writes.push({ path, body, role });
      if (path === "/credit-requests" && method === "POST") {
        if (role !== "RELATIONSHIP_MANAGER")
          return response({ message: "Forbidden" }, 403);
        const f = facilities.find(
          (x) => x.creditLimitId === body.creditLimitId,
        )!;
        const amount = Number(body.amount);
        const group = members.find(
          (m) => m.counterpartyId === f.counterpartyId,
        )!.corporateGroupId;
        const shared = limits.find((g) => g.corporateGroupId === group)!;
        if (amount > f.limitAmount - f.usedAmount - f.reservedAmount)
          return response(
            { message: "Requested amount exceeds available headroom" },
            400,
          );
        if (amount > shared.amount - exposure(group))
          return response(
            {
              message:
                "Requested amount exceeds corporate group available headroom",
            },
            400,
          );
        f.reservedAmount += amount;
        const created = {
          id: 500 + outcomes.length,
          amount,
          status: "RESERVED",
          creditLimitId: f.creditLimitId,
        };
        outcomes.push(created);
        for (const queue of queues.values())
          queue.push(
            `event:credit-request-status\ndata:${JSON.stringify({ creditRequestId: created.id, status: "RESERVED" })}\n\n`,
          );
        return response(created, 201);
      }
      if (role === "RELATIONSHIP_MANAGER")
        return response({ message: "Forbidden" }, 403);
      if (path === "/corporate-groups" && method === "POST") {
        if (groups.some((g) => g.name === body.name))
          return response({ message: "Corporate group already exists" }, 409);
        const group = { id: 10 + groups.length, name: String(body.name) };
        groups.push(group);
        return response(group, 201);
      }
      if (path === "/corporate-group-memberships" && method === "POST") {
        if (members.some((m) => m.counterpartyId === body.counterpartyId))
          return response(
            { message: "counterparty already belongs to a corporate group" },
            409,
          );
        const group = groups.find((g) => g.id === body.corporateGroupId)!;
        const member = {
          id: 10 + members.length,
          corporateGroupId: group.id,
          corporateGroupName: group.name,
          counterpartyId: Number(body.counterpartyId),
        };
        members.push(member);
        return response(member, 201);
      }
      if (path === "/group-credit-limits" && method === "POST") {
        const group = groups.find((g) => g.id === body.corporateGroupId)!;
        const limit = {
          id: 10 + limits.length,
          corporateGroupId: group.id,
          corporateGroupName: group.name,
          financialInstitutionId: 5,
          amount: Number(body.limitAmount),
        };
        limits.push(limit);
        return response(limit, 201);
      }
      if (/^\/group-credit-limits\/\d+$/.test(path) && method === "PUT") {
        const limit = limits.find((g) => g.id === Number(path.split("/")[2]))!;
        if (Number(body.limitAmount) < exposure(limit.corporateGroupId))
          return response(
            {
              message:
                "Group credit limit cannot be lower than current group exposure",
            },
            400,
          );
        limit.amount = Number(body.limitAmount);
        return response(limit);
      }
      return response({ message: "Unsupported mutation" }, 400);
    });
    await page.goto("/");
    await page.getByLabel("Work email").fill("employee@example.test");
    await page
      .getByLabel("Password", { exact: true })
      .fill("mock-session-only");
    await page.getByRole("button", { name: "Sign in securely" }).click();
    await page
      .getByRole("navigation", { name: "Main navigation" })
      .getByRole("button", { name: "Limits & exposure", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Credit exposure", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByLabel("Corporate group", { exact: true }),
    ).toHaveValue("3");
    await expect(
      page
        .getByRole("table", { name: "Corporate group companies" })
        .getByRole("row"),
    ).toHaveCount(5);
    return { page, context };
  }
  return {
    login,
    writes,
    forbiddenSearches,
    groups,
    members,
    limits,
    facilities,
    outcomes,
    exposure,
  };
}

for (const role of ["ADMIN", "RISK_OFFICER"] as const) {
  test(`${role} creates a group, adds an existing company and sets the institution's shared limit`, async ({
    browser,
  }) => {
    const server = groupServer();
    const { page, context } = await server.login(browser, role);
    expect(server.writes).toHaveLength(0);
    await page
      .getByRole("button", { name: "Create corporate group", exact: true })
      .click();
    await page
      .getByLabel("Group name", { exact: true })
      .fill("Presentation Ventures");
    await page
      .getByRole("button", { name: "Confirm create corporate group" })
      .click();
    await expect(
      page.getByLabel("Corporate group", { exact: true }),
    ).toHaveValue("12");
    await page
      .getByRole("button", { name: "Add company", exact: true })
      .click();
    await expect(
      page
        .getByLabel("Existing company")
        .getByRole("option", { name: "Falcon Freight Services" }),
    ).toHaveCount(0);
    await page.getByLabel("Existing company").selectOption("8");
    await page.getByRole("button", { name: "Confirm add company" }).click();
    await expect(
      page
        .getByRole("table")
        .getByText("Unassigned Technology", { exact: true }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Set shared limit", exact: true })
      .click();
    await page.getByLabel("Shared limit amount (BHD)").fill("800000");
    await page
      .getByRole("button", { name: "Confirm set shared limit" })
      .click();
    await expect(
      page
        .locator(".corporate-management .group-card")
        .getByText("BD 800,000", { exact: true }),
    ).toHaveCount(2);
    expect(server.writes.map((x) => ({ path: x.path, body: x.body }))).toEqual([
      { path: "/corporate-groups", body: { name: "Presentation Ventures" } },
      {
        path: "/corporate-group-memberships",
        body: { corporateGroupId: 12, counterpartyId: 8 },
      },
      {
        path: "/group-credit-limits",
        body: { corporateGroupId: 12, limitAmount: "800000" },
      },
    ]);
    expect(server.forbiddenSearches).toEqual([]);
    await context.close();
  });
}

test("Risk Officer sees every member's individual capacity and backend rejects an excessive reduction", async ({
  browser,
}) => {
  const server = groupServer();
  const { page, context } = await server.login(browser, "RISK_OFFICER");
  const row = page
    .getByRole("row")
    .filter({ hasText: "Falcon Freight Services" });
  await expect(row).toContainText("BD 1,000,000");
  await expect(row).toContainText("BD 300,000");
  await expect(row).toContainText("BD 700,000");
  await page.setViewportSize({ width: 390, height: 844 });
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBe(true);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page
    .getByRole("button", { name: "Update shared limit", exact: true })
    .click();
  await page.getByLabel("Shared limit amount (BHD)").fill("1000000");
  await page
    .getByRole("button", { name: "Confirm update shared limit" })
    .click();
  await expect(page.getByRole("dialog").getByRole("alert")).toHaveText(
    "Group credit limit cannot be lower than current group exposure",
  );
  expect(server.limits[0].amount).toBe(2000000);
  await expect(page.getByRole("dialog")).toBeVisible();
  await context.close();
});

test("Admin resolves a facility in the UI without requesting forbidden RO history", async ({
  browser,
}) => {
  const server = groupServer();
  const { page, context } = await server.login(browser, "ADMIN");
  const row = page
    .getByRole("row")
    .filter({ hasText: "Falcon Freight Services" });
  await expect(row).toContainText("Individual facility details are not loaded");
  await page
    .getByRole("button", { name: "Find facility for Falcon Freight Services" })
    .click();
  await page.getByLabel("Credit-limit ID", { exact: true }).fill("79");
  await page.getByLabel("Credit-limit ID", { exact: true }).press("Enter");
  await expect(row).toContainText("BD 700,000");
  await page
    .getByRole("button", {
      name: "Find facility for Bahrain Gateway Warehousing",
    })
    .click();
  await page.getByLabel("Credit-limit ID", { exact: true }).fill("79");
  await page.getByLabel("Credit-limit ID", { exact: true }).press("Enter");
  await expect(page.getByRole("alert")).toContainText(
    "This facility belongs to a different company",
  );
  expect(server.forbiddenSearches).toEqual([]);
  expect(server.writes).toEqual([]);
  await context.close();
});

test("Duplicate group errors stay visible without fake success or extra records", async ({
  browser,
}) => {
  const server = groupServer();
  const { page, context } = await server.login(browser, "RISK_OFFICER");
  await page
    .getByRole("button", { name: "Create corporate group", exact: true })
    .click();
  await page
    .getByLabel("Group name", { exact: true })
    .fill("Gulf Investment Group");
  await page
    .getByRole("button", { name: "Confirm create corporate group" })
    .click();
  await expect(page.getByRole("dialog").getByRole("alert")).toHaveText(
    "Corporate group already exists",
  );
  expect(server.groups).toHaveLength(2);
  await expect(page.getByRole("dialog")).toBeVisible();
  await context.close();
});

test("Two RM browser sessions submit competing subsidiary requests and immediately see shared headroom", async ({
  browser,
}) => {
  const server = groupServer();
  const first = await server.login(browser, "RELATIONSHIP_MANAGER", 15);
  const second = await server.login(browser, "RELATIONSHIP_MANAGER", 58);
  await expect(
    first.page
      .getByRole("row")
      .filter({ hasText: "Pearl Coast Shipping Co." })
      .getByRole("button", { name: "Request credit", exact: true }),
  ).toBeDisabled();
  for (const { page } of [first, second]) {
    await expect(
      page.getByRole("button", { name: "Create corporate group", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Add company", exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: "Update shared limit", exact: true }),
    ).toHaveCount(0);
    await expect(
      page
        .locator(".corporate-management .group-card")
        .getByText("BD 800,000", { exact: true }),
    ).toBeVisible();
  }
  for (const [page, company] of [
    [first.page, "Falcon Freight Services"],
    [second.page, "Bahrain Gateway Warehousing"],
  ] as [Page, string][]) {
    await page
      .getByRole("row")
      .filter({ hasText: company })
      .getByRole("button", { name: "Request credit", exact: true })
      .click();
    await page.getByLabel("Requested amount (BHD)").fill("450000");
    await expect(
      page.getByRole("dialog").getByText("BD 800,000", { exact: true }),
    ).toBeVisible();
  }
  await Promise.all(
    [first.page, second.page].map((p) =>
      p.getByRole("button", { name: "Confirm create credit request" }).click(),
    ),
  );
  await expect.poll(() => server.outcomes.length).toBe(1);
  expect(
    server.writes
      .filter((w) => w.path === "/credit-requests")
      .map((w) => w.body.creditLimitId)
      .sort(),
  ).toEqual([79, 80]);
  expect(server.exposure(3)).toBe(1650000);
  const loser =
    server.outcomes[0].creditLimitId === 79 ? second.page : first.page;
  await expect(loser.getByRole("dialog").getByRole("alert")).toHaveText(
    "Requested amount exceeds corporate group available headroom",
  );
  await expect(
    loser.getByRole("dialog").getByText("BD 350,000", { exact: true }),
  ).toBeVisible();
  for (const { page } of [first, second])
    await expect(
      page
        .locator(".corporate-management .group-card")
        .first()
        .getByText("BD 350,000", { exact: true }),
    ).toBeVisible();
  expect(server.forbiddenSearches).toEqual([]);
  await first.context.close();
  await second.context.close();
});
