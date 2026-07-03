import { useEffect, useMemo, useRef, useState } from "react";
import { DayPicker } from "react-day-picker";
import { ko } from "react-day-picker/locale";
import "react-day-picker/style.css";

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

/** "yyyy-MM-dd" -> 로컬 Date. new Date(str) 은 UTC 로 파싱돼 하루가 밀릴 수 있어 쓰지 않는다. */
function parseYmd(str) {
  if (!str) return undefined;
  const [y, m, d] = str.split("-").map(Number);
  return new Date(y, m - 1, d);
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
  // 달력에서 시작일만 클릭한 "진행 중" 선택. 완성(종료일까지 선택)되기 전엔 부모로 올리지 않는다
  // (조회 API 를 부분 선택 상태로 두 번 부르지 않기 위함).
  const [pendingRange, setPendingRange] = useState(null);
  // 드래그(마우스 누른 채 날짜 위로 끌기) 진행 상태. react-day-picker 는 클릭(같은 날짜에서
  // mousedown+mouseup) 기반 선택만 기본 지원해서, 드래그는 mousedown/mouseenter/mouseup 을
  // 직접 조합해 별도로 구현한다 — DayPicker 의 onSelect(클릭 경로)와는 독립적으로 동작.
  const dragRef = useRef(null); // { start: Date } | null

  useEffect(() => {
    if (!isCustom) setPendingRange(null);
  }, [isCustom]);

  useEffect(() => {
    function onWindowMouseUp() {
      if (!dragRef.current) return;
      dragRef.current = null;
      setPendingRange((pr) => {
        if (pr && pr.from && pr.to) {
          const [from, to] = pr.from <= pr.to ? [pr.from, pr.to] : [pr.to, pr.from];
          onChange({ from: fmt(from), to: fmt(to), preset: "custom" });
          return null;
        }
        return pr;
      });
    }
    window.addEventListener("mouseup", onWindowMouseUp);
    return () => window.removeEventListener("mouseup", onWindowMouseUp);
  }, [onChange]);

  // 달력 카드 전체에서 mousedown 을 위임 처리 — react-day-picker 가 각 날짜 <td> 에 심어주는
  // data-day="yyyy-MM-dd" 속성을 읽어 어떤 날짜에서 드래그가 시작됐는지 알아낸다.
  // 여기서는 시작점만 기록하고 pendingRange 는 아직 건드리지 않는다 — 실제로 마우스가 다른 날짜로
  // 넘어가기 전까지(= 진짜 드래그가 아니라 그냥 클릭인 경우) DayPicker 의 기존 클릭 기반 선택 로직
  // (addToRange, "두 번 클릭해야 시작일 변경") 을 그대로 살려두기 위함. 여기서 바로 setPendingRange
  // 를 호출하면 클릭 한 번마다 선택이 항상 단일 날짜로 리셋돼버리는 회귀가 생긴다.
  function handleCalendarMouseDown(e) {
    if (disabled) return;
    const cell = e.target.closest?.("[data-day]");
    if (!cell || cell.dataset.disabled === "true") return;
    const start = parseYmd(cell.dataset.day);
    if (!start) return;
    dragRef.current = { start };
  }

  // 마우스가 시작 날짜와 다른 날짜 위로 넘어온 시점에야 비로소 "드래그 중"으로 확정하고
  // pendingRange 를 세팅한다(진짜 클릭과 드래그를 가르는 지점).
  function handleDayMouseEnter(date) {
    if (!dragRef.current) return;
    setPendingRange({ from: dragRef.current.start, to: date });
  }

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

  function handleCalendarSelect(range) {
    if (disabled) return;
    if (!range || !range.from) {
      setPendingRange(null);
      return;
    }
    if (range.to) {
      if (dragRef.current) return; // 드래그 중이면 mouseup 핸들러가 커밋을 전담한다.
      // 시작~종료 모두 클릭 완료 → 즉시 커밋해 조회(별도 버튼 없이 바로 반영).
      setPendingRange(null);
      onChange({ from: fmt(range.from), to: fmt(range.to), preset: "custom" });
    } else {
      // 시작일만 클릭한 중간 상태 → 로컬로만 반영, 아직 조회하지 않음.
      setPendingRange({ from: range.from, to: undefined });
    }
  }

  const calendarSelected = pendingRange || { from: parseYmd(value?.from), to: parseYmd(value?.to) };

  // 달력 자체에서도 한도 밖 날짜는 선택 불가로 막는다(칩 레벨의 exceedsLimit 과 동일 기준, 9.3).
  const earliestAllowed = maxRangeDays != null && Number(maxRangeDays) >= 0
    ? addDays(today, -(Number(maxRangeDays) - 1))
    : null;
  const calendarDisabled = disabled
    ? true
    : earliestAllowed
      ? [{ before: earliestAllowed }]
      : false;

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
        <div className="pp-calendar" onMouseDown={handleCalendarMouseDown}>
          <DayPicker
            mode="range"
            locale={ko}
            selected={calendarSelected}
            onSelect={handleCalendarSelect}
            onDayMouseEnter={(date) => handleDayMouseEnter(date)}
            disabled={calendarDisabled}
            defaultMonth={calendarSelected.to || calendarSelected.from || today}
            excludeDisabled
          />
          <div className="pp-calendar-readout">
            {value?.from && value?.to ? (
              <span>
                {value.from} ~ {value.to}
              </span>
            ) : (
              <span className="muted">시작일과 종료일을 순서대로 클릭하세요.</span>
            )}
          </div>
          <div className="pp-calendar-hint">
            날짜를 누른 채 끌어 기간을 한 번에 선택할 수 있어요. 이미 선택된 기간의 시작일을
            바꾸려면 새 시작일을 두 번 클릭(또는 드래그)해야 반영돼요.
          </div>
        </div>
      ) : null}

      {maxRangeDays != null && Number(maxRangeDays) >= 0 ? (
        <div className="pp-limit-note">현재 플랜은 최근 {maxRangeDays}일까지 조회할 수 있어요 · PRO로 업그레이드</div>
      ) : null}
    </div>
  );
}
