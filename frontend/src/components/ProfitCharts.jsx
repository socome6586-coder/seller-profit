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
