import { NavLink } from "react-router-dom";
import { useAuth } from "../auth.jsx";

export default function Nav() {
  const { user, logout } = useAuth();
  return (
    <div className="nav">
      <div className="nav-inner">
        <span className="brand">셀러프로핏</span>
        <NavLink to="/dashboard" className={({ isActive }) => (isActive ? "active" : "")}>
          대시보드
        </NavLink>
        <NavLink to="/accounts" className={({ isActive }) => (isActive ? "active" : "")}>
          계정 연동
        </NavLink>
        <NavLink to="/ad-roi" className={({ isActive }) => (isActive ? "active" : "")}>
          광고 ROI
        </NavLink>
        <NavLink to="/pricing" className={({ isActive }) => (isActive ? "active" : "")}>
          요금제
        </NavLink>
        {user?.role === "ADMIN" ? (
          <NavLink to="/admin" className={({ isActive }) => (isActive ? "active" : "")}>
            관리자
          </NavLink>
        ) : null}
        <span className="spacer" />
        <span className="who">{user?.email}</span>
        <button className="ghost" onClick={logout}>
          로그아웃
        </button>
      </div>
    </div>
  );
}
