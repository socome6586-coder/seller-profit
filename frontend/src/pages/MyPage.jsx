import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth.jsx";
import { contactPath } from "../contact";
import { usePageTitle } from "../hooks/usePageTitle";

const CONFIRM_PHRASE = "회원탈퇴";
const STATUS_LABELS = {
  FREE: "무료",
  ACTIVE: "PRO 이용중",
  PAST_DUE: "결제 연체",
  CANCELED: "해지 예정",
};

function formatDateTime(value) {
  if (!value) return "–";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric" });
}

// 개인정보처리방침(6항)에서 "마이페이지에서 회원 탈퇴 가능"이라고 안내하는 실제 화면.
// 관리자 계정은 서버에서 거부되므로(admin_audit 참조무결성 + 마지막 관리자 보호),
// 그 경우 에러 메시지를 그대로 보여주고 문의하기로 안내한다.
export default function MyPage() {
  usePageTitle("마이페이지");
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const [confirmText, setConfirmText] = useState("");
  const [subscription, setSubscription] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api("/api/subscription")
      .then(setSubscription)
      .catch(() => setSubscription(null));
  }, []);

  async function withdraw() {
    setError("");
    if (confirmText !== CONFIRM_PHRASE) return;
    if (!window.confirm("정말 탈퇴하시겠어요? 연동된 쿠팡 계정·수집된 주문/정산/반품·원가·광고비 데이터가 모두 즉시 삭제되며 복구할 수 없습니다.")) {
      return;
    }
    setBusy(true);
    try {
      await api("/api/auth/me", { method: "DELETE" });
      await refresh(); // 서버 세션은 이미 무효화됨 — 클라이언트 상태만 비로그인으로 맞춘다
      navigate("/", { replace: true });
    } catch (err) {
      setError(err.message || "탈퇴에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="wrap">
      <h1>마이페이지</h1>
      <div className="sub">계정 정보 확인 및 회원 탈퇴</div>

      <h2>계정 정보</h2>
      <div className="panel">
        <div className="field">
          <label>이메일</label>
          <div>{user?.email}</div>
        </div>
        <div className="field">
          <label>내 요금제</label>
          <div>
            {subscription?.plan?.name || STATUS_LABELS[user?.subscriptionStatus] || user?.subscriptionStatus || "–"}
            {subscription?.status ? (
              <span className="muted"> · {STATUS_LABELS[subscription.status] || subscription.status}</span>
            ) : null}
          </div>
        </div>
        <div className="field">
          <label>만료 기한</label>
          <div>{formatDateTime(subscription?.currentPeriodEnd)}</div>
        </div>
      </div>

      <h2>회원 탈퇴</h2>
      <div className="panel">
        <p className="muted" style={{ marginTop: 0 }}>
          탈퇴하면 연동된 쿠팡 계정, 수집된 주문·정산·반품 데이터, 직접 입력한 원가·광고비 정보가
          전부 즉시 삭제되며 되돌릴 수 없습니다. 진행 중인 유료 구독은 자동으로 함께 해지됩니다.
        </p>
        {error ? <div className="note err">{error}</div> : null}
        <div className="field">
          <label>계속하려면 "{CONFIRM_PHRASE}"를 입력하세요</label>
          <input
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder={CONFIRM_PHRASE}
            autoComplete="off"
          />
        </div>
        <button
          type="button"
          className="danger"
          disabled={busy || confirmText !== CONFIRM_PHRASE}
          onClick={withdraw}
        >
          {busy ? "탈퇴 처리 중…" : "회원 탈퇴"}
        </button>
        <div className="guide-note muted" style={{ marginTop: 12 }}>
          관리자 계정이거나 처리에 문제가 있다면 <Link to={contactPath("회원 탈퇴 요청")}>문의하기</Link>로
          연락해 주세요.
        </div>
      </div>
    </div>
  );
}
