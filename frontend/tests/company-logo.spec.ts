import { expect, test } from "@playwright/test";
import { createServer, type ViteDevServer } from "vite";

let server: ViteDevServer;
let origin: string;

test.beforeAll(async () => {
  server = await createServer({
    configFile: false,
    define: {
      "import.meta.env.VITE_BRANDFETCH_CLIENT_ID":
        JSON.stringify("logo-test-only"),
    },
    esbuild: { jsx: "automatic" },
    optimizeDeps: {
      include: [
        "react",
        "react-dom/client",
        "react/jsx-runtime",
        "react/jsx-dev-runtime",
      ],
    },
    server: { host: "127.0.0.1", port: 0 },
    plugins: [
      {
        name: "company-logo-test-fixture",
        resolveId(id) {
          if (id === "/company-logo-fixture.js") return id;
        },
        load(id) {
          if (id !== "/company-logo-fixture.js") return;
          return `
            import { createElement } from "react";
            import { createRoot } from "react-dom/client";
            import { CompanyLogo, CorporateGroupLogo } from "/src/components/CompanyLogo.tsx";
            import { counterpartyLogoDomain, institutionLogoDomain } from "/src/config/verifiedLogoDomains.ts";
            const citi = { id: 5, name: "Citi" };
            const params = new URLSearchParams(location.search);
            const company = params.get("company");
            const group = params.get("group");
            const name = company ?? citi.name;
            const domain = company ? counterpartyLogoDomain(company) : institutionLogoDomain(citi);
            createRoot(document.querySelector("#root")).render(
              group ? createElement(CorporateGroupLogo, { name: group }) :
                createElement(CompanyLogo, { name, domain, size: company ? "small" : "large" })
            );
          `;
        },
      },
    ],
  });
  await server.listen(0);
  const address = server.httpServer?.address();
  if (!address || typeof address === "string")
    throw new Error("Logo fixture server has no HTTP port");
  origin = `http://127.0.0.1:${address.port}`;
});

test.afterAll(async () => {
  await server?.close();
});

test.beforeEach(async ({ page }) => {
  await page.route("**/company-logo-fixture?*", (route) =>
    route.fulfill({
      contentType: "text/html",
      body: '<div id="root"></div><script type="module" src="/company-logo-fixture.js"></script>',
    }),
  );
});

test("loads the verified Citi logo inside the large badge", async ({
  page,
}) => {
  await page.route("https://cdn.brandfetch.io/**", (route) =>
    route.fulfill({
      contentType: "image/svg+xml",
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"><rect width="20" height="20" fill="blue"/></svg>',
    }),
  );

  await page.goto(`${origin}/company-logo-fixture?logo`);
  const image = page.locator(".company-logo-large img");
  await expect(image).toHaveAttribute(
    "src",
    "https://cdn.brandfetch.io/domain/citi.com/w/88/h/88/fallback/404?c=logo-test-only",
  );
  await expect(image).toHaveJSProperty("naturalWidth", 20);
  await expect(page.locator(".company-logo")).toHaveCSS("width", "44px");
});

test("uses the operating domain for each exact counterparty name", async ({
  page,
}) => {
  await page.route("https://cdn.brandfetch.io/**", (route) =>
    route.fulfill({
      contentType: "image/svg+xml",
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"/>',
    }),
  );

  const companies = [
    ["Accenture plc", "accenture.com"],
    ["Adobe Inc.", "adobe.com"],
    ["Airbnb, Inc.", "airbnb.com"],
    ["Amazon Web Services, Inc.", "aws.amazon.com"],
    ["Amazon.com Services LLC", "amazon.com"],
    ["Hyundai Motor Company", "hyundai.com"],
    ["Hyundai Mobis Co., Ltd.", "mobis.co.kr"],
    ["Lufthansa Cargo AG", "lufthansa-cargo.com"],
    ["Lufthansa Technik AG", "lufthansa-technik.com"],
    ["Tata Consultancy Services Limited", "tcs.com"],
  ] as const;
  for (const [name, domain] of companies) {
    await page.goto(
      `${origin}/company-logo-fixture?company=${encodeURIComponent(name)}`,
    );
    const image = page.locator(".company-logo img");
    await expect(image).toHaveAttribute(
      "src",
      `https://cdn.brandfetch.io/domain/${domain}/w/68/h/68/fallback/404?c=logo-test-only`,
    );
    await expect(image).toHaveJSProperty("naturalWidth", 20);
    await expect(page.locator(".company-logo")).toHaveCSS("width", "34px");
  }
});

test("uses initials without requesting a logo when no domain is supplied", async ({
  page,
}) => {
  const requests: string[] = [];
  await page.route("https://cdn.brandfetch.io/**", (route) => {
    requests.push(route.request().url());
    return route.abort();
  });

  await page.goto(`${origin}/company-logo-fixture?company=Audi%20Services`);
  await expect(page.locator(".company-logo")).toHaveText("AS");
  await expect(page.locator(".company-logo img")).toHaveCount(0);
  expect(requests).toEqual([]);
});

test("restores initials when the image fails to load", async ({ page }) => {
  await page.route("https://cdn.brandfetch.io/**", (route) =>
    route.fulfill({ status: 404 }),
  );

  const response = page.waitForResponse("https://cdn.brandfetch.io/**");
  await page.goto(`${origin}/company-logo-fixture?company=AUDI%20AG`);
  const failed = await response;
  expect(failed.status()).toBe(404);
  expect(failed.url()).toContain("/domain/audi.com/w/68/h/68/");
  await expect(page.locator(".company-logo")).toHaveText("AU");
  await expect(page.locator(".company-logo img")).toHaveCount(0);
});

test("requests Disney's corporate wordmark and retains initials for unverified group artwork", async ({
  page,
}) => {
  await page.route("https://cdn.brandfetch.io/**", (route) =>
    route.fulfill({
      contentType: "image/svg+xml",
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"/>',
    }),
  );

  await page.goto(
    `${origin}/company-logo-fixture?group=The%20Walt%20Disney%20Company`,
  );
  await expect(page.locator(".company-logo img")).toHaveAttribute(
    "src",
    "https://cdn.brandfetch.io/domain/thewaltdisneycompany.com/w/68/h/68/fallback/404/logo?c=logo-test-only",
  );

  await page.goto(`${origin}/company-logo-fixture?group=Lufthansa%20Group`);
  await expect(page.locator(".company-logo")).toHaveText("LG");
  await expect(page.locator(".company-logo img")).toHaveCount(0);
});
