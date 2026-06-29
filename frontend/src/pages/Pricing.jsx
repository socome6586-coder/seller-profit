import { useEffect, useState, useCallback } from "react";
import { api, won } from "../api";
import { useAuth } from "../auth.jsx";

// 요금제 + 현재 구독 상태 + 구독/해지.
//
// 결제(토스 빌링)는 환경변수 TOSS_SECRET_KEY 가 설정돼야 활성화된다(GET /api/billing/status).
// 미설정이면 '구독' 버튼을 비활성화하고 안내만 한다. 실제 토스 SDK 카드 등록(authKey 발급)
// 연동은 키 확보 후 마무리하는 단계(README/CLAUDE 의 'Phase 3 마무리' 참고).
export default function Pricing() {
  const { user } = useAuth();
  const [plans, setPlans] = useState([]);
  const [sub, setSub] = useState(null);
  const [billingEnabled, setBillingEnabled] = useState(false);
  const [note, setNote] = useState(null);
  const [busy, setBusy] = useState(false);

  const loadSub = useCallback(async () => {
    if (!user) return;
    try {
      setSub(await api("/api/subscription")); // 세션 주체로 조회(userId 미전달)
    } catch {
      setSub(null);
    }
  }, [user]);

  useEffect(() => {
    api("/api/plans").then(setPlans).catch(() => setPlans([]));
    api("/api/billing/status")
      .then((s) => setBillingEnabled(!!s?.enabled))
      .catch(() => setBillingEnabled(false));
    loadSub();
  }, [loadSub]);

  const currentStatus = sub?.status || user?.subscriptionStatus;
  const currentCode = currentStatus === "FREE" ? "FREE" : "PRO";
  const isActive = currentStatus === "ACTIVE";

  async function subscribe() {
    // TODO(토스 키 확보 후): 토스 SDK 로 카드 등록 → authKey 수신 → 아래 호출에 전달.
    //   import { loadTossPayments } from "@tosspayments/payment-sdk";
    //   const toss = await loadTossPayments(CLIENT_KEY);
    //   const { authKey } = await toss.requestBillingAuth("카드", { customerKey, successUrl, failUrl });
    setNote(null);
    setBusy(true);
    try {
      await api("/api/billing/subscribe", { method: "POST", body: { authKey: "PLACEHOLDER_AUTH_KEY" } });
      setNote({ ok: true, msg: "구독이 시작됐습니다." });
      loadSub();
    } catch (e) {
      setNote({ ok: false, msg: e.message });
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    setNote(null);
    setBusy(true);
    try {
      await api("/api/billing/cancel", { method: "POST" });
      setNote({ ok: true, msg: "구독이 해지됐습니다. 남은 기간까지 이용할 수 있어요." });
      loadSub();
    } catch (e) {
      setNote({ ok: false, msg: e.message });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="wrap">
      <h1>요금제</h1>
      <div className="sub">
        현재 상태: <b>{currentStatus || "–"}</b>
        {sub?.currentPeriodEnd ? ` · 이용 만료 ${sub.currentPeriodEnd.slice(0, 10)}` : ""}
      </div>

      {note ? <div className={"note " + (note.ok ? "ok" : "err")}>{note.msg}</div> : null}

      <div className="plans">
        {plans.map((p) => {
          const current = p.code === currentCode;
          return (
            <div key={p.code} className={"plan" + (current ? " current" : "")}>
              <div className="pname">
                {p.name}
                {current ? <span className="tag">현재 플랜</span> : null}
              </div>
              <div className="price">
                {p.monthlyPrice === 0 ? "무료" : won(p.monthlyPrice)}
                {p.monthlyPrice > 0 ? <small> / 월</small> : null}
              </div>
              <ul>
                {p.features.map((f, i) => (
                  <li key={i}>{f}</li>
                ))}
              </ul>

              {p.code === "PRO" ? (
                isActive ? (
                  <button className="ghost" onClick={cancel} disabled={busy}>
                    구독 해지
                  </button>
                ) : (
                  <>
                    <button onClick={subscribe} disabled={busy || !billingEnabled}>
                      {busy ? "처리 중…" : "PRO 구독하기"}
                    </button>
                    {!billingEnabled ? (
                      <div className="note muted" style={{ marginTop: 8 }}>
                        결제 연동 준비 중입니다(토스 키 설정 후 활성화).
                      </div>
                    ) : null}
                  </>
                )
              ) : (
                <button className="ghost" disabled>
                  기본 제공
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
