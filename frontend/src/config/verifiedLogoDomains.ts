// Exact catalog names identify these reviewed demo counterparties across seed runs.
const counterpartyDomains = new Map([
  ["ABB Ltd", "abb.com"],
  ["ABC, Inc.", "abc.com"],
  ["Accenture plc", "accenture.com"],
  ["Activision Blizzard, Inc.", "activisionblizzard.com"],
  ["Adobe Inc.", "adobe.com"],
  ["Airbnb, Inc.", "airbnb.com"],
  ["Amazon Web Services, Inc.", "aws.amazon.com"],
  ["Amazon.com Services LLC", "amazon.com"],
  ["Apple Inc.", "apple.com"],
  ["AUDI AG", "audi.com"],
  ["Berkshire Hathaway Energy Company", "brkenergy.com"],
  ["BNSF Railway Company", "bnsf.com"],
  ["Broadcom Inc.", "broadcom.com"],
  ["Cisco Systems, Inc.", "cisco.com"],
  ["Costco Wholesale Corporation", "costco.com"],
  ["CrowdStrike Holdings, Inc.", "crowdstrike.com"],
  ["Dell Technologies Inc.", "dell.com"],
  ["Delta Air Lines, Inc.", "delta.com"],
  ["Dr. Ing. h.c. F. Porsche AG", "porsche.com"],
  ["Emirates", "emirates.com"],
  ["ESPN Enterprises, Inc.", "espn.com"],
  ["FedEx Corporation", "fedex.com"],
  ["Frito-Lay North America, Inc.", "fritolay.com"],
  ["Google LLC", "google.com"],
  ["HSBC Holdings plc", "hsbc.com"],
  ["Hyundai Mobis Co., Ltd.", "mobis.co.kr"],
  ["Hyundai Motor Company", "hyundai.com"],
  ["Intuit Inc.", "intuit.com"],
  ["Kia Corporation", "kia.com"],
  ["LinkedIn Corporation", "linkedin.com"],
  ["Lufthansa Cargo AG", "lufthansa-cargo.com"],
  ["Lufthansa Technik AG", "lufthansa-technik.com"],
  ["McLane Company, Inc.", "mclaneco.com"],
  ["Meta Platforms, Inc.", "meta.com"],
  ["Microsoft Ireland Operations Limited", "microsoft.com"],
  ["Pixar Animation Studios", "pixar.com"],
  ["Ring LLC", "ring.com"],
  ["Sabra Dipping Company, LLC", "sabra.com"],
  ["Salesforce, Inc.", "salesforce.com"],
  ["SAP SE", "sap.com"],
  ["ServiceNow, Inc.", "servicenow.com"],
  ["Siemens AG", "siemens.com"],
  ["Snowflake Inc.", "snowflake.com"],
  ["SodaStream International Ltd.", "sodastream.com"],
  ["Spotify Technology S.A.", "spotify.com"],
  ["Tata Consultancy Services Limited", "tcs.com"],
  ["Tata Consumer Products Limited", "tataconsumer.com"],
  ["Tata Steel Limited", "tatasteel.com"],
  ["Tesla, Inc.", "tesla.com"],
  ["The Boeing Company", "boeing.com"],
  ["The Home Depot, Inc.", "homedepot.com"],
  ["Titan Company Limited", "titancompany.in"],
  ["Twitch Interactive, Inc.", "twitch.tv"],
  ["United Parcel Service, Inc.", "ups.com"],
  ["Visa Inc.", "visa.com"],
  ["Walmart Inc.", "walmart.com"],
  ["Waymo LLC", "waymo.com"],
  ["Wing Aviation LLC", "wing.com"],
  ["Škoda Auto a.s.", "skoda-auto.com"],
]);

const corporateGroupDomains = new Map([
  ["Alphabet", "abc.xyz"],
  ["Amazon", "amazon.com"],
  ["Microsoft", "microsoft.com"],
  ["Volkswagen Group", "volkswagen-group.com"],
  ["Berkshire Hathaway", "berkshirehathaway.com"],
  ["The Walt Disney Company", "thewaltdisneycompany.com"],
  ["PepsiCo", "pepsico.com"],
  ["Hyundai Motor Group", "hyundaimotorgroup.com"],
  ["Tata Group", "tata.com"],
  ["Lufthansa Group", "lufthansagroup.com"],
]);

export function corporateGroupLogoDomain(name: string): string | undefined {
  return corporateGroupDomains.get(name);
}

export function counterpartyLogoDomain(name: string): string | undefined {
  return counterpartyDomains.get(name);
}

export function institutionLogoDomain(institution: {
  id: number;
  name: string;
}): string | undefined {
  // The Citi demo portfolio is tied to institution 5.
  return institution.id === 5 && institution.name === "Citi"
    ? "citi.com"
    : undefined;
}
