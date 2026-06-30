import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";

// 쿠팡 계정 연동/해제. 키는 서버에서 암호화 저장되며 응답엔 절대 노출되지 않는다.
// 플랜 한도(FREE=1개)는 서버가 강제하고, 화면은 한도 도달 시 폼을 잠근다.
export default function Accounts() {
  const [accounts, setAccounts] = useState(null);
  const [plan, setPlan] = useState(null); // /api/subscription 의 plan (maxMarketAccounts 포함)
  const [vendorId, setVendorId] = useState("");
  const [accessKey, setAccessKey] = useState("");
  const [secretKey, setSecretKey] = useState("");
  const [note, setNote] = useState(null);
  const [busy, setBusy] = useState(false);
  const [syncingId, setSyncingId] = useState(null);

  const load = useCallback(async () => {
    try {
      setAccounts(await api("/api/me/accounts"));
    } catch {
      setAccounts([]);
    }
    try {
      const sub = await api("/api/subscription");
      setPlan(sub?.plan || null);
    } catch {
      setPlan(null);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const max = plan?.maxMarketAccounts ?? -1; // -1 무제한
  const count = accounts?.length ?? 0;
  const atLimit = max >= 0 && count >= max;

  async function connect(e) {
    e.preventDefault();
    setNote(null);
    if (!vendorId.trim() || !accessKey.trim() || !secretKey.trim()) {
      return setNote({ ok: false, msg: "업체코드·Access Key·Secret Key 를 모두 입력하세요." });
    }
    setBusy(true);
    try {
      await api("/api/me/accounts", {
        method: "POST",
        body: { vendorId: vendorId.trim(), accessKey: accessKey.trim(), secretKey: secretKey.trim() },
      });
      setNote({ ok: true, msg: "연동됐습니다. 대시보드에서 선택할 수 있어요." });
      setVendorId("");
      setAccessKey("");
      setSecretKey("");
      load();
    } catch (err) {
      setNote({ ok: false, msg: err.message });
    } finally {
      setBusy(false);
    }
  }

  async function syncNow(id) {
    setNote(null);
    setSyncingId(id);
    try {
      const r = await api("/api/me/accounts/" + id + "/sync", { method: "POST" });
      const line = (label, s) =>
        s.ok ? `${label} ${s.count}건` : `${label} 실패(${s.error})`;
      const allOk = r.orders.ok && r.settlements.ok && r.returns.ok;
      setNote({
        ok: allOk,
        msg:
          "동기화 결과 — " +
          [line("주문", r.orders), line("정산", r.settlements), line("반품", r.returns)].join(" · "),
      });
    } catch (err) {
      setNote({ ok: false, msg: "동기화 실패: " + err.message });
    } finally {
      setSyncingId(null);
    }
  }

  async function disconnect(id) {
    if (!window.confirm("이 계정을 연동 해제할까요? 수집된 주문/정산/반품 데이터도 함께 삭제됩니다.")) return;
    setNote(null);
    try {
      await api("/api/me/accounts/" + id, { method: "DELETE" });
      setNote({ ok: true, msg: "연동을 해제했습니다." });
      load();
    } catch (err) {
      setNote({ ok: false, msg: err.message });
    }
  }

  return (
    <div className="wrap">
      <h1>계정 연동</h1>
      <div className="sub">
        쿠팡 셀러 API 키를 등록하면 주문·정산·반품을 자동 수집해 순이익을 계산합니다.
        키는 암호화되어 저장되고 화면/응답에 다시 표시되지 않습니다.
      </div>

      {note ? <div className={"note " + (note.ok ? "ok" : "err")}>{note.msg}</div> : null}

      <h2>
        연동된 계정{" "}
        <span className="muted" style={{ fontSize: 12, fontWeight: 400 }}>
          {accounts == null ? "" : max >= 0 ? `${count} / ${max}` : `${count} (무제한)`}
        </span>
      </h2>
      <table>
        <thead>
          <tr><th>채널</th><th>업체코드</th><th>계정 ID</th><th></th><th></th></tr>
        </thead>
        <tbody>
          {accounts == null ? (
            <tr><td colSpan={4} className="empty">불러오는 중…</td></tr>
          ) : accounts.length === 0 ? (
            <tr><td colSpan={5} className="empty">아직 연동된 계정이 없습니다. 아래에서 추가하세요.</td></tr>
          ) : (
            accounts.map((a) => (
              <tr key={a.id}>
                <td>{a.channel}</td>
                <td>{a.vendorId}</td>
                <td className="muted">#{a.id}</td>
                <td>
                  <button onClick={() => syncNow(a.id)} disabled={syncingId === a.id}>
                    {syncingId === a.id ? "동기화 중…" : "지금 동기화"}
                  </button>
                </td>
                <td>
                  <button className="ghost" onClick={() => disconnect(a.id)}>해제</button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <h2>새 쿠팡 계정 연동</h2>
      {atLimit ? (
        <div className="error-banner">
          현재 플랜({plan?.name})의 연동 한도({max}개)에 도달했습니다.{" "}
          <Link to="/pricing">PRO 로 업그레이드</Link>하면 무제한으로 연동할 수 있어요.
        </div>
      ) : (
        <form className="panel" onSubmit={connect}>
          <div className="field">
            <label>업체코드 (vendorId)</label>
            <input value={vendorId} onChange={(e) => setVendorId(e.target.value)} placeholder="예: A00012345" />
          </div>
          <div className="field">
            <label>Access Key</label>
            <input value={accessKey} onChange={(e) => setAccessKey(e.target.value)} autoComplete="off" />
          </div>
          <div className="field">
            <label>Secret Key</label>
            <input type="password" value={secretKey} onChange={(e) => setSecretKey(e.target.value)} autoComplete="off" />
          </div>
          <button type="submit" disabled={busy}>{busy ? "연동 중…" : "계정 연동"}</button>
        </form>
      )}
    </div>
  );
}
