import { useEffect, useId, useState } from "react";
import { ChartNoAxesCombined } from "lucide-react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  useActiveTooltipLabel,
  useChartWidth,
  useIsTooltipActive,
  usePlotArea,
  useXAxisScale,
  useYAxisScale,
  XAxis,
  YAxis,
} from "recharts";
import { api, ApiError } from "../api/client";
import { compact } from "../analytics/metrics";
import { preciseMoney } from "./ExposureHeadroomBar";
import { ErrorBox, Loading } from "./ui";

type Snapshot = {
  capturedAt: string;
  usedAmount: number | string;
  reservedAmount: number | string;
  limitAmount: number | string;
  synthetic?: boolean;
  current?: boolean;
};

const day = 86_400_000;
const ranges = [
  { days: 7, name: "7D", label: "Last 7 days" },
  { days: 30, name: "30D", label: "Last 30 days" },
  { days: 90, name: "90D", label: "Last 90 days" },
  { days: 365, name: "1Y", label: "Last year" },
] as const;

const series = [
  { key: "used", name: "Used exposure" },
  { key: "reserved", name: "Reserved exposure" },
  { key: "approved", name: "Approved capacity" },
] as const;

const shortDate = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  timeZone: "UTC",
});
const snapshotDate = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

const tooltipDate = new Intl.DateTimeFormat("en-GB", {
  dateStyle: "medium",
  timeZone: "UTC",
});
const tooltipTime = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
  timeZone: "UTC",
});

function usedDomain([minimum, maximum]: readonly [number, number]): [
  number,
  number,
] {
  const padding = Math.max((maximum - minimum) * 0.12, maximum * 0.02, 1);
  return [Math.max(0, minimum - padding), maximum + padding];
}

function parseSnapshots(snapshots: Snapshot[]) {
  if (!Array.isArray(snapshots))
    throw new Error("The service returned incomplete exposure history.");

  const dates = new Set<number>();
  return snapshots
    .map((snapshot) => {
      const timestamp = Date.parse(snapshot.capturedAt);
      const amounts = [
        snapshot.usedAmount,
        snapshot.reservedAmount,
        snapshot.limitAmount,
      ];
      if (
        !/Z$/.test(snapshot.capturedAt) ||
        !Number.isFinite(timestamp) ||
        dates.has(timestamp) ||
        amounts.some(
          (amount) =>
            (typeof amount !== "number" &&
              !(typeof amount === "string" && /^\d+(\.\d+)?$/.test(amount))) ||
            !Number.isFinite(Number(amount)) ||
            Number(amount) < 0,
        )
      )
        throw new Error("The service returned incomplete exposure history.");
      dates.add(timestamp);
      const [used, reserved, approved] = amounts.map(Number);
      return {
        timestamp,
        synthetic: snapshot.synthetic === true,
        current: snapshot.current === true,
        used,
        reserved,
        approved,
        headroom: Math.round((approved - used - reserved) * 100) / 100,
      };
    })
    .sort((a, b) => a.timestamp - b.timestamp);
}

