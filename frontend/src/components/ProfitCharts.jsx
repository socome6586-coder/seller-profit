import { won } from "../api";

// 대시보드 표(ProfitTable)만으로는 적자/흑자 비중이 한눈에 안 들어온다는 피드백(2026-07-07)
// 반영. 별도 차트 라이브러리(recharts 등)를 새로 추가하지 않고 순수 SVG + CSS 폭으로 도넛과
// 가로 바를 직접 그린다 — 이미 ReturnsTable 이 .bar-wrap/.bar(라이브러리 없이 width%로 바를
// 그리는 방식)를 쓰고 있어 같은 접근을 그대로 잇는다. 번들 크기를 늘리지 않는 게 장점.
// docs/DECISIONS.md 참고(차트 도입 결정 기록).

/** 적자 상품 합계가 흑자 상품 합계를 얼마나 갚아먹는지 보여주는 도넛. */
export function ProfitDonut({ products }) {
  const items = products || [];
  let gain = 0;
  let loss = 0;
  for (const p of items) {
    const v = Number(p.profit) || 0;
    if (v >= 0) gain += v;
    else loss += -v;
  }
  const total = gain + loss;

  if (items.length === 0 || total <= 0) {
    return (
      <div className="donut-card">
        <div className="empty">표시할 데이터가 없습니다.</div>
      </div>
    );
  }

  const lossPct = (loss / total) * 100;
  const R = 60;
  const C = 2 * Math.PI * R;
  const lossLen = (lossPct / 100) * C;

  return (
    <div className="donut-card">
      <div className="donut-wrap">
        <svg viewBox="0 0 140 140" className="donut-svg" role="img" aria-label={`적자 비중 ${lossPct.toFixed(0)}%`}>
          <circle cx="70" cy="70" r={R} fill="none" stroke="var(--profit)" strokeWidth="18" />
          {lossPct > 0 ? (
            <circle
              cx="70"
              cy="70"
              r={R}
              fill="none"
              stroke="var(--loss)"
              strokeWidth="18"
              strokeLinecap="round"
              strokeDasharray={`${lossLen} ${C - lossLen}`}
              transform="rotate(-90 70 70)"
            />
          ) : null}
        </svg>
        <div className="donut-center">
          <div className={"donut-pct" + (lossPct >= 30 ? " hot" : "")}>{lossPct.toFixed(0)}%</div>
          <div className="donut-label">적자 비중</div>
        </div>
      </div>
      <div className="donut-legend">
        <span><i className="dot dot-profit" />흑자 합계 {won(gain)}</span>
        <span><i className="dot dot-loss" />적자 합계 {won(loss)}</span>
      </div>
      {lossPct >= 1 ? (
        <div className="donut-callout">
          적자 상품이 흑자의 <b>{lossPct.toFixed(0)}%</b>를 갚아먹고 있어요.
        </div>
      ) : (
        <div className="donut-callout ok">적자 상품이 없어요. 좋은 흐름이에요 👍</div>
      )}
    </div>
  );
}

// 도넛/바 차트는 "얼마나 적자인지"를 보여주지만 "왜 적자인지"는 말해주지 않는다는 피드백
// (2026-07-07, 차트 도입 직후 이어진 요청) 반영. 원가/배분비용/광고비/반품률은 이미
// ProfitDashboardController 가 계산해 내려주는 실수치이므로, 가짜 지표를 만들지 않고
// 그 비율만으로 규칙 기반 진단 문구를 만든다(docs/HANDOFF.md "가짜 지표 금지" 원칙 준수).

/** 적자 상품 1개에 대해 "무엇이 매출을 가장 많이 갚아먹었는지"와 반품률을 규칙으로 진단한다. */
function diagnoseLoss(it) {
  const revenue = Number(it.revenue) || 0;
  const cogs = Number(it.cogsTotal) || 0;
  const alloc = Number(it.allocatedCost) || 0;
  const ad = Number(it.adSpend) || 0;
  const units = Number(it.units) || 0;
  const returnedUnits = Number(it.returnedUnits) || 0;
  const notes = [];

  if (revenue <= 0) {
    notes.push("이 기간 매출이 없는데 비용만 발생했어요. 재고·광고 운영을 점검해보세요.");
  } else {
    const drivers = [
      { ratio: cogs / revenue, text: (r) => `매입원가가 매출의 ${r}%예요. 매입가를 낮추거나 판매가를 올리는 걸 검토해보세요.` },
      { ratio: alloc / revenue, text: (r) => `배분된 기타비용(배송비 등)이 매출의 ${r}%를 차지해요. 배송·포장 비용 절감을 검토해보세요.` },
      { ratio: ad / revenue, text: (r) => `광고비가 매출의 ${r}%를 차지해요. 키워드·타겟팅을 재점검하거나 광고비를 줄여보세요.` },
    ].sort((a, b) => b.ratio - a.ratio);
    const top = drivers[0];
    if (top && top.ratio > 0) notes.push(top.text(Math.round(top.ratio * 100)));
  }

  if (units > 0 && returnedUnits / units >= 0.1) {
    notes.push(`반품률이 ${Math.round((returnedUnits / units) * 100)}%로 높은 편이에요. 상품 설명이나 품질을 점검해보세요.`);
  }

  if (notes.length === 0) {
    notes.push("매출보다 비용이 근소하게 많아요. 판매가를 소폭 올리는 것만으로도 개선될 수 있어요.");
  }
  return notes;
}

/** 적자 상품마다 "왜 적자인지" + "무엇을 해볼 수 있는지"를 안내하는 진단 카드 목록. */
export function LossInsights({ products, max = 6 }) {
  const items = (products || []).filter((p) => p.loss).slice(0, max);
  if (items.length === 0) return null;

  return (
    <div className="loss-insights">
      <h3>적자 상품 진단</h3>
      {items.map((it, i) => (
        <div className="loss-insight-row" key={i}>
          <div className="loss-insight-name">
            {it.name}
            <span className="badge">적자</span>
          </div>
          <ul className="loss-insight-notes">
            {diagnoseLoss(it).map((note, j) => (
              <li key={j}>{note}</li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

/** 상품별 순이익을 절대값 큰 순서(가장 극적인 것부터)로 가로 바 차트로 보여준다. */
export function ProfitBarChart({ products, max = 8 }) {
  const items = [...(products || [])]
    .sort((a, b) => Math.abs(Number(b.profit) || 0) - Math.abs(Number(a.profit) || 0))
    .slice(0, max);
  const maxAbs = Math.max(...items.map((it) => Math.abs(Number(it.profit) || 0)), 1);

  if (items.length === 0) {
    return <div className="empty">표시할 데이터가 없습니다.</div>;
  }

  return (
    <div className="profit-bars">
      {items.map((it, i) => {
        const v = Number(it.profit) || 0;
        const w = Math.max((Math.abs(v) / maxAbs) * 100, 2);
        const isLoss = v < 0;
        return (
          <div className="profit-bar-row" key={i}>
            <div className="profit-bar-name" title={it.name}>
              {it.name}
              {it.loss ? <span className="badge">적자</span> : null}
            </div>
            <div className="profit-bar-track">
              <div className={"profit-bar-fill " + (isLoss ? "is-loss" : "is-profit")} style={{ width: w + "%" }} />
            </div>
            <div className={"profit-bar-value " + (isLoss ? "neg" : "pos")}>{won(v)}</div>
          </div>
        );
      })}
    </div>
  );
}
