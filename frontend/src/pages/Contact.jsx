import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth.jsx";
import { usePageTitle } from "../hooks/usePageTitle";

const INITIAL = {
  name: "",
  email: "",
  subject: "",
  message: "",
};

export default function Contact() {
  usePageTitle("문의하기");
  const { user } = useAuth();
  const [params] = useSearchParams();
  const [form, setForm] = useState(() => ({
    ...INITIAL,
    subject: params.get("subject") || "",
  }));
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (user?.email && !form.email) {
      setForm((current) => ({ ...current, email: user.email }));
    }
  }, [user?.email, form.email]);

  function update(field) {
    return (e) => setForm((current) => ({ ...current, [field]: e.target.value }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await api("/api/contact", {
        method: "POST",
        body: {
          name: form.name,
          email: form.email,
          subject: form.subject,
          message: form.message,
          context: `${window.location.pathname}${window.location.search}`,
        },
      });
      setSent(true);
      setForm((current) => ({ ...INITIAL, email: current.email }));
    } catch (err) {
      setError(err.message || "문의 전송에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="contact-page">
      <div className="contact-shell">
        <div className="contact-copy">
          <Link className="contact-back" to={user ? "/dashboard" : "/"}>
            ← 돌아가기
          </Link>
          <h1>문의하기</h1>
          <p className="sub">
            계정 연동, 정산 데이터, 요금제, 탈퇴 요청까지 필요한 내용을 남겨주세요.
            SMTP 설정이 완료되면 이 폼에서 바로 관리자 메일로 전달됩니다.
          </p>
          <div className="contact-note">
            <strong>빠른 확인을 위해</strong>
            쿠팡 업체코드, 오류가 난 화면, 대략적인 시간대를 함께 적어주시면 더 빨리 확인할 수 있어요.
          </div>
        </div>

        <div className="contact-card">
          {sent ? (
            <div className="contact-done">
              <h2>문의가 접수됐습니다.</h2>
              <p>확인 후 입력하신 이메일로 답변드릴게요.</p>
              <button type="button" onClick={() => setSent(false)}>
                새 문의 작성
              </button>
            </div>
          ) : (
            <form onSubmit={onSubmit}>
              <div className="field">
                <label>이름</label>
                <input
                  value={form.name}
                  onChange={update("name")}
                  maxLength={50}
                  placeholder="홍길동"
                  required
                />
              </div>
              <div className="field">
                <label>답변 받을 이메일</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={update("email")}
                  maxLength={255}
                  placeholder="you@example.com"
                  required
                />
              </div>
              <div className="field">
                <label>제목</label>
                <input
                  value={form.subject}
                  onChange={update("subject")}
                  maxLength={120}
                  placeholder="문의 제목"
                  required
                />
              </div>
              <div className="field">
                <label>문의 내용</label>
                <textarea
                  value={form.message}
                  onChange={update("message")}
                  maxLength={3000}
                  rows={9}
                  placeholder="어떤 부분에서 막혔는지 자세히 적어주세요."
                  required
                />
              </div>
              {error ? <div className="note err">{error}</div> : null}
              <button type="submit" disabled={busy}>
                {busy ? "전송 중…" : "문의 보내기"}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
