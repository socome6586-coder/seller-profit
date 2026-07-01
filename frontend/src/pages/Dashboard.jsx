import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, won, pct, num, signClass } from "../api";

export default function Dashboard() {
  const [accounts, setAccounts] = useState(null); // null=로딩, []=계정없음
  const [accountId, setAccountId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [error, setError] = useState("");

  const [profit, setProfit] = useState(null);
  const [returns, setReturns] = useState(null);
  const [products, setProducts] = useState([]);
  const [costs, setCosts] = useState([]);

  const periodParams = useCallback(() => {
    const p = new URLSearchParams({ accountId });
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    return p.toString();
  }, [accountId, from, to]);

  const loadProfit = useCallback(async () => {
    setError("");
    try {
      setProfit(await api("/api/dashboard/profit?" + periodParams()));
    } catch (e) {
      setError("불러오기 실패: " + e.message);
      setProfit(null);
    }
  }, [periodParams]);

  const loadReturns = useCallback(async () => {
    try {
      setReturns(await api("/api/dashboard/returns?" + periodParams()));
    } catch {
      setReturns({ failed: true });
    }
  }, [periodParams]);

  const loadProducts = useCallback(async () => {
    try {
      setProducts(await api("/api/products?accountId=" + accountId));
    } catch {
      setProducts([]);
    }
  }, [accountId]);

  const loadCosts = useCallback(async () => {
    try {
      setCosts(await api("/api/costs?accountId=" + accountId));
    } catch {
      setCosts([]);
    }
  }, [accountId]);

  const refreshAll = useCallback(() => {
    if (!accountId) return;
    loadProfit();
    loadReturns();
    loadProducts();
    loadCosts();
  }, [accountId, loadProfit, loadReturns, loadProducts, loadCosts]);

  // 내 마켓 계정 목록을 받아 첫 계정을 기본 선택.
  useEffect(() => {
    (async () => {
      try {
        const list = await api("/api/me/accounts");
        setAccounts(list);
        if (list.length > 0) setAccountId(String(list[0].id));
      } catch {
        setAccounts([]);
      }
    })();
  }, []);

  // 계정이 정해지면(또는 바뀌면) 자동 조회.
  useEffect(() => {
    refreshAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId]);

  const noAccounts = accounts != null && accounts.length === 0;

  return (
    <div className="wrap">
      <h1>셀러 순이익 대시보드</h1>
      <div className="sub">
        정산 실수령 − 원가 − 배분된 기타비용 − 광고비 = 진짜 순이익. 적자 상품이 맨 위로 올라옵니다.
      </div>

      {noAccounts ? (
        <div className="error-banner">
          연결된 마켓 계정이 없습니다. <Link to="/accounts">쿠팡 계정을 연동</Link>하면
          여기에서 순이익을 볼 수 있어요.
        </div>
      ) : null}

      <div className="controls">
        <div className="field">
          <label>마켓 계정</label>
          <select value={accountId} onChange={(e) => setAccountId(e.target.value)}>
            {accounts == null ? (
              <option value="">불러오는 중…</option>
            ) : accounts.length === 0 ? (
              <option value="">계정 없음</option>
            ) : (
              accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.channel} · {a.vendorId} (#{a.id})
                </option>
              ))
            )}
          </select>
        </div>
        <div className="field">
          <label>시작일 (선택)</label>
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="field">
          <label>종료일 (선택)</label>
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <button onClick={refreshAll} disabled={!accountId}>조회</button>
      </div>

      {error ? <div className="error-banner">{error}</div> : null}

      <div className="cards">
        <Card k="총매출 (정산 실수령)" v={won(profit?.totalRevenue)} />
        <Card k="총순이익 (진짜)" v={won(profit?.totalProfit)} cls={profit ? signClass(profit.totalProfit) : ""} />
        <Card k="평균 마진율" v={pct(profit?.avgMarginPct)} />
        <Card k="배분 기타비용" v={won(profit?.totalAllocatedCost)} />
        <Card
          k="광고비"
          v={won(profit?.totalAdSpend)}
          sub={
            profit && Number(profit.unallocatedAdSpend) > 0
              ? "미할당 " + won(profit.unallocatedAdSpend)
              : null
          }
        />
      </div>

      <ProfitTable products={profit?.products} />

      <h2>
        반품 사유 분석{" "}
        {returns && !returns.failed ? (
          <span className="muted" style={{ fontSize: 12, fontWeight: 400 }}>
            총 반품 {num(returns.totalQuantity)}개
          </span>
        ) : null}
      </h2>
      <ReturnsTable returns={returns} />

      <h2>입력 / 관리</h2>
      <div className="manage">
        <CogsPanel products={products} onSaved={() => { loadProducts(); loadProfit(); }} />
        <CostPanel
          accountId={accountId}
          costs={costs}
          onSaved={() => { loadCosts(); loadProfit(); }}
        />
      </div>
    </div>
  );
}

