import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 빌드 산출물을 Spring Boot 정적 리소스로 직접 내보낸다.
//  → 운영/시드 실행 시 같은 오리진(:8088)에서 SPA + API 가 함께 서빙되어
//    세션 쿠키(JSESSIONID)가 CORS 없이 그대로 동작한다.
// 개발(vite dev, :5173)에서는 /api 를 백엔드(:8088)로 프록시해 동일 환경을 만든다.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true, // 이전 빌드(해시 파일) 정리. static 에는 SPA 산출물만 둔다.
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8088",
        changeOrigin: false, // 같은 호스트로 보내 세션 쿠키 도메인 유지
      },
    },
  },
});
