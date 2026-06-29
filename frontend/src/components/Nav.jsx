import { NavLink } from "react-router-dom";
import { useAuth } from "../auth.jsx";

export default function Nav() {
  const { user, logout } = useAuth();
  return (
    <div className="nav">
      <span className="brand">셀러프로핏</span>
      <NavLink to="/" end className={({ isActive }) => (isActive ? "active" : "")}>
        대시보드
      </NavLink>
      <NavLink to="/pricing" className={({ isActive }) => (isActive ? "active" : "")}>
        요금제
      </NavLink>
      <span className="spacer" />
      <span className="who">{user?.email}</span>
      <button className="ghost" onClick={logout}>
        로그아웃
      </button>
    </div>
  );
}
