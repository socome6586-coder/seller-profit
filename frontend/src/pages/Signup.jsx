import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import ReceiptCard from "../components/ReceiptCard.jsx";

// 가치 패널용 인라인 SVG 아이콘 — 아이콘 라이브러리 의존성 추가 없이 최소 stroke 스타일로 직접 그림.
function Icon({ children, ...props }) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>
      {children}
    </svg>
  );
}
const MegaphoneIcon = (p) => (
  <Icon {...p}>
    <path d="M3 10v4a1 1 0 0 0 1 1h2l4 3V6L6 9H4a1 1 0 0 0-1 1z" />
    <path d="M14 8a4 4 0 0 1 0 8" />
  </Icon>
);
const UserPlusIcon = (p) => (
  <Icon {...p}>
    <circle cx="9" cy="8" r="3" />
    <path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6" />
    <path d="M18 8v4M16 10h4" />
  </Icon>
);
const LinkIcon = (p) => (
  <Icon {...p}>
    <path d="M9 12a3 3 0 0 0 3 3h3a3 3 0 0 0 0-6h-1" />
    <path d="M15 12a3 3 0 0 0-3-3H9a3 3 0 0 0 0 6h1" />
  </Icon>
);
const TrendingUpIcon = (p) => (
  <Icon {...p}>
    <polyline points="4 16 10 10 13 13 20 6" />
    <polyline points="14 6 20 6 20 12" />
  </Icon>
);
const CheckCircleIcon = (p) => (
  <Icon {...p}>
    <circle cx="12" cy="12" r="9" />
    <polyline points="8 12 11 15 16 9" />
  </Icon>
);
const GiftIcon = (p) => (
  <Icon {...p}>
    <rect x="4" y="9" width="16" height="11" rx="1" />
    <path d="M4 9h16M12 9v11" />
    <path d="M12 9c-1.6 0-3-1-3-3a2 2 0 0 1 4 0c0 1 0 3-1 3zM12 9c1.6 0 3-1 3-3a2 2 0 0 0-4 0c0 1 0 3 1 3z" />
  </Icon>
);
const ShieldCheckIcon = (p) => (
  <Icon {...p}>
    <path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z" />
    <polyline points="9 12 11.5 14.5 15 10" />
  </Icon>
);

export default function Signup() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    if (password.length < 8) {
      setError("비밀번호는 8자 이상이어야 합니다.");
      return;
    }
    setBusy(true);
    try {
      await signup(email.trim(), password);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err.message || "가입에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-split-wrap">
      {/* 좌: 폼(기존 로직/검증 그대로) · 우: 가치 패널(ReceiptCard+안내) — docs/signup-tasks.md T11.2.
          모바일에서는 1열로 접히고 DOM 순서상 폼이 먼저 보인다. 폼 필드는 이메일+비밀번호로 불변. */}
      <div className="auth-split">
        <div className="auth-card">
          <h1>무료로 시작하기</h1>
          <form onSubmit={onSubmit}>
            <div className="field">
              <label>이메일</label>
              <input
                type="email"
                value={email}
                autoComplete="username"
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
              />
            </div>
            <div className="field">
              <label>비밀번호 (8자 이상)</label>
              <input
                type="password"
                value={password}
                autoComplete="new-password"
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            {error ? <div className="note err">{error}</div> : null}
            <button type="submit" disabled={busy}>
              {busy ? "가입 중…" : "무료 가입"}
            </button>
          </form>
          <div className="auth-switch">
            이미 계정이 있으신가요? <Link to="/login">로그인</Link>
          </div>
        </div>

        <aside className="auth-value">
          <div className="auth-value-intro reveal-in">
            <span className="auth-value-eyebrow">
              <MegaphoneIcon />
              무료로 시작하기
            </span>
            <h2 className="auth-value-title">진짜 순이익, 가입하고 바로 확인하세요</h2>
            <p className="auth-value-sub">
              쿠팡 계정만 연동하면 광고비까지 반영한 상품별 순이익을 자동으로 보여드려요.
            </p>
          </div>

          <div className="reveal-in reveal-in-2">
            <ReceiptCard />
          </div>

          <div className="auth-value-steps reveal-in reveal-in-3">
            <div className="auth-step">
              <span className="auth-step-icon-wrap">
                <UserPlusIcon />
                <span className="auth-step-num">1</span>
              </span>
              <span className="auth-step-label">가입</span>
            </div>
            <span className="auth-step-connector" aria-hidden="true" />
            <div className="auth-step">
              <span className="auth-step-icon-wrap">
                <LinkIcon />
                <span className="auth-step-num">2</span>
              </span>
              <span className="auth-step-label">쿠팡 계정 연동</span>
            </div>
            <span className="auth-step-connector" aria-hidden="true" />
            <div className="auth-step">
              <span className="auth-step-icon-wrap">
                <TrendingUpIcon />
                <span className="auth-step-num">3</span>
              </span>
              <span className="auth-step-label">진짜 순이익 확인</span>
            </div>
          </div>

          <div className="auth-value-checks reveal-in reveal-in-4">
            <span className="auth-check">
              <CheckCircleIcon />
              쿠팡 판매자 계정만 있으면 시작
            </span>
            <span className="auth-check">
              <GiftIcon />
              무료 시작 · 카드 등록 불필요
            </span>
            <span className="auth-check">
              <ShieldCheckIcon />
              연동 시 API 키 AES-256 암호화
            </span>
          </div>
        </aside>
      </div>
    </div>
  );
}