function Card({ k, v, cls, sub }) {
  return (
    <div className="card">
      <div className="k">{k}</div>
      <div className={"v " + (cls || "")}>{v}</div>
      {sub ? <div className="muted" style={{ fontSize: 12 }}>{sub}</div> : null}
    </div>
  );
}

function ProfitTable({ products }) {
  const items = products || [];
  return (
    <table>
      <thead>
        <tr>
          <th>상품</th><th>매출</th><th>수량</th><th>반품</th><th>원가</th>
          <th>배분비용</th><th>광고비</th><th>순이익</th><th>마진율</th>
        </tr>
      </thead>
      <tbody>
        {products == null ? (
          <tr><td colSpan={9} className="empty">「조회」를 눌러 데이터를 불러오세요.</td></tr>
        ) : items.length === 0 ? (
          <tr><td colSpan={9} className="empty">해당 기간 데이터가 없습니다.</td></tr>
        ) : (
          items.map((it, i) => (
            <tr key={i} className={it.loss ? "loss" : ""}>
              <td>
                {it.name}
                {it.loss ? <span className="badge">적자</span> : null}
              </td>
              <td>{won(it.revenue)}</td>
              <td>{num(it.units)}</td>
              <td className={Number(it.returnedUnits) > 0 ? "neg" : "muted"}>{num(it.returnedUnits)}</td>
              <td>{won(it.cogsTotal)}</td>
              <td>{won(it.allocatedCost)}</td>
              <td>{won(it.adSpend)}</td>
              <td className={signClass(it.profit)}>{won(it.profit)}</td>
              <td className={signClass(it.profit)}>{pct(it.marginPct)}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}

function ReturnsTable({ returns }) {
  const items = returns && !returns.failed ? returns.reasons || [] : [];
  const maxShare = Math.max(...items.map((it) => Number(it.sharePct) || 0), 1);
  return (
    <table>
      <thead>
        <tr><th>반품 사유</th><th>반품수량</th><th>건수</th><th>비중</th></tr>
      </thead>
      <tbody>
        {returns == null ? (
          <tr><td colSpan={4} className="empty">「조회」를 눌러 데이터를 불러오세요.</td></tr>
        ) : returns.failed ? (
          <tr><td colSpan={4} className="empty">반품 통계 불러오기 실패</td></tr>
        ) : items.length === 0 ? (
          <tr><td colSpan={4} className="empty">해당 기간 반품이 없습니다.</td></tr>
        ) : (
          items.map((it, i) => {
            const share = Number(it.sharePct) || 0;
            const w = Math.round((share / maxShare) * 120);
            return (
              <tr key={i}>
                <td>{it.reason}</td>
                <td>{num(it.quantity)}</td>
                <td>{num(it.lineCount)}</td>
                <td>
                  <span className="bar-wrap">
                    {share.toFixed(1)}%
                    <span className="bar" style={{ width: w + "px" }} />
                  </span>
                </td>
              </tr>
            );
          })
        )}
      </tbody>
    </table>
  );
}

function CogsPanel({ products, onSaved }) {
  const [productId, setProductId] = useState("");
  const [cogs, setCogs] = useState("");
  const [note, setNote] = useState(null);

  async function save() {
    if (!productId) return setNote({ ok: false, msg: "상품을 선택하세요." });
    if (cogs === "") return setNote({ ok: false, msg: "원가를 입력하세요." });
    try {
      await api(`/api/products/${productId}/cogs`, { method: "PATCH", body: { cogs: Number(cogs) } });
      setNote({ ok: true, msg: "저장됐습니다. 대시보드를 갱신합니다." });
      onSaved();
    } catch (e) {
      setNote({ ok: false, msg: "실패: " + e.message });
    }
  }

  return (
    <div className="panel">
      <h3>상품 원가(COGS) 입력</h3>
      <div className="field">
        <label>상품 선택</label>
        <select value={productId} onChange={(e) => setProductId(e.target.value)}>
          <option value="">{products.length === 0 ? "상품 없음" : "상품을 선택하세요"}</option>
          {products.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} {p.cogs == null ? "(원가 미입력)" : "(₩" + num(p.cogs) + ")"}
            </option>
          ))}
        </select>
      </div>
      <div className="field">
        <label>매입원가 (개당, 원)</label>
        <input type="number" min="0" step="1" value={cogs}
          onChange={(e) => setCogs(e.target.value)} placeholder="예: 3000" />
      </div>
      <button onClick={save}>원가 저장</button>
      {note ? <div className={"note " + (note.ok ? "ok" : "err")}>{note.msg}</div> : <div className="note" />}
    </div>
  );
}

function CostPanel({ accountId, costs, onSaved }) {
  const [costType, setCostType] = useState("SHIPPING");
  const [amount, setAmount] = useState("");
  const [periodStart, setPeriodStart] = useState("");
  const [periodEnd, setPeriodEnd] = useState("");
  const [memo, setMemo] = useState("");
  const [note, setNote] = useState(null);

  async function save() {
    if (!amount || !periodStart || !periodEnd) {
      return setNote({ ok: false, msg: "금액·시작일·종료일을 입력하세요." });
    }
    try {
      await api("/api/costs", {
        method: "POST",
        body: {
          accountId: Number(accountId),
          costType,
          amount: Number(amount),
          periodStart,
          periodEnd,
          memo: memo || null,
        },
      });
      setNote({ ok: true, msg: "추가됐습니다. 대시보드를 갱신합니다." });
      setAmount("");
      setMemo("");
      onSaved();
    } catch (e) {
      setNote({ ok: false, msg: "실패: " + e.message });
    }
  }

  return (
    <div className="panel">
      <h3>기타비용 입력 (기간 총액)</h3>
      <p className="muted" style={{ fontSize: 12, marginBottom: 10 }}>
        광고비는 여기서 받지 않습니다. SKU별 광고 손익 분석을 위해 별도 광고비 입력 API(<code>/api/ads/spends</code>)로 옮겨졌습니다(전용 화면은 준비 중).
      </p>
      <div className="field">
        <label>유형</label>
        <select value={costType} onChange={(e) => setCostType(e.target.value)}>
          <option value="SHIPPING">배송비 (SHIPPING)</option>
          <option value="ETC">기타 (ETC)</option>
        </select>
      </div>
      <div className="field">
        <label>금액 (원)</label>
        <input type="number" min="0" step="1" value={amount}
          onChange={(e) => setAmount(e.target.value)} placeholder="예: 50000" />
      </div>
      <div className="field">
        <label>시작일</label>
        <input type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
      </div>
      <div className="field">
        <label>종료일</label>
        <input type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
      </div>
      <div className="field">
        <label>메모 (선택)</label>
        <input type="text" value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="예: 6월 광고비" />
      </div>
      <button onClick={save}>비용 추가</button>
      {note ? <div className={"note " + (note.ok ? "ok" : "err")}>{note.msg}</div> : <div className="note" />}
      <div className="cost-list">
        {costs.length === 0 ? (
          "등록된 비용이 없습니다."
        ) : (
          costs.map((c, i) => (
            <div key={i}>
              {c.periodStart} ~ {c.periodEnd} · {c.costType} · ₩{num(c.amount)}
              {c.memo ? " · " + c.memo : ""}
            </div>
          ))
        )}
      </div>
    </div>
  );
}
