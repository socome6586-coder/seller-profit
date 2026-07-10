// 문의 채널 단일 소스(docs/trust-legal-tasks.md T15.1).
export const CONTACT_EMAIL = "socome6586@gmail.com";

export function contactPath(subject) {
  return subject ? `/contact?subject=${encodeURIComponent(subject)}` : "/contact";
}
