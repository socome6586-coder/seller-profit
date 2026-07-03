import { createContext, useContext, useEffect, useState, useCallback } from "react";
import { api } from "./api";

// 세션 기반 인증 상태. 앱 시작 시 GET /api/auth/me 로 현재 로그인 유저를 확인한다.
//  - user === undefined : 아직 확인 중(로딩)
//  - user === null      : 비로그인
//  - user === {...}     : 로그인됨
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(undefined);

  const refresh = useCallback(async () => {
    try {
      const me = await api("/api/auth/me");
      setUser(me);
    } catch {
      setUser(null); // 401 등 → 비로그인
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const login = useCallback(async (email, password) => {
    const me = await api("/api/auth/login", { method: "POST", body: { email, password } });
    setUser(me);
    return me;
  }, []);

  const signup = useCallback(async (email, password, phone) => {
    const me = await api("/api/auth/signup", { method: "POST", body: { email, password, phone } });
    // 가입 직후 자동 로그인(세션 생성)까지 이어 붙인다.
    await api("/api/auth/login", { method: "POST", body: { email, password } });
    setUser(me);
    return me;
  }, []);

  const logout = useCallback(async () => {
    await api("/api/auth/logout", { method: "POST" });
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, signup, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
