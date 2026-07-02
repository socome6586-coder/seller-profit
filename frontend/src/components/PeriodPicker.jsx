import { useMemo } from "react";

// 공용 기간 선택 컴포넌트. 대시보드/광고ROI 양쪽에서 재사용(docs/period-picker-tasks.md T9).
// 프리셋 계산은 KST(Asia/Seoul) 기준 "오늘"을 기준으로 한다. 서버 데이터가 KST 기준이므로 일관성 유지.
//
// props:
//   value: { from, to, preset } — from/to 는 "yyyy-MM-dd" 문자열, preset 은 PRESET 키(직접 선택 시 "custom")
//   onChange(next): 프리셋/직접선택 값이 바뀔 때 호출. next 는 value 와 동일한 shape.
//   maxRangeDays: 플랜의 조회기간 한도(예: FREE=30). null/undefined/-1 이면 무제한.
//   disabled: 전체 비활성화(계정 미선택 등)

const PRESET_LABELS = {
  today: "오늘",
  thisWeek: "이번 주",
  thisMonth: "이번 달",
  lastMonth: "지난 달",
  last7: "최근 7일",
  last30: "최근 30일",
  custom: "직접 선택",
};

export const PRESET_ORDER = ["today", "thisWeek", "thisMonth", "lastMonth", "last7", "last30", "custom"];

/** 오늘을 KST 기준 달력 날짜로 스냅샷해 "지역 무관 순수 Date"로 만든다. toISOString() 은 UTC 로 되돌아가므로 쓰지 않는다. */
export function kstToday() {
  const iso = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date()); // yyyy-mm-dd
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y, m - 1, d);
}

function fmt(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function addDays(date, n) {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return d;
}

function startOfWeekMonday(date) {
  const day = date.getDay(); // 0=일 ... 6=토
  const diff = day === 0 ? -6 : 1 - day;
  return addDays(date, diff);
}

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function endOfLastMonth(date) {
  return addDays(startOfMonth(date), -1);
}

function startOfLastMonth(date) {
  return startOfMonth(endOfLastMonth(date));
}

/** preset -> {from,to} Date 객체(내부 계산용). "직접 선택"은 파생 범위가 없으므로 null. */
function computeRangeDates(preset, today) {
  switch (preset) {
    case "today":
      return { from: today, to: today };
    case "thisWeek":
      return { from: startOfWeekMonday(today), to: today };
    case "thisMonth":
      return { from: startOfMonth(today), to: today };
    case "lastMonth": {
      const eol = endOfLastMonth(today);
      return { from: startOfMonth(eol), to: eol };
    }
    case "last7":
      return { from: addDays(today, -6), to: today };
    case "last30":
      return { from: addDays(today, -29), to: today };
    default:
      return null;
  }
}

/** preset -> {from,to} "yyyy-MM-dd" 문자열. Dashboard/AdRoi 가 초기 상태를 세팅할 때도 재사용한다. */
export function computeRange(preset, today = kstToday()) {
  const range = computeRangeDates(preset, today);
  if (!range) return null;
  return { from: fmt(range.from), to: fmt(range.to) };
}

/** 이 프리셋의 범위가 플랜 한도를 넘는지. "오늘 - (한도-1)"보다 이른 시작일이면 초과(서버 게이팅과 동일 기준). */
function exceedsLimit(preset, today, maxRangeDays) {
  if (maxRangeDays == null || Number(maxRangeDays) < 0) return false;
  const range = computeRangeDates(preset, today);
  if (!range) return false; // 직접 선택은 달력 단계(9.2/9.3)에서 별도로 막는다.
  const earliestAllowed = addDays(today, -(Number(maxRangeDays) - 1));
  return range.from < earliestAllowed;
}

export default function PeriodPicker({ value, onChange, maxRangeDays, disabled }) {
  const today = useMemo(() => kstToday(), []);
  const preset = value?.preset || "thisMonth";
  const isCustom = preset === "custom";

  function selectPreset(p) {
    if (disabled) return;
    if (p === "custom") {
      // 직접 선택 진입: 기존 from/to 가 있으면 유지, 없으면 이번 달을 시작값으로.
      const seed = value?.from && value?.to ? { from: value.from, to: value.to } : computeRange("thisMonth", today);
      onChange({ ...seed, preset: "custom" });
      return;
    }
    if (exceedsLimit(p, today, maxRangeDays)) return;
    onChange({ ...computeRange(p, today), preset: p });
  }

  function updateCustom(field, raw) {
    const next = { from: value?.from || "", to: value?.to || "", preset: "custom", [field]: raw };
    onChange(next);
  }

  const customInvalid = isCustom && value?.from && value?.to && value.from > value.to;

  return (
    <div className="period-picker">
      <div className="pp-chips" role="group" aria-label="조회 기간 선택">
        {PRESET_ORDER.map((p) => {
          const active = preset === p;
          const limited = p !== "custom" && exceedsLimit(p, today, maxRangeDays);
          return (
            <button
              key={p}
              type="button"
              className={"pp-chip" + (active ? " active" : "") + (limited ? " limited" : "")}
              aria-pressed={active}
              disabled={disabled || limited}
              title={limited ? `현재 플랜은 최근 ${maxRangeDays}일까지 조회할 수 있어요 · PRO로 업그레이드` : undefined}
              onClick={() => selectPreset(p)}
            >
              {PRESET_LABELS[p]}
            </button>
          );
        })}
      </div>

      {isCustom ? (
        <div className="pp-custom">
          <div className="field">
            <label>시작일</label>
            <input
              type="date"
              value={value?.from || ""}
              disabled={disabled}
              onChange={(e) => updateCustom("from", e.target.value)}
            />
          </div>
          <div className="field">
            <label>종료일</label>
            <input
              type="date"
              value={value?.to || ""}
              disabled={disabled}
              onChange={(e) => updateCustom("to", e.target.value)}
            />
          </div>
          {customInvalid ? <div className="pp-warn">시작일이 종료일보다 늦을 수 없습니다.</div> : null}
        </div>
      ) : null}

      {maxRangeDays != null && Number(maxRangeDays) >= 0 ? (
        <div className="pp-limit-note">현재 플랜은 최근 {maxRangeDays}일까지 조회할 수 있어요 · PRO로 업그레이드</div>
      ) : null}
    </div>
  );
}