type ExposurePoint = ReturnType<typeof parseSnapshots>[number];
function UsedExposureArea({ points }: { points: ExposurePoint[] }) {
  const gradient = useId();
  const scaleX = useXAxisScale();
  const scaleY = useYAxisScale();
  const plot = usePlotArea();
  const label = useActiveTooltipLabel();
  const isActive = useIsTooltipActive();

  if (!scaleX || !scaleY || !plot) return null;

  const bottom = plot.y + plot.height;
  const observations = points.flatMap((point) => {
    const x = scaleX(point.timestamp);
    const y = scaleY(point.used);
    return x === undefined || y === undefined ? [] : [{ ...point, x, y }];
  });
  let line = "";
  let area = "";
  let startX = observations[0]?.x;
  observations.forEach((point, index) => {
    const previous = observations[index - 1];
    // Break the area across missing UTC days. Only observations are interactive.
    if (
      !previous ||
      Math.floor(point.timestamp / day) - Math.floor(previous.timestamp / day) >
        1
    ) {
      if (previous) area += `V${bottom}H${startX}Z`;
      startX = point.x;
      line += `M${point.x},${point.y}`;
      area += `M${point.x},${point.y}`;
    } else {
      line += `L${point.x},${point.y}`;
      area += `L${point.x},${point.y}`;
    }
  });
  if (observations.length) area += `V${bottom}H${startX}Z`;

  const active = isActive
    ? observations.find((point) => point.timestamp === Number(label))
    : undefined;

  return (
    <g aria-hidden="true" pointerEvents="none">
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
          <stop className="rm-trend-gradient" stopOpacity={0.26} />
          <stop className="rm-trend-gradient" offset="100%" stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${gradient})`} />
      <path className="rm-trend-line" d={line} fill="none" />
      {active && (
        <line
          className="rm-trend-guide"
          x1={active.x}
          x2={active.x}
          y1={plot.y}
          y2={bottom}
        />
      )}
      {observations.map((point) => (
        <circle
          key={point.timestamp}
          className="rm-trend-point"
          data-live={point.current}
          cx={point.x}
          cy={point.y}
          r={point === active || point.current ? 4.5 : 2}
        />
      ))}
    </g>
  );
}

function ExposureTooltip({
  point,
  x,
  active,
}: {
  point: ExposurePoint;
  x?: number;
  active: boolean;
}) {
  const width = useChartWidth();

  return (
    <div
      className="rm-trend-tooltip"
      role="status"
      data-active={active}
      data-side={x !== undefined && width && x > width / 2 ? "left" : "right"}
    >
      <div className="rm-trend-tooltip-header">
        <time dateTime={new Date(point.timestamp).toISOString()}>
          {tooltipDate.format(point.timestamp)} ·{" "}
          {tooltipTime.format(point.timestamp)} UTC
        </time>
        {point.current && (
          <span className="rm-trend-tooltip-tag">Live balance</span>
        )}
      </div>
      <dl className="rm-trend-tooltip-metrics">
        {series.slice(0, 2).map(({ key, name }) => (
          <div key={key}>
            <dt>{name}</dt>
            <dd>{preciseMoney(point[key])}</dd>
          </div>
        ))}
        <div className="rm-trend-tooltip-committed">
          <dt>Total committed exposure</dt>
          <dd>{preciseMoney(point.used + point.reserved)}</dd>
        </div>
        <div className="rm-trend-tooltip-headroom">
          <dt>Available headroom</dt>
          <dd>{preciseMoney(point.headroom)}</dd>
        </div>
      </dl>
    </div>
  );
}

export default function RelationshipExposureTrend({
  version,
}: {
  version: number;
}) {
  const title = useId();
  const [history, setHistory] = useState<ReturnType<typeof parseSnapshots>>();
  const [current, setCurrent] = useState<ExposurePoint>();
  const [error, setError] = useState("");
  const [range, setRange] = useState("all");
  const [tapToInspect, setTapToInspect] = useState(false);
  const [tooltipHost, setTooltipHost] = useState<HTMLDivElement | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    // Older servers may still match this path to the credit-limit update route.
    void api<Snapshot[]>(
      "/credit-limits/exposure-history?includeCurrent=true",
      "GET",
      undefined,
      controller.signal,
    )
      .then((snapshots) => {
        if (controller.signal.aborted) return;
        if (!Array.isArray(snapshots))
          throw new Error("The service returned incomplete exposure history.");
        const history = parseSnapshots(
          snapshots.filter((snapshot) => !snapshot.current),
        );
        const [current] = parseSnapshots(
          snapshots.filter((snapshot) => snapshot.current),
        );
        setHistory(history);
        setCurrent(current);
        setError("");
      })
      .catch((error: Error) => {
        if (controller.signal.aborted) return;
        if (
          error instanceof ApiError &&
          (error.status === 404 || error.status === 405)
        ) {
          setHistory([]);
          setCurrent(undefined);
          setError("");
        } else {
          setError(error.message);
        }
      });
    return () => controller.abort();
  }, [version]);

  const now = Date.now();
  const snapshots = history ?? [];
  const available = ranges
    .map((option) => {
      const start = now - option.days * day;
      return {
        ...option,
        start,
        count: snapshots.filter((point) => point.timestamp >= start).length,
      };
    })
    .filter(
      ({ start, count }) =>
        count >= 2 &&
        snapshots[0].timestamp <= start + day &&
        snapshots[snapshots.length - 1].timestamp >= now - 2 * day,
    );
  const selected = available.find((option) => option.name === range);
  const visibleHistory = selected
    ? snapshots.filter((point) => point.timestamp >= selected.start)
    : snapshots;
  const lastSnapshot = visibleHistory.at(-1);
  const latest = current ?? lastSnapshot;
  // The live reading is separate from daily history and only adds a chart point when balances differ.
  const visible =
    current &&
    (!lastSnapshot ||
      (current.timestamp > lastSnapshot.timestamp &&
        series.some(({ key }) => current[key] !== lastSnapshot[key])))
      ? [...visibleHistory, current]
      : visibleHistory;

  return (
    <section className="panel rm-exposure-trend" aria-labelledby={title}>
      <div className="section-head">
        <div>
          <span className="eyebrow">FACILITY HISTORY</span>
          <h2 id={title}>Exposure Trend</h2>
          <p className="muted">Individual facilities in your request history</p>
        </div>
        {available.length > 0 && (
          <div
            className="rm-trend-ranges"
            role="group"
            aria-label="Exposure trend date range"
          >
            {[{ name: "all", label: "All recorded history" }, ...available].map(
              ({ name, label }) => (
                <button
                  key={name}
                  type="button"
                  aria-label={label}
                  aria-pressed={(selected?.name ?? "all") === name}
                  onClick={() => setRange(name)}
                >
                  {name === "all" ? "All" : name}
                </button>
              ),
            )}
          </div>
        )}
      </div>
      {error ? (
        <ErrorBox message={error} />
      ) : !history ? (
        <Loading />
      ) : !latest ? (
        <div className="rm-trend-empty">
          <span className="rm-trend-icon">
            <ChartNoAxesCombined size={23} aria-hidden="true" />
          </span>
          <h3>
            {visible.length
              ? "One snapshot recorded"
              : "Exposure history is not yet available"}
          </h3>
          <p>
            A trend will appear once at least two dated exposure snapshots are
            available.
          </p>
        </div>
      ) : (
        <>
          <div className="rm-trend-inspector">
            <div
              className="rm-trend-summary"
              aria-label="Latest snapshot values"
            >
              <div className="rm-trend-primary">
                <span>Used exposure</span>
                <strong>{preciseMoney(latest.used)}</strong>
              </div>
              <div>
                <span>Reserved exposure</span>
                <strong>{preciseMoney(latest.reserved)}</strong>
              </div>
              <div>
                <span>Approved capacity</span>
                <strong>{preciseMoney(latest.approved)}</strong>
              </div>
            </div>
            {current && (
              <p className="rm-trend-live-status">
                Live balance · {preciseMoney(current.headroom)} available
                headroom
              </p>
            )}
            {visible.length < 2 ? (
              <div className="rm-trend-empty">
                <h3>Exposure history is not yet available</h3>
                <p>
                  The latest balances are shown above. The trend needs two
                  distinct observations.
                </p>
              </div>
            ) : (
              <>
                <div className="rm-trend-axis-heading">
                  <span>Used exposure · BD</span>
                  {visible.at(-1)?.current && (
                    <span className="rm-trend-live-key">Live reading</span>
                  )}
                </div>
                <div className="rm-trend-chart-frame">
                  <div
                    className="rm-trend-plot"
                    data-tap={tapToInspect}
                    onPointerDownCapture={(event) =>
                      setTapToInspect(event.pointerType !== "mouse")
                    }
                    onPointerMoveCapture={(event) => {
                      if (tapToInspect && event.pointerType === "mouse")
                        setTapToInspect(false);
                    }}
                    onKeyDownCapture={() => setTapToInspect(false)}
                  >
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart
                        data={visible}
                        title="Used exposure by snapshot"
                        desc="Use the arrow keys or tap the chart to inspect recorded values."
                        accessibilityLayer
                        margin={{ top: 12, right: 17, bottom: 4, left: 0 }}
                      >
                        <CartesianGrid
                          vertical={false}
                          className="rm-trend-grid"
                        />
                        <XAxis
                          dataKey="timestamp"
                          type="number"
                          domain={["dataMin", "dataMax"]}
                          tickFormatter={(value: number) =>
                            Math.floor(visible[0].timestamp / day) ===
                            Math.floor(
                              visible[visible.length - 1].timestamp / day,
                            )
                              ? tooltipTime.format(value)
                              : shortDate.format(value)
                          }
                          tick={{ fill: "#526784", fontSize: 11 }}
                          minTickGap={18}
                          tickMargin={8}
                          interval="preserveStartEnd"
                          padding={{ left: 5, right: 5 }}
                          axisLine={false}
                          tickLine={false}
                        />
                        <YAxis
                          domain={usedDomain}
                          allowDataOverflow
                          tickFormatter={(value: number) => compact(value)}
                          tick={{ fill: "#526784", fontSize: 11 }}
                          width={49}
                          tickCount={5}
                          axisLine={false}
                          tickLine={false}
                        />
                        {tooltipHost && (
                          <Tooltip
                            portal={tooltipHost}
                            trigger={tapToInspect ? "click" : "hover"}
                            cursor={false}
                            isAnimationActive={false}
                            wrapperStyle={{ visibility: "visible" }}
                            content={({ active, label, coordinate }) => {
                              const hovered =
                                active &&
                                visible.find(
                                  (point) => point.timestamp === Number(label),
                                );
                              const point = hovered || latest;
                              return point ? (
                                <ExposureTooltip
                                  point={point}
                                  x={coordinate?.x}
                                  active={Boolean(hovered)}
                                />
                              ) : null;
                            }}
                          />
                        )}
                        <UsedExposureArea points={visible} />
                        <Line
                          dataKey="used"
                          name="Used exposure"
                          stroke="transparent"
                          isAnimationActive={false}
                          activeDot={false}
                          dot={false}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                  <div
                    className="rm-trend-tooltip-layer"
                    ref={setTooltipHost}
                  />
                </div>
              </>
            )}
          </div>
        </>
      )}
      <div className="rm-trend-footer">
        {latest && !error && history && (
          <details className="rm-trend-data">
            <summary>View snapshot values</summary>
            {visibleHistory.some((point) => point.synthetic) && (
              <p className="scope-note">
                Rows marked synthetic demo are simulated historical values.
              </p>
            )}
            <div className="table-scroll">
              <table aria-label="Exposure snapshots">
                <thead>
                  <tr>
                    <th scope="col">Captured (UTC)</th>
                    {series.map(({ key, name }) => (
                      <th key={key} scope="col">
                        {name}
                      </th>
                    ))}
                    <th scope="col">Available headroom</th>
                  </tr>
                </thead>
                <tbody>
                  {[...visibleHistory, ...(current ? [current] : [])].map(
                    (point) => (
                      <tr key={`${point.timestamp}-${point.current}`}>
                        <th scope="row">
                          {snapshotDate.format(point.timestamp)}
                          {point.synthetic && " (synthetic demo)"}
                          {point.current && " (live reading)"}
                        </th>
                        {series.map(({ key }) => (
                          <td key={key} className="numeric">
                            {preciseMoney(point[key])}
                          </td>
                        ))}
                        <td className="numeric">
                          {preciseMoney(point.headroom)}
                        </td>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>
          </details>
        )}
        <p className="scope-note">
          Shared corporate group limits are shown separately.
        </p>
      </div>
    </section>
  );
}
