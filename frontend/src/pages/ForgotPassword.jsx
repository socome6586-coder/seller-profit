import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { usePageTitle } from "../hooks/usePageTitle";

export default function ForgotPassword() {
  usePageTitle("비밀번호 찾기");
  const [email, setEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await api("/api/auth/password-reset/request", { method: "POST", body: { email: email.trim() } });
      // 가입 여부와 무관하게 서버가 항상 204 를 준다(계정 열거 방지) — 화면도 항상 성공으로 안내.
      setSent(true);
    } catch (err) {
      setError(err.message || "요청에 실패했습니다. 잠시 후 다시 시도해주세요.");
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
        <h1>비밀번호 찾기</h1>
        {sent ? (
          <>
            <div className="note ok">
              입력하신 이메일로 가입된 계정이 있다면, 비밀번호 재설정 링크를 보냈습니다.
              메일함(스팸함 포함)을 확인해주세요. 링크는 30분간 유효합니다.
            </div>
            <div className="auth-switch">
              <Link to="/login">로그인으로 돌아가기</Link>
            </div>
          </>
        ) : (
          <form onSubmit={onSubmit}>
            <p className="field-hint" style={{ marginBottom: 16 }}>
              가입하신 이메일 주소를 입력하시면 비밀번호 재설정 링크를 보내드립니다.
            </p>
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
            {error ? <div className="note err">{error}</div> : null}
            <button type="submit" disabled={busy}>
              {busy ? "전송 중…" : "재설정 링크 보내기"}
            </button>
            <div className="auth-switch">
              <Link to="/login">로그인으로 돌아가기</Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
