import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth } from "./auth.jsx";
import Nav from "./components/Nav.jsx";
import Login from "./pages/Login.jsx";
import Signup from "./pages/Signup.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import Pricing from "./pages/Pricing.jsx";

// 로그인 필요한 라우트의 '벽'. 확인 중이면 로딩, 비로그인이면 /login 으로.
function Protected({ children }) {
  const { user } = useAuth();
  if (user === undefined) return <div className="center-msg">불러오는 중…</div>;
  if (user === null) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  const { user } = useAuth();
  return (
    <>
      {/* 인증된 화면에서만 상단 네비를 보인다. */}
      {user ? <Nav /> : null}
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/" replace /> : <Login />} />
        <Route path="/signup" element={user ? <Navigate to="/" replace /> : <Signup />} />
        <Route
          path="/"
          element={
            <Protected>
              <Dashboard />
            </Protected>
          }
        />
        <Route
          path="/pricing"
          element={
            <Protected>
              <Pricing />
            </Protected>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
