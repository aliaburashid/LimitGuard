import { useEffect, useState } from "react";
import { api } from "../api/client";
import {
  facilityDirectory,
  groupPortfolio,
  memberships,
  requestDirectory,
} from "../api/portfolio";
import { facilityReferences, type Facility } from "../analytics/metrics";
import type { GroupExposure, Membership, Request } from "../types";
export function usePortfolio(version: number, risk = true, enabled = true) {
  const [requests, setRequests] = useState<Request[] | null>(null);
  const [groups, setGroups] = useState<GroupExposure[] | null>(null);
  const [members, setMembers] = useState<Membership[] | null>(null);
  const [facilities, setFacilities] = useState<Facility[] | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [refreshing, setRefreshing] = useState(false);
  useEffect(() => {
    if (!enabled) return;
    const c = new AbortController();
    setErrors([]);
    setRefreshing(true);
    const capture = (e: Error) => {
      if (!c.signal.aborted) setErrors((old) => [...old, e.message]);
    };
    const groupLoad = groupPortfolio(c.signal)
      .then((values) => {
        if (!c.signal.aborted) setGroups(values);
      })
      .catch(capture);
    const memberLoad = memberships(c.signal)
      .then((values) => {
        if (!c.signal.aborted) setMembers(values);
      })
      .catch(capture);
    const requestLoad = requestDirectory(risk, c.signal)
      .then(async (values) => {
        if (c.signal.aborted) return;
        setRequests(values);
        if (!risk) return;
        const refs = facilityReferences(values);
        const result = await Promise.all(
          refs.map(async (ref) => ({
            ...(await api<Facility>(
              `/credit-limits/${ref.id}/exposure`,
              "GET",
              undefined,
              c.signal,
            )),
            name: ref.name,
            status: ref.status,
          })),
        );
        if (!c.signal.aborted) setFacilities(result);
      })
      .catch((e) => {
        if (risk && !c.signal.aborted) setFacilities(null);
        capture(e);
      });
    const facilityLoad = risk
      ? Promise.resolve()
      : facilityDirectory(c.signal)
          .then((values) => {
            if (!c.signal.aborted) setFacilities(values);
          })
          .catch((error: Error) => {
            if (!c.signal.aborted) setFacilities(null);
            capture(error);
          });
    void Promise.all([
      groupLoad,
      memberLoad,
      requestLoad,
      facilityLoad,
    ]).finally(() => {
      if (!c.signal.aborted) setRefreshing(false);
    });
    return () => c.abort();
  }, [version, risk, enabled]);
  return { requests, groups, members, facilities, errors, refreshing };
}
