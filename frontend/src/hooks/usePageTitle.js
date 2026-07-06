import { useEffect } from "react";

// 페이지별 <title> 을 설정한다(docs/trust-legal-tasks.md T15.3). 이전엔 index.html 의 정적
// "…대시보드" 제목이 랜딩/로그인/가입 등 모든 화면에 그대로 고정돼 있었다 — 브라우저 탭/북마크/
// 공유 시 페이지 구분이 안 되는 문제라 각 페이지 컴포넌트에서 이 훅으로 덮어쓴다.
// 언마운트 시 원상복구는 하지 않는다 — 어차피 다음 페이지가 마운트되며 다시 설정하기 때문
// (SPA 라우트 전환마다 매번 새 컴포넌트가 이 훅을 호출).
const SUFFIX = " · 셀러프로핏";

export function usePageTitle(title) {
  useEffect(() => {
    document.title = title ? `${title}${SUFFIX}` : "셀러프로핏 — 쿠팡 순이익 대시보드";
  }, [title]);
}
