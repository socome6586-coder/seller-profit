import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth.jsx";
import { checkEmailAvailable } from "../api";
import "./SignupValue.css";

// 비밀번호: 8자 이상 + 영문 포함 + (숫자 또는 특수문자) 포함 — 요즘 방식 비밀번호 정책, 백엔드
// SignupRequest 의 @Pattern 과 동일 기준(docs 없음, 코드가 유일한 정책 소스이므로 서버와 정규식 일치시킴).
const PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*[0-9!@#$%^&*()_\-+=[\]{};:'",.<>/?]).{8,}$/;
// 휴대전화번호: 01x + 7~8자리(총 10~11자리), 하이픈은 표시용이라 검증 전 제거.
const PHONE_RE = /^01[016789]\d{7,8}$/;

// 입력 중 자동으로 하이픈을 붙여준다(010-1234-5678 형태). 숫자만 최대 11자리로 제한.
function formatPhone(raw) {
  const d = raw.replace(/\D/g, "").slice(0, 11);
  if (d.length <= 3) return d;
  if (d.length <= 7) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, d.length - 4)}-${d.slice(d.length - 4)}`;
}

// 가치 패널용 인라인 SVG 아이콘 — 아이콘 라이브러리 의존성 추가 없이 최소 stroke 스타일로 직접 그림.
// 참고 목업(사용자 첨부 이미지)의 아이콘 구성(가방/카드/박스/트럭/경고/사람/자물쇠 등)을 그대로 재현.
function Icon({ children, ...props }) {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>
      {children}
    </svg>
  );
}
const MegaphoneIcon = (p) => (
  <Icon {...p}>
    <path d="M3 10v4a1 1 0 0 0 1 1h2l4 3V6L6 9H4a1 1 0 0 0-1 1z" />
    <path d="M14 8a4 4 0 0 1 0 8" />
  </Icon>
);
const LinkIcon = (p) => (
  <Icon {...p}>
    <path d="M9 12a3 3 0 0 0 3 3h3a3 3 0 0 0 0-6h-1" />
    <path d="M15 12a3 3 0 0 0-3-3H9a3 3 0 0 0 0 6h1" />
  </Icon>
);
const TrendingUpIcon = (p) => (
  <Icon {...p}>
    <polyline points="4 16 10 10 13 13 20 6" />
    <polyline points="14 6 20 6 20 12" />
  </Icon>
);
const BagIcon = (p) => (
  <Icon {...p}>
    <path d="M6 8h12l-1 12H7L6 8z" />
    <path d="M9 8V6a3 3 0 0 1 6 0v2" />
  </Icon>
);
const CardIcon = (p) => (
  <Icon {...p}>
    <rect x="3" y="6" width="18" height="12" rx="2" />
    <path d="M3 10h18" />
  </Icon>
);
const BoxIcon = (p) => (
  <Icon {...p}>
    <path d="M21 8l-9-5-9 5 9 5 9-5z" />
    <path d="M3 8v8l9 5 9-5V8" />
    <path d="M12 13v8" />
  </Icon>
);
const TruckIcon = (p) => (
  <Icon {...p}>
    <path d="M3 7h11v9H3z" />
    <path d="M14 10h4l3 3v3h-7z" />
    <circle cx="7" cy="18" r="1.6" />
    <circle cx="17.5" cy="18" r="1.6" />
  </Icon>
);
const AlertTriangleIcon = (p) => (
  <Icon {...p}>
    <path d="M12 4l9 16H3L12 4z" />
    <path d="M12 10v4" />
    <path d="M12 17h.01" />
  </Icon>
);
const PersonIcon = (p) => (
  <Icon {...p}>
    <circle cx="12" cy="8" r="3.5" />
    <path d="M5 20c0-3.6 3.1-6.5 7-6.5s7 2.9 7 6.5" />
  </Icon>
);
const LockIcon = (p) => (
  <Icon {...p}>
    <rect x="5" y="11" width="14" height="9" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </Icon>
);
// 배경에 은은하게 깔리는 대형 워터마크 그래프 — 뜬금없이 떠 있던 카드형 장식 대신
// 패널 전체의 배경 질감으로 녹아들도록 저채도/저투명도로 렌더링한다.
const WatermarkGraph = (p) => (
  <svg viewBox="0 0 400 220" fill="none" aria-hidden="true" {...p}>
    <path
      d="M0 170 L55 140 L110 158 L165 96 L220 120 L275 55 L330 78 L400 30"
      stroke="currentColor" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round"
    />
    <path
      d="M0 170 L55 140 L110 158 L165 96 L220 120 L275 55 L330 78 L400 30 L400 220 L0 220 Z"
      fill="currentColor" stroke="none"
    />
  </svg>
);

const EMAIL_IDLE = { checking: false, checkedFor: "", available: null, message: "" };

export default function Signup() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [emailCheck, setEmailCheck] = useState(EMAIL_IDLE);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function onEmailChange(e) {
    setEmail(e.target.value);
    // 이메일을 다시 고치면 이전 확인 결과는 더 이상 유효하지 않으니 초기화.
    setEmailCheck(EMAIL_IDLE);
  }

  async function onCheckEmail() {
    const value = email.trim();
    if (!value) {
      setEmailCheck({ ...EMAIL_IDLE, message: "이메일을 먼저 입력해주세요." });
      return;
    }
    setEmailCheck((s) => ({ ...s, checking: true, message: "" }));
    try {
      const available = await checkEmailAvailable(value);
      setEmailCheck({
        checking: false,
        checkedFor: value,
        available,
        message: available ? "사용 가능한 이메일입니다." : "이미 가입된 이메일입니다.",
      });
    } catch (err) {
      setEmailCheck({ ...EMAIL_IDLE, message: err.message || "확인에 실패했습니다." });
    }
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError("");

    const trimmedEmail = email.trim();
    const phoneDigits = phone.replace(/\D/g, "");

    if (!PASSWORD_RE.test(password)) {
      setError("비밀번호는 8자 이상이며 영문과 숫자 또는 특수문자를 함께 포함해야 합니다.");
      return;
    }
    if (password !== confirmPassword) {
      setError("비밀번호 확인이 일치하지 않습니다.");
      return;
    }
    if (!PHONE_RE.test(phoneDigits)) {
      setError("휴대전화번호 형식이 올바르지 않습니다.");
      return;
    }
    if (emailCheck.checkedFor === trimmedEmail && emailCheck.available === false) {
      setError("이미 가입된 이메일입니다.");
      return;
    }

    setBusy(true);
    try {
      await signup(trimmedEmail, password, phoneDigits);
      navigate("/dashboard", { replace: true });
    } catch (err) {
      setError(err.message || "가입에 실패했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-split-wrap">
      {/* 좌: 폼(기존 로직/검증 그대로) · 우: 가치 패널 — 화면을 1:1로 나누고 우측이 뷰포트 전체 높이를
          꽉 채운다(docs/signup-tasks.md T11.2, 사용자 첨부 목업 재현). 모바일에서는 1열로 접히고
          DOM 순서상 폼이 먼저 보인다. 폼 필드는 이메일+비밀번호로 불변. */}
      <div className="auth-form-pane">
        {/* 좌측도 우측만큼 세로 공간을 채우도록 상단 브랜드 · 중앙 카드 · 하단 카피 3단 구성으로 변경
            (사용자 피드백: 카드 하나만 텅 빈 화면 가운데 떠 있는 느낌이 어색함). 카피는 랜딩 푸터와
            동일한 실제 문구 재사용 — 가짜 카피 아님. auth-form-decor 는 우측 패널과 대칭되는
            저채도 배경 장식(블롭 + 워터마크 그래프, 좌우 대칭으로 뒤집음) — "로그인 창만 덩그러니"
            피드백에 대한 보강. */}
        <div className="auth-form-decor" aria-hidden="true">
          <span className="auth-form-decor-blob" />
          <WatermarkGraph className="auth-form-decor-graph" />
        </div>
        <div className="auth-form-brand">SELLER PROFIT</div>
        <div className="auth-form-center">
          <div className="auth-card">
            <h1>무료로 시작하기</h1>
            <form onSubmit={onSubmit}>
              <div className="field">
                <label>이메일</label>
                <div className="field-check-row">
                  <input
                    type="email"
                    value={email}
                    autoComplete="username"
                    onChange={onEmailChange}
                    placeholder="you@example.com"
                    required
                  />
                  <button type="button" className="ghost" onClick={onCheckEmail} disabled={emailCheck.checking}>
                    {emailCheck.checking ? "확인 중…" : "중복확인"}
                  </button>
                </div>
                {emailCheck.message ? (
                  <div className={"note " + (emailCheck.available === false ? "err" : emailCheck.available ? "ok" : "")}>
                    {emailCheck.message}
                  </div>
                ) : null}
              </div>
              <div className="field">
                <label>비밀번호</label>
                <input
                  type="password"
                  value={password}
                  autoComplete="new-password"
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <div className="field-hint">8자 이상, 영문 + 숫자 또는 특수문자 조합</div>
              </div>
              <div className="field">
                <label>비밀번호 확인</label>
                <input
                  type="password"
                  value={confirmPassword}
                  autoComplete="new-password"
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                />
                {confirmPassword && confirmPassword !== password ? (
                  <div className="note err">비밀번호가 일치하지 않습니다.</div>
                ) : null}
              </div>
              <div className="field">
                <label>휴대전화번호</label>
                <input
                  type="tel"
                  inputMode="numeric"
                  value={phone}
                  autoComplete="tel"
                  onChange={(e) => setPhone(formatPhone(e.target.value))}
                  placeholder="010-1234-5678"
                  required
                />
                <div className="field-hint">가입 확인 및 중복가입 방지에 사용돼요.</div>
              </div>
              {error ? <div className="note err">{error}</div> : null}
              <button type="submit" disabled={busy}>
                {busy ? "가입 중…" : "무료 가입"}
              </button>
            </form>
            <div className="auth-switch">
              이미 계정이 있으신가요? <Link to="/login">로그인</Link>
            </div>
          </div>
        </div>
        <div className="auth-form-foot">쿠팡 셀러를 위한 진짜 순이익 분석 · 얼리액세스 단계</div>
      </div>

      <aside className="av-panel">
        <div className="av-decor" aria-hidden="true">
          <span className="av-decor-blob-a" />
          <span className="av-decor-blob-b" />
          <span className="av-decor-dots" />
          <WatermarkGraph className="av-decor-graph" />
        </div>

        <div className="av-content">
        <h2 className="av-title">
          <span className="av-reveal av-d1">이젠 진짜 순이익부터</span>
          <br />
          <span className="av-title-hl av-reveal av-d2">확인하세요</span>
        </h2>

        <p className="av-sub av-reveal av-d3">
          상품별 손익 구조를 한눈에 보고,
          <br />
          광고를 더 써도 되는지 빠르게 판단해보세요.
        </p>

        <div className="av-receipt av-reveal av-d4" role="img"
          aria-label="적자상품 예시: ROAS 13.5배지만 진짜 순이익은 마이너스 159,737원">
          <div className="av-receipt-head">
            <span className="av-receipt-icon"><BagIcon width="16" height="16" /></span>
            <span className="av-receipt-name">적자상품 B · 이번 달</span>
            <span className="av-receipt-roas">ROAS 13.5×</span>
          </div>
          <div className="av-receipt-body">
            <div className="av-receipt-line">
              <span className="av-receipt-icon sm"><CardIcon width="15" height="15" /></span>
              <span className="lab">매출 (정산 실수령)</span>
              <span className="val">₩270,000</span>
            </div>
            <div className="av-receipt-line">
              <span className="av-receipt-icon sm"><BoxIcon width="15" height="15" /></span>
              <span className="lab">매입원가 (45개)</span>
              <span className="val">−₩405,000</span>
            </div>
            <div className="av-receipt-line">
              <span className="av-receipt-icon sm"><TruckIcon width="15" height="15" /></span>
              <span className="lab">배분 기타비용</span>
              <span className="val">−₩4,737</span>
            </div>
            <div className="av-receipt-line">
              <span className="av-receipt-icon sm"><MegaphoneIcon width="15" height="15" /></span>
              <span className="lab">광고비</span>
              <span className="val">−₩20,000</span>
            </div>
          </div>
          <div className="av-receipt-total">
            <span className="lab">진짜 순이익</span>
            <span className="val">−₩159,737</span>
          </div>
          <div className="av-receipt-warn">
            <AlertTriangleIcon width="15" height="15" />
            적자 · 광고 켤수록 손해!
          </div>
        </div>

        {/* 가입→계정연동→순이익확인 3단계 흐름(사용자 첨부 목업 재현). 아래 av-footer 카드 바로 위에
            배치 — 세로 공간이 더 늘어나는 만큼 SignupValue.css 쪽에서 다른 세로 여백을 상쇄 축소해
            뷰포트 높이(100vh) 안에 다시 들어오도록 맞췄다(스크롤 재발 방지). */}
        <div className="av-steps av-reveal av-d5" aria-hidden="true">
          <div className="av-step">
            <span className="av-step-num">1</span>
            <span className="av-step-icon"><LinkIcon width="26" height="26" /></span>
            <span className="av-step-label">가입</span>
          </div>
          <div className="av-step">
            <span className="av-step-num">2</span>
            <span className="av-step-icon"><PersonIcon width="26" height="26" /></span>
            <span className="av-step-label">쿠팡 계정 연동</span>
          </div>
          <div className="av-step">
            <span className="av-step-num">3</span>
            <span className="av-step-icon"><TrendingUpIcon width="26" height="26" /></span>
            <span className="av-step-label">진짜 순이익 확인</span>
          </div>
        </div>

        <div className="av-footer av-reveal av-d6">
          <div className="av-footer-col">
            <span className="av-footer-icon"><LinkIcon width="17" height="17" /></span>
            <div className="av-footer-title">
              가입 후
              <br />
              쿠팡 계정 연동
            </div>
            <div className="av-footer-desc">
              몇 번의 클릭으로
              <br />
              간편하게 연결하세요.
            </div>
          </div>
          <div className="av-footer-col">
            <span className="av-footer-icon"><PersonIcon width="17" height="17" /></span>
            <div className="av-footer-title">
              쿠팡 판매자
              <br />
              계정만 있으면
              <br />
              시작 가능
            </div>
            <div className="av-footer-desc">
              별도 준비 없이
              <br />
              바로 이용할 수 있어요.
            </div>
          </div>
          <div className="av-footer-col">
            <span className="av-footer-icon"><LockIcon width="17" height="17" /></span>
            <div className="av-footer-title">무료로 시작</div>
            <div className="av-footer-desc">
              · 카드 등록 불필요
              <br />
              · AES-256 암호화
            </div>
          </div>
        </div>
        </div>
      </aside>
    </div>
  );
}
