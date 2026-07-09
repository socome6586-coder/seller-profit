import { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import { mailto } from "../contact";

// 모바일(<=720px, styles.css 참고)에서는 링크가 6~7개라 한 줄에 다 못 들어가 글자 단위로
// 줄바꿈되며 깨지는 문제가 있었다. 좁은 화면에서는 햄버거 버튼으로 접어두고, 눌렀을 때만
// 링크를 세로 목록(드롭다운)으로 펼친다. 데스크톱(>720px)은 기존 가로 배치 그대로 유지.
export default function Nav() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();

  // 링크를 눌러 페이지가 바뀌면 드롭다운을 자동으로 닫는다(열린 채로 남아있으면 다음
  // 페이지 콘텐츠를 가려버림).
  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  const linkClass = ({ isActive }) => (isActive ? "active" : "");

  return (
    <div className="nav">
      <div className="nav-inner">
        <span className="brand">SELLER PROFIT</span>
        <button
          type="button"
          className="nav-burger"
          aria-label={open ? "메뉴 닫기" : "메뉴 열기"}
          aria-expanded={open}
          onClick={() => setOpen((v) => !v)}
        >
          <span />
          <span />
          <span />
        </button>
        <div className={"nav-links" + (open ? " is-open" : "")}>
          <NavLink to="/dashboard" className={linkClass}>대시보드</NavLink>
          <NavLink to="/accounts" className={linkClass}>계정 연동</NavLink>
          <NavLink to="/ad-roi" className={linkClass}>광고 ROI</NavLink>
          <NavLink to="/pricing" className={linkClass}>요금제</NavLink>
          <NavLink to="/mypage" className={linkClass}>마이페이지</NavLink>
          {user?.role === "ADMIN" ? (
            <NavLink to="/admin" className={linkClass}>관리자</NavLink>
          ) : null}
          <span className="spacer" />
          {/* 문의 채널(docs/trust-legal-tasks.md T15.1) — 베타 단계라 mailto 로 충분, 전 화면 공통 nav 에 상시 노출. */}
          <a className="nav-contact" href={mailto("seller-profit 문의")}>
            문의하기
          </a>
          <span className="who">{user?.email}</span>
          <button className="ghost" onClick={logout}>
            로그아웃
          </button>
        </div>
      </div>
    </div>
  );
}
