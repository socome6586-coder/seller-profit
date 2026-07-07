import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { usePageTitle } from "../hooks/usePageTitle";

export default function ResetPassword() {
  usePageTitle("비밀번호 재설정");
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState("");

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    if (password !== confirm) {
      setError("비밀번호가 서로 일치하지 않습니다.");
      return;
    }
    setBusy(true);
    try {
      await api("/api/auth/password-reset/confirm", { method: "POST", body: { token, newPassword: password } });
      setDone(true);
    } catch (err) {
      setError(err.message || "재설정에 실패했습니다.");
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
        <h1>비밀번호 재설정</h1>
        {!token ? (
          <div className="note err">
            유효하지 않은 링크입니다. <Link to="/forgot-password">비밀번호 찾기</Link>를 다시 진행해주세요.
          </div>
        ) : done ? (
          <>
            <div className="note ok">비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.</div>
            <div className="auth-switch">
              <Link to="/login">로그인하러 가기</Link>
            </div>
          </>
        ) : (
          <form onSubmit={onSubmit}>
            <div className="field">
              <label>새 비밀번호</label>
              <input
                type="password"
                value={password}
                autoComplete="new-password"
                onChange={(e) => setPassword(e.target.value)}
                minLength={8}
                required
              />
              <p className="field-hint">영문과 함께 숫자 또는 특수문자를 포함, 8자 이상.</p>
            </div>
            <div className="field">
              <label>새 비밀번호 확인</label>
              <input
                type="password"
                value={confirm}
                autoComplete="new-password"
                onChange={(e) => setConfirm(e.target.value)}
                minLength={8}
                required
              />
            </div>
            {error ? <div className="note err">{error}</div> : null}
            <button type="submit" disabled={busy}>
              {busy ? "변경 중…" : "비밀번호 변경"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
