import { Link } from "react-router-dom";
import { usePageTitle } from "../hooks/usePageTitle";

// /privacy, /terms 공용 뼈대. 두 페이지 모두 비로그인 방문자(랜딩 푸터·회원가입 화면)에서
// 들어올 수 있어 전역 Nav(App.jsx, user 로그인시에만 렌더)가 없을 수 있다 — 그래서 자체
// "← 홈으로" 링크를 둔다. 로그인 상태로 들어오면 Nav 가 이미 위에 있으니 이 링크는 중복이어도
// 무해하다(docs/trust-legal-tasks.md T14.1).
export default function LegalPage({ title, updated, children }) {
  // Privacy/Terms 둘 다 이 뼈대를 공유하므로 여기 한 곳에서만 <title> 설정하면 충분(docs/trust-legal-tasks.md T15.3).
  usePageTitle(title);
  return (
    <div className="wrap legal-page">
      <div className="legal-back">
        <Link to="/">← 홈으로</Link>
      </div>
      <h1>{title}</h1>
      {updated ? <div className="legal-updated">{updated}</div> : null}
      {children}
    </div>
  );
}

// 조민석 님이 직접 채워야 하는 항목(연락처, 사업자 정보, 시행일자 등)을 눈에 띄게 표시.
// 실제 게시 전 반드시 이 표시가 하나도 남지 않도록 대체해야 한다(docs/trust-legal-tasks.md T14 전제).
export function LegalTodo({ children }) {
  return <mark className="legal-todo">[작성 필요: {children}]</mark>;
}
