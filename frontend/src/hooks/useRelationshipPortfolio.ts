import { useEffect, useState } from "react";
import { allPages } from "../api/portfolio";
import { useAuth } from "../context/Auth";
import { usePortfolio } from "./usePortfolio";
import type { Counterparty } from "../types";

// These two approved Citi demo relationships intentionally have no individual or shared limit.
const unfundedCitiDemoCompanies = new Set([
  "North Quay Services W.L.L. (fictional)",
  "Orchid Quay Trading W.L.L. (fictional)",
]);

export function useRelationshipPortfolio(version: number, enabled: boolean) {
  const { user } = useAuth();
  const portfolio = usePortfolio(version, false, enabled);
  const [counterparties, setCounterparties] = useState<Counterparty[] | null>(
    null,
  );
  const [directoryError, setDirectoryError] = useState("");
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    setDirectoryError("");
    void allPages<Counterparty>(
      "/counterparties?sort=name,asc",
      controller.signal,
    )
      .then((rows) => {
        if (!controller.signal.aborted) setCounterparties(rows);
      })
      .catch((error: Error) => {
        if (!controller.signal.aborted) setDirectoryError(error.message);
      });
    return () => controller.abort();
  }, [version, enabled]);
  let individualCounterparties = counterparties;
  if (user?.financialInstitution.id === 5 && counterparties) {
    const { facilities, groups, members, refreshing } = portfolio;
    if (refreshing && (!facilities || !groups || !members)) {
      individualCounterparties = null;
    } else {
      const facilityCompanies = new Set(
        facilities?.map((facility) => facility.counterpartyId),
      );
      const institutionGroups = new Set(
        groups?.map((group) => group.corporateGroupId),
      );
      const groupCompanies = new Set(
        members
          ?.filter((member) => institutionGroups.has(member.corporateGroupId))
          .map((member) => member.counterpartyId),
      );
      individualCounterparties = counterparties.filter(
        ({ id, name }) =>
          facilityCompanies.has(id) ||
          groupCompanies.has(id) ||
          unfundedCitiDemoCompanies.has(name),
      );
    }
  }
  return {
    ...portfolio,
    counterparties,
    individualCounterparties,
    directoryError,
  };
}

export type RelationshipPortfolio = ReturnType<typeof useRelationshipPortfolio>;
