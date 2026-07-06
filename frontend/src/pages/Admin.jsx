import { useCallback, useEffect, useState } from "react";
import { api } from "../api";
import { usePageTitle } from "../hooks/usePageTitle";

// 관리자 전용 화면(T10.5). 서버가 모든 /api/admin/** 를 role 로 강제하므로(AdminAccess),
// 이 화면의 조건부 노출은 방어가 아니라 편의다 — 실제 방어는 App.jsx 의 라우트 가드 + 서버.
//  - 유저 표: role/plan/source/만료일 + PRO N개월 무상 지급(source=COMP, 결제와 분리)
//  - role 토글(마지막 ADMIN 잠금/자기 자신 해제는 서버가 400 으로 막고 메시지를 그대로 보여준다)
//  - COMP 회수(PAID 는 대상 아님 → 버튼 비활성화 + 서버도 400)
//  - 감사 로그(누가/언제/누구에게/무엇을)
export default function Admin() {
  usePageTitle("관리자");
  const [users, setUsers] = useState(null);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const [emailInput, setEmailInput] = useState("");
  const [email, setEmail] = useState("");
  const [months, setMonths] = useState({});
  const [busyId, setBusyId] = useState(null);
  const [note, setNote] = useState(null);
  const [audit, setAudit] = useState(null);

  const loadUsers = useCallback(async (p, q) => {
    try {
      const qs = new URLSearchParams({ page: String(p), size: "20" });
      if (q) qs.set("email", q);
      const res = await api("/api/admin/users?" + qs.toString());
      setUsers(res.content);
      setTotalPages(res.totalPages || 1);
    } catch (e) {
      setUsers([]);
      setNote({ ok: false, msg: e.message });
    }
  }, []);

  const loadAudit = useCallback(async () => {
    try {
      const res = await api("/api/admin/audit?page=0&size=20");
      setAudit(res.content);
    } catch {
      setAudit([]);
    }
  }, []);

  useEffect(() => {
    loadUsers(page, email);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, email]);

  useEffect(() => {
    loadAudit();
  }, [loadAudit]);

  function search(e) {
    e.preventDefault();
    setPage(0);
    setEmail(emailInput.trim());
  }

  async function withBusy(userId, fn) {
    setNote(null);
    setBusyId(userId);
    try {
      await fn();
      loadUsers(page, email);
      loadAudit();
    } catch (e) {
      setNote({ ok: false, msg: e.message });
    } finally {
      setBusyId(null);
    }
  }

  function grant(u) {
    const raw = months[u.userId];
    const m = parseInt(raw, 10);
    if (!raw || !Number.isFinite(m) || m <= 0) {
      setNote({ ok: false, msg: "지급할 개월 수를 1 이상으로 입력하세요." });
      return;
    }
    withBusy(u.userId, async () => {
      await api(`/api/admin/users/${u.userId}/grant`, { method: "POST", body: { months: m, plan: "PRO" } });
      setNote({ ok: true, msg: `${u.email} 에게 PRO ${m}개월을 지급했습니다.` });
      setMonths((s) => ({ ...s, [u.userId]: "" }));
    });
  }

  function revoke(u) {
    if (!window.confirm(`${u.email} 의 무상(COMP) 지급을 회수하고 FREE 로 되돌릴까요?`)) return;
    withBusy(u.userId, async () => {
      await api(`/api/admin/users/${u.userId}/revoke`, { method: "POST" });
      setNote({ ok: true, msg: `${u.email} 을(를) 회수했습니다.` });
    });
  }

  function toggleRole(u) {
    const next = u.role === "ADMIN" ? "USER" : "ADMIN";
    if (!window.confirm(`${u.email} 의 role 을 ${next} 로 변경할까요?`)) return;
    withBusy(u.userId, async () => {
      await api(`/api/admin/users/${u.userId}/role`, { method: "POST", body: { role: next } });
      setNote({ ok: true, msg: `${u.email} 의 role 을 ${next} 로 변경했습니다.` });
    });
  }

  return (
    <div className="wrap">
      <h1>관리자</h1>
      <div className="sub">
        유저 조회, PRO 무상 지급(COMP), role 변경, 감사 로그 확인. 결제(PAID) 구독에는 영향을 주지 않습니다.
      </div>

      {note ? <div className={"note " + (note.ok ? "ok" : "err")} role="status">{note.msg}</div> : null}

      <form className="controls" onSubmit={search}>
        <div className="field">
          <label htmlFor="admin-email-search">이메일 검색</label>
          <input
            id="admin-email-search"
            value={emailInput}
            onChange={(e) => setEmailInput(e.target.value)}
            placeholder="이메일 일부 입력"
          />
        </div>
        <button type="submit">검색</button>
      </form>

      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>이메일</th>
              <th>role</th>
              <th>plan</th>
              <th>source</th>
              <th>만료일</th>
              <th>PRO 지급</th>
              <th>role / 회수</th>
            </tr>
          </thead>
          <tbody>
            {users == null ? (
              <tr><td colSpan={7} className="empty">불러오는 중…</td></tr>
            ) : users.length === 0 ? (
              <tr><td colSpan={7} className="empty">일치하는 유저가 없습니다.</td></tr>
            ) : (
              users.map((u) => (
                <tr key={u.userId}>
                  <td>{u.email}</td>
                  <td>
                    <span className={"role-badge" + (u.role === "ADMIN" ? " admin" : "")}>{u.role}</span>
                  </td>
                  <td>{u.plan}</td>
                  <td className="muted">{u.source}</td>
                  <td className="muted">{u.currentPeriodEnd ? u.currentPeriodEnd.slice(0, 10) : "–"}</td>
                  <td>
                    <label className="sr-only" htmlFor={`months-${u.userId}`}>
                      {u.email} PRO 지급 개월 수
                    </label>
                    <input
                      id={`months-${u.userId}`}
                      className="grant-input"
                      type="number"
                      min="1"
                      inputMode="numeric"
                      value={months[u.userId] || ""}
                      onChange={(e) => setMonths((s) => ({ ...s, [u.userId]: e.target.value }))}
                    />
                    <button onClick={() => grant(u)} disabled={busyId === u.userId}>
                      지급
                    </button>
                  </td>
                  <td>
                    <button
                      className="ghost"
                      onClick={() => toggleRole(u)}
                      disabled={busyId === u.userId}
                      style={{ marginRight: 6 }}
                    >
                      {u.role === "ADMIN" ? "USER 로" : "ADMIN 으로"}
                    </button>
                    <button
                      className="ghost"
                      onClick={() => revoke(u)}
                      disabled={busyId === u.userId || u.source !== "COMP"}
                      title={u.source !== "COMP" ? "무상(COMP) 지급만 회수할 수 있습니다." : undefined}
                    >
                      회수
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="pager">
        <button className="ghost" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page <= 0}>
          이전
        </button>
        <span className="muted">{page + 1} / {totalPages}</span>
        <button
          className="ghost"
          onClick={() => setPage((p) => p + 1)}
          disabled={page + 1 >= totalPages}
        >
          다음
        </button>
      </div>

      <h2>감사 로그</h2>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              <th>일시</th>
              <th>관리자</th>
              <th>동작</th>
              <th>대상</th>
              <th>상세</th>
            </tr>
          </thead>
          <tbody>
            {audit == null ? (
              <tr><td colSpan={5} className="empty">불러오는 중…</td></tr>
            ) : audit.length === 0 ? (
              <tr><td colSpan={5} className="empty">기록이 없습니다.</td></tr>
            ) : (
              audit.map((a) => (
                <tr key={a.id}>
                  <td className="muted">{a.createdAt ? a.createdAt.slice(0, 19).replace("T", " ") : "–"}</td>
                  <td className="muted">#{a.adminUserId}</td>
                  <td>{a.action}</td>
                  <td className="muted">#{a.targetUserId}</td>
                  <td className="muted">{JSON.stringify(a.detail)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
