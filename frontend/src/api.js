// API 호출 헬퍼. 같은 오리진이라 credentials 기본 동작(세션 쿠키 자동 첨부)이지만
// 명시적으로 same-origin 을 둬 의도를 드러낸다.

async function parseError(res) {
  try {
    const j = await res.json();
    return j.error || `HTTP ${res.status}`;
  } catch {
    return `HTTP ${res.status}`;
  }
}

/** JSON 요청. 실패(>=400) 시 서버 error 메시지를 담은 Error 를 던진다. status 도 붙인다. */
export async function api(path, { method = "GET", body } = {}) {
  const res = await fetch(path, {
    method,
    credentials: "same-origin",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return null;
  if (!res.ok) {
    const err = new Error(await parseError(res));
    err.status = res.status;
    throw err;
  }
  // 일부 엔드포인트는 빈 본문일 수 있음
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

export const won = (n) => (n == null ? "–" : "₩" + Number(n).toLocaleString("ko-KR"));
export const pct = (n) => (n == null ? "–" : Number(n).toFixed(1) + "%");
export const num = (n) => Number(n || 0).toLocaleString("ko-KR");
export const signClass = (n) => (Number(n) < 0 ? "neg" : "pos");
