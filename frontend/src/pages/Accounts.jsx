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
  // 계정 연동 가이드용 서버 공인 IP. undefined=조회 중, null=조회 실패, string=정상값.
  // 단일 소스(app.public-server-ip, application.yml)를 GET /api/config 로 받아온다 —
  // 이 값을 프론트 코드에 직접 하드코딩하지 않는다(docs/onboarding-tasks.md §2).
  const [serverIp, setServerIp] = useState(undefined);
  const [ipCopied, setIpCopied] = useState(false);

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

  useEffect(() => {
    api("/api/config")
      .then((c) => setServerIp(c?.publicServerIp || null))
      .catch(() => setServerIp(null));
  }, []);

  async function copyServerIp() {
    if (!serverIp) return;
    try {
      await navigator.clipboard.writeText(serverIp);
    } catch {
      return; // 클립보드 권한 거부 등 — 값은 화면에 그대로 보이므로 조용히 무시
    }
    setIpCopied(true);
    setTimeout(() => setIpCopied(false), 1500);
  }

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

      <details className="guide" open>
        <summary className="guide-summary">
          Access Key·Secret Key는 어디서 받나요?
          <span className="guide-summary-hint">(쿠팡 WING에서 발급 — 클릭해 접기/펼치기)</span>
        </summary>
        <ol className="guide-steps">
          <li>
            쿠팡 <b>WING</b>(
            <a href="https://wing.coupang.com" target="_blank" rel="noopener noreferrer">wing.coupang.com</a>
            )에 로그인
          </li>
          <li>우측 상단 아이디 클릭 → <b>"판매자정보"</b>(또는 <b>"추가판매정보"</b>) 클릭</li>
          <li>페이지 하단 <b>"OPEN API 키 발급받기"</b> 버튼 클릭</li>
          <li>
            팝업에서 <b>"OPEN API"</b> 선택 → 약관 동의 → <b>"연동업체 선택"</b> 화면에서{" "}
            <b>"자체개발(직접입력)"</b> 선택(seller-profit이 목록에 없어요) → 아래 정보 입력:
            <ul className="guide-substeps">
              <li>업체명: 아무 값 (예: 개인 상호명)</li>
              <li>URL: 없으면 <code>wing.coupang.com</code> 입력해도 무방</li>
              <li className="guide-ip-warn">
                <span className="badge">⚠️ 필수</span>
                <b> IP 주소: 반드시 seller-profit 운영 서버의 공인 IP를 입력하세요.</b>{" "}
                본인 PC의 IP가 아닙니다 — 실제 API 호출은 seller-profit 서버에서 나갑니다.
                <div className="guide-ip-box">
                  <code>
                    {serverIp === undefined ? "불러오는 중…" : serverIp || "조회 실패 — 새로고침 해주세요"}
                  </code>
                  <button type="button" className="ghost" onClick={copyServerIp} disabled={!serverIp}>
                    {ipCopied ? "복사됨!" : "복사"}
                  </button>
                </div>
              </li>
            </ul>
          </li>
          <li>발급 완료 화면에서 <b>업체코드 / Access Key / Secret Key</b> 확인 후 복사</li>
          <li>아래 폼에 그대로 붙여넣기</li>
        </ol>
        <a className="guide-wing-link" href="https://wing.coupang.com" target="_blank" rel="noopener noreferrer">
          쿠팡 WING 바로가기 ↗
        </a>
        <div className="guide-note muted">
          참고: 사업자 인증이 안 된 일반회원은 API 키 발급이 되지 않아요(이미 활성 쿠팡 셀러라면
          보통 문제 없습니다). 재발급·정보수정은 월 10회 제한, 유효기간 180일입니다.
        </div>
      </details>

      {atLimit ? (
        <div className="error-banner">
          현재 플랜({plan?.name})의 연동 한도({max}개)에 도달했습니다.{" "}
          <Link to="/pricing">PRO 로 업그레이드</Link>하면 무제한으로 연동할 수 있어요.
        </div>
      ) : (
        // 브라우저(특히 Chrome) 비밀번호 관리자가 이 폼을 로그인 폼으로 오인해 저장된 로그인
        // 이메일/비밀번호를 Access Key/Secret Key 칸에 자동완성하는 문제가 있었다. autoComplete="off"
        // 는 Chrome이 종종 무시하므로, 폼 전체에 autoComplete="off"를 걸고 Secret Key는
        // "new-password"(저장된 비밀번호를 채우지 말라는 표준 신호)로 지정해 억제한다.
        <form className="panel" onSubmit={connect} autoComplete="off">
          <div className="field">
            <label>업체코드 (vendorId)</label>
            <input
              value={vendorId}
              onChange={(e) => setVendorId(e.target.value)}
              placeholder="예: A00012345"
              autoComplete="off"
              name="coupang-vendor-id"
            />
          </div>
          <div className="field">
            <label>Access Key</label>
            <input
              value={accessKey}
              onChange={(e) => setAccessKey(e.target.value)}
              autoComplete="off"
              name="coupang-access-key"
            />
          </div>
          <div className="field">
            <label>Secret Key</label>
            <input
              type="password"
              value={secretKey}
              onChange={(e) => setSecretKey(e.target.value)}
              autoComplete="new-password"
              name="coupang-secret-key"
            />
          </div>
          <button type="submit" disabled={busy}>{busy ? "연동 중…" : "계정 연동"}</button>
        </form>
      )}
    </div>
  );
}
