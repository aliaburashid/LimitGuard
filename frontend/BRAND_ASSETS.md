# Company branding

LimitGuard can show Brandfetch logos for the revised Citi demo portfolio. Other company and institution badges use initials. The previous presentation assets and manifest are archived locally in `frontend/.local-branding/`. Git ignores this folder and the frontend build does not include it.

## Brandfetch configuration

Set `VITE_BRANDFETCH_CLIENT_ID` in the ignored `frontend/.env.local` file, then restart Vite or rebuild the frontend. Use the public Logo API client ID from the [Brandfetch developer dashboard](https://developers.brandfetch.com/). Each image loads directly from Brandfetch. Missing configuration, unmatched records, and failed images use the initials badge.

The sidebar maps [Citi](https://www.citi.com/) for the existing demo institution. The counterparty directory and detail badges use the [reviewed exact-name mappings](src/config/verifiedLogoDomains.ts), covering all 59 companies in the revised Citi seed catalog. Seed runs can assign different IDs. [AWS](https://aws.amazon.com/) and [Amazon.com Services LLC](https://ship.amazon.com/site-terms) use their respective operating domains. [Microsoft Ireland Operations Limited](https://www.microsoft.com/en-ie/store/b/imprint) is identified on Microsoft's Irish storefront.

Earlier demo databases may still contain legacy names that use initials. The revised fixtures use real company identities with fictional borrowing scenarios and synthetic financial data. Review each brand’s usage terms before enabling its mark in a public deployment.

## Local presentation asset sources

The inventory below uses the checked-in Citi presentation fixture. Logos identify the named entity and do not indicate affiliation or endorsement.

The source pages document the copyright classification of the archived assets. Trademark rights still apply.

### Assets available in the local pack

- AUDI AG · [Audi-Logo 2016.svg](https://commons.wikimedia.org/wiki/File:Audi-Logo_2016.svg)
- BNSF Railway Company · [BNSF logo.svg](https://commons.wikimedia.org/wiki/File:BNSF_logo.svg)
- ESPN Enterprises, Inc. · [ESPN wordmark.svg](https://commons.wikimedia.org/wiki/File:ESPN_wordmark.svg)
- Visa Inc. · [Visa Inc. logo (2021–present).svg](https://commons.wikimedia.org/wiki/File:Visa_Inc._logo_%282021%E2%80%93present%29.svg)
- SAP SE · [SAP 2011 logo.svg](https://commons.wikimedia.org/wiki/File:SAP_2011_logo.svg)
- Dell Technologies Inc. · [Dell Technologies logo.svg](https://commons.wikimedia.org/wiki/File:Dell_Technologies_logo.svg)
- Alphabet · [Alphabet Inc Logo 2015.svg](https://commons.wikimedia.org/wiki/File:Alphabet_Inc_Logo_2015.svg)
- Amazon · [Amazon 2024.svg](https://commons.wikimedia.org/wiki/File:Amazon_2024.svg)
- Berkshire Hathaway · [Berkshire.png](https://commons.wikimedia.org/wiki/File:Berkshire.png)
- The Walt Disney Company · [The Walt Disney Company Logo.svg](https://commons.wikimedia.org/wiki/File:The_Walt_Disney_Company_Logo.svg)

## Initials fallbacks

The sidebar uses the signed-in institution identity from the profile. The Citi website terms remain a separate consideration for public use. [Citi website terms](https://www.citigroup.com/global/terms)

### Brand permissions checked

- Apple, Adobe, and LinkedIn require a license or other express authorization for their corporate logos in this context. [Apple](https://www.apple.com/legal/intellectual-property/guidelinesfor3rdparties.html) · [Adobe](https://www.adobe.com/legal/permissions/trademarks.html) · [LinkedIn](https://brand.linkedin.com/business-books-and-media-usage)
- Google and Microsoft publish permissions for particular contexts. I could not verify that those permissions cover identification in the counterparty directory. [Google](https://about.google/brand-resource-center/guidance/) · [Microsoft](https://www.microsoft.com/en-us/legal/intellectualproperty/trademarks)
- The PepsiCo and Volkswagen newsroom terms limit reuse of their website or press assets. I could not verify a grant covering the dashboard. [PepsiCo](https://www.pepsico.com/en/terms-of-use) · [Volkswagen](https://www.volkswagen-newsroom.com/en/terms-of-service-35)
- Cisco generally withholds third-party use of its corporate logo. Activision Blizzard's site terms reserve permission for its marks. [Cisco](https://www.cisco.com/c/en/us/about/brand-center/logo-usage-guidelines.html) · [Activision Blizzard](https://www.activisionblizzard.com/legal/terms-of-use)

### Corporate groups

- Microsoft
- Volkswagen Group
- PepsiCo
- Gulf Investment Group (Citi Demo)

### Historical list of individual counterparties in the initials-only release

- Google LLC
- XXVI Holdings Inc.
- Alphabet Capital US LLC
- Amazon Web Services, Inc.
- Amazon.com Services LLC
- Amazon Data Services, Inc.
- Amazon.com Sales, Inc.
- LinkedIn Corporation
- Activision Blizzard, Inc.
- Microsoft Ireland Operations Limited
- Dr. Ing. h.c. F. Porsche AG
- Škoda Auto a.s.
- Berkshire Hathaway Energy Company
- McLane Company, Inc.
- ABC, Inc.
- Disney Consumer Products, Inc.
- Pepsi-Cola Metropolitan Bottling Company, Inc.
- Sabra Dipping Company, LLC
- Quaker Manufacturing, LLC
- Falcon Freight Services (Citi Demo)
- Bahrain Gateway Warehousing (Citi Demo)
- Gulf Meridian Logistics W.L.L. (Citi Demo)
- Apple Inc.
- Meta Platforms, Inc.
- Broadcom Inc.
- Tesla, Inc.
- Walmart Inc.
- Cisco Systems, Inc.
- Salesforce, Inc.
- Adobe Inc.
- Accenture plc
- Spotify Technology S.A.
- ServiceNow, Inc.
- Airbnb, Inc.
- Snowflake Inc.
- Harbor Quay Equipment W.L.L. (fictional)
- North Quay Services W.L.L. (fictional)
- Orchid Quay Trading W.L.L. (fictional)
- Intuit Inc.
- CrowdStrike Holdings, Inc.
