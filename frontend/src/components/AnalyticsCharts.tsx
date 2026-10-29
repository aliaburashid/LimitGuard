import { useState, type ReactNode } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ArrowUpRight, Info } from "lucide-react";
import type { Exposure, GroupExposure, FinancialProfile } from "../types";
import { bhd, compact, percent, reconciles } from "../analytics/metrics";
export const CHART_COLORS = {
  used: "#254d7d",
  reserved: "#7396be",
  available: "#dfe9e6",
  green: "#25785c",
  amber: "#b58029",
  red: "#b65059",
  grid: "#edf0f4",
};
const tooltipStyle = {
  border: "1px solid #dce3ea",
  borderRadius: 8,
  fontSize: 12,
  boxShadow: "0 8px 24px #122c4415",
  padding: "10px 14px",
};
export function ChartCard({
  title,
  subtitle,
  action,
  children,
  className = "",
}: {
  title: string;
  subtitle?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`analytics-card ${className}`}>
      <div className="analytics-card-head">
        <div>
          <h2>{title}</h2>
          {subtitle && <p>{subtitle}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}
function exposureParts(data: Exposure) {
  return [
    { name: "Used", value: Number(data.usedAmount), fill: CHART_COLORS.used },
    {
      name: "Reserved",
      value: Number(data.reservedAmount),
      fill: CHART_COLORS.reserved,
    },
    {
      name: "Available",
      value: Number(data.availableHeadroom),
      fill: CHART_COLORS.available,
    },
  ];
}
export function ExposureDonut({ data }: { data: Exposure }) {
  const [active, setActive] = useState<number | null>(null);
  const parts = exposureParts(data);
  const usage = percent(
    Number(data.usedAmount) + Number(data.reservedAmount),
    Number(data.limitAmount),
  );
  if (!reconciles(data))
    return (
      <div className="data-note warning" role="alert">
        The exposure values do not reconcile to the approved limit. Refresh the
        data before using this chart.
      </div>
    );
  return (
    <div className="exposure-donut-layout">
      <div
        className="donut-plot"
        role="group"
        aria-label="Credit exposure composition chart"
      >
        <ResponsiveContainer width="100%" height="100%">
          <PieChart accessibilityLayer>
            <Pie
              data={parts}
              dataKey="value"
              nameKey="name"
              innerRadius="70%"
              outerRadius="90%"
              startAngle={90}
              endAngle={-270}
              paddingAngle={2}
              stroke="none"
              isAnimationActive={false}
              onMouseEnter={(_, index) => setActive(index)}
              onMouseLeave={() => setActive(null)}
            >
              {parts.map((p, index) => (
                <Cell
                  key={p.name}
                  fill={p.fill}
                  opacity={active === null || active === index ? 1 : 0.45}
                />
              ))}
            </Pie>
            <Tooltip
              formatter={(value) => bhd(Number(value))}
              contentStyle={tooltipStyle}
              itemStyle={{ color: "#4b6079" }}
              labelStyle={{ color: "#344d68" }}
            />
          </PieChart>
        </ResponsiveContainer>
        <div className="donut-center" aria-hidden="true">
          <strong>
            {usage?.toFixed(1) ?? "—"}
            <small>%</small>
          </strong>
          <span>Committed</span>
        </div>
      </div>
      <div className="chart-key">
        {parts.map((p, index) => (
          <button
            key={p.name}
            type="button"
            className="chart-key-item"
            onMouseEnter={() => setActive(index)}
            onMouseLeave={() => setActive(null)}
            onFocus={() => setActive(index)}
            onBlur={() => setActive(null)}
            aria-label={`${p.name}: ${bhd(p.value)}, ${percent(p.value, data.limitAmount)?.toFixed(1)} percent of capacity`}
          >
            <i style={{ background: p.fill }} />
            <span>
              {p.name}
              <strong>{bhd(p.value)}</strong>
            </span>
            <em>{percent(p.value, data.limitAmount)?.toFixed(1)}%</em>
          </button>
        ))}
      </div>
    </div>
  );
}
export function ExposureStack({ data }: { data: Exposure }) {
  if (!reconciles(data))
    return (
      <div className="data-note warning" role="alert">
        Individual capacity values are inconsistent. Refresh before deciding.
      </div>
    );
  const usage = percent(
    Number(data.usedAmount) + Number(data.reservedAmount),
    Number(data.limitAmount),
  );
  return (
    <>
      <div className="capacity-headline">
        <strong>
          {usage?.toFixed(1) ?? "—"}% <small>utilized</small>
        </strong>
        <span>{bhd(data.limitAmount)} approved limit</span>
      </div>
      <div
        className="capacity-stack"
        role="group"
        aria-label="Individual exposure stacked bar"
      >
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={[
              {
                name: "Facility",
                Used: Number(data.usedAmount),
                Reserved: Number(data.reservedAmount),
                Available: Number(data.availableHeadroom),
              },
            ]}
            layout="vertical"
            margin={{ left: 0, right: 0, top: 0, bottom: 0 }}
            accessibilityLayer
          >
            <XAxis hide type="number" domain={[0, Number(data.limitAmount)]} />
            <YAxis hide type="category" dataKey="name" />
            <Tooltip
              formatter={(v) => bhd(Number(v))}
              contentStyle={tooltipStyle}
              itemStyle={{ color: "#4b6079" }}
              labelStyle={{ color: "#344d68" }}
            />
            {["Used", "Reserved", "Available"].map((key, index) => (
              <Bar
                key={key}
                dataKey={key}
                stackId="capacity"
                fill={
                  [
                    CHART_COLORS.used,
                    CHART_COLORS.reserved,
                    CHART_COLORS.available,
                  ][index]
                }
                isAnimationActive={false}
                barSize={24}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="capacity-values">
        {exposureParts(data).map((p) => (
          <div key={p.name}>
            <span>
              <i style={{ background: p.fill }} />
              {p.name}
            </span>
            <strong>{bhd(p.value)}</strong>
            <small>
              {percent(p.value, data.limitAmount)?.toFixed(1)}% of limit
            </small>
          </div>
        ))}
      </div>
    </>
  );
}
export function GroupComparison({
  groups,
  onSelect,
}: {
  groups: GroupExposure[];
  onSelect?: () => void;
}) {
  const [order, setOrder] = useState("utilization");
  const rows = [...groups]
    .sort((a, b) =>
      order === "exposure"
        ? b.exposure - a.exposure
        : b.exposure / Number(b.amount) - a.exposure / Number(a.amount),
    )
    .slice(0, 6)
    .map((g) => ({
      name: g.corporateGroupName,
      Committed: Number(g.exposure),
      Available: Number(g.headroom),
      limit: Number(g.amount),
    }));
  return (
    <>
      <div className="chart-toolbar">
        <div className="mini-legend">
          <span>
            <i style={{ background: CHART_COLORS.used }} />
            Committed
          </span>
          <span>
            <i style={{ background: CHART_COLORS.available }} />
            Available
          </span>
        </div>
        <label>
          <span className="sr-only">Group chart order</span>
          <select value={order} onChange={(e) => setOrder(e.target.value)}>
            <option value="utilization">Highest utilization</option>
            <option value="exposure">Largest exposure</option>
          </select>
        </label>
      </div>
      <div
        className="group-comparison-plot"
        role="group"
        aria-label="Corporate group exposure against approved credit limits"
      >
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={rows}
            layout="vertical"
            margin={{ left: 0, right: 12, top: 8, bottom: 0 }}
            barSize={14}
            accessibilityLayer
          >
            <CartesianGrid
              horizontal={false}
              stroke={CHART_COLORS.grid}
              strokeDasharray="3 3"
            />
            <XAxis
              type="number"
              axisLine={false}
              tickLine={false}
              tick={{ fontSize: 10, fill: "#8994a3" }}
              tickFormatter={(v) => compact(Number(v))}
            />
            <YAxis
              type="category"
              dataKey="name"
              width={172}
              axisLine={false}
              tickLine={false}
              tick={{ fontSize: 10, fill: "#58677a" }}
              tickFormatter={(v) =>
                String(v).length > 28 ? String(v).slice(0, 27) + "…" : String(v)
              }
            />
            <Tooltip
              labelFormatter={(value, payload) =>
                String(payload?.[0]?.payload?.fullName ?? value)
              }
              formatter={(v) => bhd(Number(v))}
              contentStyle={tooltipStyle}
              itemStyle={{ color: "#4b6079" }}
              labelStyle={{ color: "#344d68" }}
            />
            <Bar
              dataKey="Committed"
              stackId="group"
              fill={CHART_COLORS.used}
              isAnimationActive={false}
            />
            <Bar
              dataKey="Available"
              stackId="group"
              fill={CHART_COLORS.available}
              radius={[0, 3, 3, 0]}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <div className="chart-footer">
        <span>
          Approved group capacity · BHD · Top {rows.length} of {groups.length}
        </span>
        {onSelect && (
          <button className="text-button" onClick={onSelect}>
            All limits
            <ArrowUpRight size={13} />
          </button>
        )}
      </div>
    </>
  );
}
export function ProbabilityGauge({
  probability,
  risk,
}: {
  probability: number | null;
  risk: string | null;
}) {
  const color =
    risk === "HIGH_RISK"
      ? CHART_COLORS.red
      : risk === "MEDIUM_RISK"
        ? CHART_COLORS.amber
        : CHART_COLORS.green;
  const data =
    probability === null
      ? [{ name: "Not run", value: 1, fill: "#e8edf2" }]
      : [
          { name: "Class 1 probability", value: probability, fill: color },
          {
            name: "Remaining probability",
            value: 1 - probability,
            fill: "#e8edf2",
          },
        ];
  return (
    <div
      className="probability-plot"
      role="group"
      aria-label={
        probability === null
          ? "Model not yet run"
          : `Class 1 probability: ${(probability * 100).toFixed(2)} percent`
      }
    >
      <ResponsiveContainer width="100%" height="100%">
        <PieChart accessibilityLayer>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius="80%"
            outerRadius="94%"
            startAngle={90}
            endAngle={-270}
            stroke="none"
            isAnimationActive={false}
          >
            {data.map((p) => (
              <Cell key={p.name} fill={p.fill} />
            ))}
          </Pie>
          {probability !== null && (
            <Tooltip
              formatter={(v) => `${(Number(v) * 100).toFixed(2)}%`}
              contentStyle={tooltipStyle}
              itemStyle={{ color: "#4b6079" }}
              labelStyle={{ color: "#344d68" }}
            />
          )}
        </PieChart>
      </ResponsiveContainer>
      <div className="probability-center">
        <strong style={{ color: probability === null ? "#8391a2" : color }}>
          {probability === null ? "—" : (probability * 100).toFixed(2)}
          {probability !== null && <small>%</small>}
        </strong>
        <span>Class 1 probability</span>
      </div>
    </div>
  );
}
export function ProfitabilityChart({ profile }: { profile: FinancialProfile }) {
  const rows = [
    { name: "Return on assets", value: Number(profile.returnOnAssets) },
    { name: "Net income margin", value: Number(profile.profitMargin) },
  ];
  return (
    <div
      className="profitability-plot"
      role="group"
      aria-label="Return on assets and net income margin, in percent"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={rows}
          layout="vertical"
          margin={{ left: 0, right: 24, top: 6, bottom: 0 }}
          accessibilityLayer
        >
          <CartesianGrid horizontal={false} stroke={CHART_COLORS.grid} />
          <XAxis
            type="number"
            tickFormatter={(v) => `${v}%`}
            axisLine={false}
            tickLine={false}
            tick={{ fontSize: 10, fill: "#8994a3" }}
          />
          <YAxis
            type="category"
            dataKey="name"
            width={110}
            axisLine={false}
            tickLine={false}
            tick={{ fontSize: 10, fill: "#637186" }}
          />
          <ReferenceLine x={0} stroke="#aebac8" />
          <Tooltip
            formatter={(v) => `${Number(v).toLocaleString("en-GB")}%`}
            contentStyle={tooltipStyle}
            itemStyle={{ color: "#4b6079" }}
            labelStyle={{ color: "#344d68" }}
          />
          <Bar
            dataKey="value"
            name="Reported ratio"
            barSize={15}
            radius={[0, 3, 3, 0]}
            isAnimationActive={false}
          >
            {rows.map((r, index) => (
              <Cell
                key={r.name}
                fill={
                  r.value < 0
                    ? CHART_COLORS.red
                    : [CHART_COLORS.used, "#649d92"][index]
                }
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
export function RatioChart({
  name,
  value,
  unit = "×",
}: {
  name: string;
  value: number;
  unit?: string;
}) {
  const magnitude = Math.pow(
    10,
    Math.floor(Math.log10(Math.max(Math.abs(value), 0.1))),
  );
  const bound = Math.max(
    1,
    ([1, 2, 5, 10].find((step) => step * magnitude >= Math.abs(value)) ?? 10) *
      magnitude,
  );
  return (
    <div className="ratio-row">
      <div>
        <span>{name}</span>
        <strong>
          {value.toLocaleString("en-GB", { maximumFractionDigits: 2 })}
          {unit}
        </strong>
      </div>
      <div
        className="ratio-mini-plot"
        role="group"
        aria-label={`${name}: ${value}${unit}, individual zero-based scale`}
      >
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={[{ name, value }]}
            layout="vertical"
            margin={{ left: 0, right: 1, top: 0, bottom: 0 }}
            accessibilityLayer
          >
            <XAxis
              hide
              type="number"
              domain={[value < 0 ? -bound : 0, value < 0 ? 0 : bound]}
            />
            <YAxis hide type="category" dataKey="name" />
            <ReferenceLine x={0} stroke="#acb7c5" />
            <Tooltip
              formatter={(v) => `${Number(v).toLocaleString("en-GB")}${unit}`}
              contentStyle={tooltipStyle}
              itemStyle={{ color: "#4b6079" }}
              labelStyle={{ color: "#344d68" }}
            />
            <Bar
              dataKey="value"
              name={name}
              fill={value < 0 ? CHART_COLORS.red : CHART_COLORS.used}
              background={{ fill: "#f0f3f7", radius: 3 }}
              barSize={6}
              radius={3}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <small>
        {value < 0 ? "−" : "0 → "}
        {bound.toLocaleString("en-GB", { maximumFractionDigits: 1 })}
        {value < 0 ? " → 0" : ""}
        {unit}
      </small>
    </div>
  );
}
export function DebtCapitalChart({ value }: { value: number }) {
  const data = [
    { name: "Debt share", value, fill: CHART_COLORS.used },
    { name: "Remaining capital share", value: 100 - value, fill: "#e8edf2" },
  ];
  return (
    <div
      className="debt-capital-chart"
      role="group"
      aria-label={`Debt to total capital: ${value} percent`}
    >
      <ResponsiveContainer width="100%" height="100%">
        <PieChart accessibilityLayer>
          <Pie
            data={data}
            dataKey="value"
            innerRadius="74%"
            outerRadius="95%"
            startAngle={90}
            endAngle={-270}
            stroke="none"
            isAnimationActive={false}
          >
            {data.map((d) => (
              <Cell key={d.name} fill={d.fill} />
            ))}
          </Pie>
          <Tooltip
            formatter={(v) => `${v}%`}
            contentStyle={tooltipStyle}
            itemStyle={{ color: "#4b6079" }}
            labelStyle={{ color: "#344d68" }}
          />
        </PieChart>
      </ResponsiveContainer>
      <div className="donut-center">
        <strong>
          {value}
          <small>%</small>
        </strong>
        <span>Debt / capital</span>
      </div>
    </div>
  );
}
export function ContributionChart({
  rows,
}: {
  rows: { name: string; amount: number }[];
}) {
  return (
    <div
      className="contribution-plot"
      role="group"
      aria-label="Company contributions to committed group exposure"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={[...rows].sort((a, b) => b.amount - a.amount).slice(0, 6)}
          layout="vertical"
          margin={{ left: 0, right: 16, top: 5, bottom: 0 }}
          accessibilityLayer
        >
          <CartesianGrid horizontal={false} stroke={CHART_COLORS.grid} />
          <XAxis
            type="number"
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => compact(Number(v))}
            tick={{ fontSize: 10 }}
          />
          <YAxis
            dataKey="name"
            type="category"
            axisLine={false}
            tickLine={false}
            width={160}
            tick={{ fontSize: 10 }}
            tickFormatter={(v) => {
              const s = String(v);
              return s.length > 24 ? s.slice(0, 23) + "…" : s;
            }}
          />
          <Tooltip
            formatter={(v) => bhd(Number(v))}
            contentStyle={tooltipStyle}
            itemStyle={{ color: "#4b6079" }}
            labelStyle={{ color: "#344d68" }}
          />
          <Bar
            dataKey="amount"
            name="Committed exposure"
            fill="#507fa2"
            barSize={15}
            radius={[0, 3, 3, 0]}
            isAnimationActive={false}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
export function DataNote({ children }: { children: ReactNode }) {
  return (
    <div className="data-note">
      <Info size={15} />
      <span>{children}</span>
    </div>
  );
}
