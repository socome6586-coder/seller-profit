// 문의 채널 단일 소스(docs/trust-legal-tasks.md T15.1). 베타 단계라 채널톡 같은 도구 없이
// mailto: 링크로 충분 — 여러 화면(Nav, Accounts, Login)에서 이메일 주소를 각자 하드코딩하면
// 나중에 바뀔 때 놓치기 쉬워 여기 한 곳에만 둔다.
export const CONTACT_EMAIL = "socome6586@gmail.com";

export function mailto(subject) {
  return `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(subject)}`;
}
