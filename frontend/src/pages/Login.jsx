import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import { usePageTitle } from "../hooks/usePageTitle";
import { mailto } from "../contact";

export default function Login() {
  usePageTitle("로그인");
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await login(email.trim(), password);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err.message || "로그인에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-wrap">
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
          {/* 비밀번호 재설정 기능은 아직 없다(이메일 발송 인프라 미구축, docs/trust-legal-tasks.md
              T15.2). 정식 기능 전까지 문의 채널로 임시 안내. */}
          <div className="auth-forgot">
            <a href={mailto("비밀번호 재설정 문의")}>비밀번호를 잊으셨나요? 문의하기</a>
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
