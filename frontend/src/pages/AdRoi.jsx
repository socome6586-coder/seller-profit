import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, uploadFile, won, roas, num, signClass } from "../api";
import PeriodPicker, { computeRange } from "../components/PeriodPicker.jsx";
import { usePageTitle } from "../hooks/usePageTitle";

export default function AdRoi() {
  usePageTitle("광고 ROI");
  const [accounts, setAccounts] = useState(null); // null=로딩, []=계정없음
  const [accountId, setAccountId] = useState("");
  // 기본 진입 = "이번 달"(docs/period-picker-tasks.md T9 9.4, 대시보드와 동일 컴포넌트 재사용).
  const [period, setPeriod] = useState(() => ({ ...computeRange("thisMonth"), preset: "thisMonth" }));
  const [error, setError] = useState("");
  // 플랜의 조회기간 한도(9.3). UI 안내일 뿐 — 실제 강제는 서버(SubscriptionService.assertWithinLookback).
  const [maxRangeDays, setMaxRangeDays] = useState(null);

  const [summary, setSummary] = useState(null);

  const { from, to } = period;

  const periodParams = useCallback(() => {
    const p = new URLSearchParams({ accountId });
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    return p.toString();
  }, [accountId, from, to]);

  const loadSummary = useCallback(async () => {
    setError("");
    try {
      setSummary(await api("/api/dashboard/ad-roi?" + periodParams()));
    } catch (e) {
      setError("불러오기 실패: " + e.message);
      setSummary(null);
    }
  }, [periodParams]);

  const refreshAll = useCallback(() => {
    if (!accountId) return;
    if (from && to && from > to) return; // 직접 선택에서 잘못된 범위(시작>종료)면 조회하지 않음
    loadSummary();
  }, [accountId, from, to, loadSummary]);

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

  // 내 플랜의 조회기간 한도를 받아 PeriodPicker 에 안내용으로 전달(9.3).
  useEffect(() => {
    (async () => {
      try {
        const sub = await api("/api/subscription");
        setMaxRangeDays(sub?.plan?.dashboardLookbackDays ?? null);
      } catch {
        setMaxRangeDays(null);
      }
    })();
  }, []);

  // 계정이 정해지거나 기간이 바뀌면 자동 조회(프리셋 칩 선택 시 즉시 반영).
  useEffect(() => {
    refreshAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId, from, to]);

  const noAccounts = accounts != null && accounts.length === 0;
  const lossCount = (summary?.rows || []).filter((r) => r.adLoss).length;

  return (
    <div className="wrap">
      <h1>광고 ROI</h1>
      <div className="sub">
        광고전 기여이익보다 광고비가 더 큰 SKU("광고손실")를 적발합니다. 광고를 돌릴수록 손해인 상품이 맨 위로 올라옵니다.
      </div>

      {noAccounts ? (
        <div className="error-banner">
          연결된 마켓 계정이 없습니다. <Link to="/accounts">쿠팡 계정을 연동</Link>하면
          여기에서 광고 효율을 볼 수 있어요.
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
      </div>

      {error ? <div className="error-banner">{error}</div> : null}

      {summary && Number(summary.unassignedAdSpend) > 0 ? (
        <div className="warn-banner">
          미할당 광고비 {won(summary.unassignedAdSpend)} — 캠페인 단위로만 집행됐거나(옵션ID 없음) 현재
          상품 목록과 매칭되지 않는 SKU 의 광고비입니다. 특정 상품 손익엔 반영되지 않았습니다.
        </div>
      ) : null}

      {/* 직접 선택으로 달력이 펼쳐지면 달력을 왼쪽에, 요약 카드를 오른쪽에 나란히 배치한다(is-custom). */}
      <div className={"period-and-cards" + (period?.preset === "custom" ? " is-custom" : "")}>
        <PeriodPicker value={period} onChange={setPeriod} disabled={!accountId} maxRangeDays={maxRangeDays} />

        <div className="cards">
          <Card k="총 광고비" v={won(summary?.totalAdSpend)} />
          <Card k="재검토 대상 광고비" v={won(summary?.reviewAdSpend)} cls={summary && Number(summary.reviewAdSpend) > 0 ? "neg" : ""} />
          <Card k="미할당 광고비" v={won(summary?.unassignedAdSpend)} />
          <Card k="광고손실 SKU 수" v={num(lossCount)} cls={lossCount > 0 ? "neg" : ""} />
        </div>
      </div>

      <AdRoiTable rows={summary?.rows} />

      <h2>입력 / 관리</h2>
      <div className="manage">
        <AdSpendPanel accountId={accountId} onSaved={refreshAll} />
        <AdCsvPanel accountId={accountId} onSaved={refreshAll} />
      </div>
    </div>
  );
}

function Card({ k, v, cls }) {
  return (
    <div className="card">
      <div className="k">{k}</div>
      <div className={"v " + (cls || "")}>{v}</div>
    </div>
  );
}

function AdRoiTable({ rows }) {
  const items = rows || [];
  return (
    <table>
      <thead>
        <tr>
          <th>상품</th><th>매출</th><th>광고전 기여이익</th><th>광고비</th>
          <th>광고후 순이익</th><th>ROAS</th>
        </tr>
      </thead>
      <tbody>
        {rows == null ? (
          <tr><td colSpan={6} className="empty">「조회」를 눌러 데이터를 불러오세요.</td></tr>
        ) : items.length === 0 ? (
          <tr><td colSpan={6} className="empty">해당 기간 데이터가 없습니다.</td></tr>
        ) : (
          items.map((it, i) => (
            <tr key={i} className={it.adLoss ? "loss" : ""}>
              <td>
                {it.name}
                {it.adLoss ? <span className="badge">광고손실</span> : null}
              </td>
              <td>{won(it.revenue)}</td>
              <td className={signClass(it.contributionProfit)}>{won(it.contributionProfit)}</td>
              <td>{won(it.adSpend)}</td>
              <td className={signClass(it.postAdProfit)}>{won(it.postAdProfit)}</td>
              <td>{roas(it.roas)}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  );
}

function AdSpendPanel({ accountId, onSaved }) {
  const [vendorItemId, setVendorItemId] = useState("");
  const [campaign, setCampaign] = useState("");
  const [spendDate, setSpendDate] = useState("");
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState(null);

  async function save() {
    if (!accountId) return setNote({ ok: false, msg: "마켓 계정을 먼저 선택하세요." });
    if (!spendDate || amount === "") return setNote({ ok: false, msg: "광고일자·광고비를 입력하세요." });
    try {
      await api("/api/ads/spends", {
        method: "POST",
        body: {
          accountId: Number(accountId),
          vendorItemId: vendorItemId || null,
          campaign: campaign || null,
          adGroup: null,
          keyword: null,
          spendDate,
          amount: Number(amount),
        },
      });
      setNote({ ok: true, msg: "추가됐습니다. 표를 갱신합니다." });
      setAmount("");
      setCampaign("");
      setVendorItemId("");
      onSaved();
    } catch (e) {
      setNote({ ok: false, msg: "실패: " + e.message });
    }
  }

  return (
    <div className="panel">
      <h3>광고비 수기 입력 (1건)</h3>
      <p className="muted" style={{ fontSize: 12, marginBottom: 10 }}>
        옵션ID(SKU)를 비우면 캠페인 단위 광고비로 "미할당" 버킷에 잡힙니다.
      </p>
      <div className="field">
        <label>옵션ID (SKU, 선택)</label>
        <input type="text" value={vendorItemId}
          onChange={(e) => setVendorItemId(e.target.value)} placeholder="예: 12345678" />
      </div>
      <div className="field">
        <label>캠페인 (선택)</label>
        <input type="text" value={campaign}
          onChange={(e) => setCampaign(e.target.value)} placeholder="예: 여름프로모션" />
      </div>
      <div className="field">
        <label>광고일자</label>
        <input type="date" value={spendDate} onChange={(e) => setSpendDate(e.target.value)} />
      </div>
      <div className="field">
        <label>광고비 (원)</label>
        <input type="number" min="0" step="1" value={amount}
          onChange={(e) => setAmount(e.target.value)} placeholder="예: 20000" />
      </div>
      <button onClick={save}>광고비 추가</button>
      {note ? <div className={"note " + (note.ok ? "ok" : "err")}>{note.msg}</div> : <div className="note" />}
    </div>
  );
}

function AdCsvPanel({ accountId, onSaved }) {
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [note, setNote] = useState(null);
  const [busy, setBusy] = useState(false);

  async function upload() {
    if (!accountId) return setNote({ ok: false, msg: "마켓 계정을 먼저 선택하세요." });
    if (!file) return setNote({ ok: false, msg: "CSV 파일을 선택하세요." });
    setBusy(true);
    setResult(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const r = await uploadFile(`/api/ads/spends/import?accountId=${accountId}`, form);
      setResult(r);
      setNote({ ok: true, msg: `가져오기 완료. 표를 갱신합니다.` });
      setFile(null);
      onSaved();
    } catch (e) {
      setNote({ ok: false, msg: "실패: " + e.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="panel">
      <h3>광고비 CSV 업로드</h3>
      <p className="muted" style={{ fontSize: 12, marginBottom: 10 }}>
        헤더에 광고일자(date)·광고비(amount)는 필수, 옵션ID(vendorItemId)·캠페인·광고그룹·키워드는 선택입니다.
        같은 파일을 다시 올려도 중복 저장되지 않습니다.
      </p>
      <div className="field">
        <label>CSV 파일</label>
        <input type="file" accept=".csv,text/csv"
          onChange={(e) => setFile(e.target.files?.[0] || null)} />
      </div>
      <button onClick={upload} disabled={busy}>{busy ? "업로드 중…" : "업로드"}</button>
      {note ? <div className={"note " + (note.ok ? "ok" : "err")}>{note.msg}</div> : <div className="note" />}
      {result ? (
        <div className="cost-list">
          <div>신규 반영 {num(result.importedCount)}건</div>
          {result.skipped.length === 0 ? (
            <div>건너뛴 행 없음</div>
          ) : (
            result.skipped.map((s, i) => (
              <div key={i} className="neg">{s.row}행: {s.reason}</div>
            ))
          )}
        </div>
      ) : null}
    </div>
  );
}
