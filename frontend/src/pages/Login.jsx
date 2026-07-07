import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import { usePageTitle } from "../hooks/usePageTitle";

// 아이디(이메일) 저장 시 브라우저에 남겨두는 키. 비밀번호는 절대 여기 저장하지 않는다.
const REMEMBERED_EMAIL_KEY = "sp_remembered_email";

export default function Login() {
  usePageTitle("로그인");
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState(() => localStorage.getItem(REMEMBERED_EMAIL_KEY) || "");
  const [password, setPassword] = useState("");
  const [rememberEmail, setRememberEmail] = useState(() => !!localStorage.getItem(REMEMBERED_EMAIL_KEY));
  const [autoLogin, setAutoLogin] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    // 저장된 아이디가 있으면 자동로그인도 함께 체크해두는 게 자연스러운 기본값.
    if (localStorage.getItem(REMEMBERED_EMAIL_KEY)) setAutoLogin(true);
  }, []);

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const trimmed = email.trim();
      if (rememberEmail) {
        localStorage.setItem(REMEMBERED_EMAIL_KEY, trimmed);
      } else {
        localStorage.removeItem(REMEMBERED_EMAIL_KEY);
      }
      await login(trimmed, password, autoLogin);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err.message || "로그인에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-bg" aria-hidden="true">
        <span className="auth-bg-blob-b" />
      </div>
      <div className="auth-brand">
        <span className="auth-brand-name">SELLER PROFIT</span>
        <span className="auth-brand-tag">쿠팡 셀러의 진짜 순이익</span>
      </div>
      <div className="auth-card">
        <h1>로그인</h1>
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
            <label>비밀번호</label>
            <input
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <div className="auth-options">
            <label className="auth-check">
              <input
                type="checkbox"
                checked={rememberEmail}
                onChange={(e) => setRememberEmail(e.target.checked)}
              />
              아이디 저장
            </label>
            <label className="auth-check">
              <input
                type="checkbox"
                checked={autoLogin}
                onChange={(e) => setAutoLogin(e.target.checked)}
              />
              자동 로그인
            </label>
          </div>
          <div className="auth-forgot">
            <Link to="/forgot-password">비밀번호를 잊으셨나요? 찾기</Link>
          </div>
          {error ? <div className="note err">{error}</div> : null}
          <button type="submit" disabled={busy}>
            {busy ? "로그인 중…" : "로그인"}
          </button>
        </form>
        <div className="auth-switch">
          계정이 없으신가요? <Link to="/signup">무료로 시작하기</Link>
        </div>
      </div>
    </div>
  );
}
