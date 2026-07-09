import { useEffect, useState, useCallback } from "react";
import { api, won } from "../api";
import { useAuth } from "../auth.jsx";
import { usePageTitle } from "../hooks/usePageTitle";

// 요금제 + 현재 구독 상태 + 구독/해지.
//
// 결제(토스 빌링)는 환경변수 TOSS_SECRET_KEY 가 설정돼야 활성화된다(GET /api/billing/status).
// 미설정이면 '구독' 버튼을 비활성화하고 안내만 한다. 실제 토스 SDK 카드 등록(authKey 발급)
// 연동은 키 확보 후 마무리하는 단계(README/CLAUDE 의 'Phase 3 마무리' 참고).

// SubscriptionStatus(FREE/ACTIVE/PAST_DUE/CANCELED, 서버 enum 이름 그대로)를 화면에 그대로
// 영문으로 노출하지 않고 한글 라벨로 보여준다. 알 수 없는 값이 와도 원문을 그대로 보여줘
// 화면이 깨지지 않게 폴백한다.
const STATUS_LABELS = {
  FREE: "무료",
  ACTIVE: "PRO 이용중",
  PAST_DUE: "결제 연체",
  CANCELED: "해지 예정",
};

export default function Pricing() {
  usePageTitle("요금제");
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
    // 실수로 눌러 바로 해지되지 않도록 확인 알림 — 해지해도 남은 기간(만료일)까지는
    // 계속 PRO 로 쓸 수 있다는 걸 여기서 먼저 알려준다(실제 동작은 BillingService.cancel:
    // 상태만 CANCELED 로 바꾸고 만료일까지 접근은 유지).
    const until = sub?.currentPeriodEnd ? sub.currentPeriodEnd.slice(0, 10) : null;
    const confirmMsg = until
      ? `정말 구독을 해지하시겠습니까? ${until} 까지 PRO 이용이 가능합니다.`
      : "정말 구독을 해지하시겠습니까?";
    if (!window.confirm(confirmMsg)) return;

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
        현재 상태: <b>{currentStatus ? STATUS_LABELS[currentStatus] || currentStatus : "–"}</b>
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

              {/* PRO 카드는 결제 미연동 안내문이 버튼 아래 추가로 붙어 카드 높이가 늘어나는데,
                  FREE 카드는 버튼 하나뿐이라 두 카드의 버튼 위치가 서로 어긋나 보였다.
                  .plan-actions 를 카드 하단에 고정(margin-top: auto)해 버튼 줄을 맞춘다. */}
              <div className="plan-actions">
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
                          결제 연동 준비 중입니다.
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
            </div>
          );
        })}
      </div>
    </div>
  );
}
