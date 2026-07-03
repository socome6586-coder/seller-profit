import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import ReceiptCard from "../components/ReceiptCard.jsx";

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
          <ReceiptCard />
          <div className="auth-value-notes">
            <div className="auth-value-note">
              <span className="k">다음 단계</span>
              <span className="v">가입 → 쿠팡 계정 연동 → 진짜 순이익 확인</span>
            </div>
            <div className="auth-value-note">
              <span className="k">필요한 것</span>
              <span className="v">쿠팡 판매자 계정만 있으면 시작할 수 있어요.</span>
            </div>
            <div className="auth-value-note">
              <span className="k">안심 문구</span>
              <span className="v">무료로 시작 · 카드 등록 불필요 · 연동 시 API 키 AES-256 암호화</span>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
