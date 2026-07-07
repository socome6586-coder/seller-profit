// 랜딩 페이지 전용 인라인 SVG 아이콘/일러스트 모음. 외부 아이콘 라이브러리 의존성 없이
// 참고 목업(사용자가 제공한 이미지 샘플)의 플랫 스타일 배지 아이콘을 최소 벡터로 재현한다.
// 전부 aria-hidden — 장식/보조 시각 요소이며 옆의 텍스트가 실제 정보를 전달한다.

function Badge({ bg, children, size = 44 }) {
  return (
    <span className="l-icon-badge" style={{ background: bg, width: size, height: size }} aria-hidden="true">
      {children}
    </span>
  );
}

export function IconTrendUp({ bg = "#E7F7EF" }) {
  return (
    <Badge bg={bg}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
        <path d="M3 17l6-6 4 4 8-9" stroke="#1FAE5D" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M15 6h6v6" stroke="#1FAE5D" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </Badge>
  );
}

export function IconPieChart({ bg = "#E7F7EF" }) {
  return (
    <Badge bg={bg}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
        <path d="M12 3v9l7.8 4.5" stroke="#1FAE5D" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="12" cy="12" r="9" stroke="#1FAE5D" strokeWidth="2.4" />
      </svg>
    </Badge>
  );
}

export function IconWallet({ bg = "#FDEAE3" }) {
  return (
    <Badge bg={bg}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
        <rect x="3" y="6" width="18" height="13" rx="2.4" stroke="#FF5630" strokeWidth="2.2" />
        <path d="M3 10h13.5a2.5 2.5 0 0 1 0 5H16a1.5 1.5 0 0 1 0-3h1" stroke="#FF5630" strokeWidth="2.2" strokeLinecap="round" />
      </svg>
    </Badge>
  );
}

export function IconLinkApi({ bg = "#EAF1FF" }) {
  return (
    <Badge bg={bg} size={56}>
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
        <path d="M9 15l6-6" stroke="#3B6EF6" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M11 6.5l1-1a3.5 3.5 0 015 5l-1 1" stroke="#3B6EF6" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M13 17.5l-1 1a3.5 3.5 0 01-5-5l1-1" stroke="#3B6EF6" strokeWidth="2.2" strokeLinecap="round" />
      </svg>
    </Badge>
  );
}

export function IconDatabase({ bg = "#F1EAFE" }) {
  return (
    <Badge bg={bg} size={56}>
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
        <ellipse cx="12" cy="6" rx="7" ry="2.6" stroke="#8B5CF6" strokeWidth="2.1" />
        <path d="M5 6v12c0 1.4 3.1 2.6 7 2.6s7-1.2 7-2.6V6" stroke="#8B5CF6" strokeWidth="2.1" />
        <path d="M5 12c0 1.4 3.1 2.6 7 2.6s7-1.2 7-2.6" stroke="#8B5CF6" strokeWidth="2.1" />
      </svg>
    </Badge>
  );
}

export function IconCalcWon({ bg = "#E7F7EF" }) {
  return (
    <Badge bg={bg} size={56}>
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
        <rect x="4" y="3" width="16" height="18" rx="2.2" stroke="#1FAE5D" strokeWidth="2.1" />
        <path d="M8 8h8" stroke="#1FAE5D" strokeWidth="2.1" strokeLinecap="round" />
        <path d="M7.5 12.2h9M7.5 14.8h9" stroke="#1FAE5D" strokeWidth="1.8" strokeLinecap="round" />
        <path d="M9.5 12.2l3-2M9.5 14.8l3 2" stroke="#1FAE5D" strokeWidth="1.8" strokeLinecap="round" />
      </svg>
    </Badge>
  );
}

export function IconCsvUpload({ bg = "#EAF1FF" }) {
  return (
    <Badge bg={bg} size={56}>
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
        <path d="M7 3h7l4 4v13.5A1.5 1.5 0 0116.5 22h-9A1.5 1.5 0 016 20.5V4.5A1.5 1.5 0 017.5 3z" stroke="#3B6EF6" strokeWidth="2" />
        <path d="M14 3v4h4" stroke="#3B6EF6" strokeWidth="2" strokeLinejoin="round" />
        <path d="M12 12v6M9.2 15.2L12 12l2.8 3.2" stroke="#3B6EF6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </Badge>
  );
}

export function IconMonitorWarning({ bg = "#F1EAFE" }) {
  return (
    <Badge bg={bg} size={56}>
      <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
        <rect x="3" y="4.5" width="18" height="12" rx="1.8" stroke="#8B5CF6" strokeWidth="2" />
        <path d="M8 20h8M12 16.5V20" stroke="#8B5CF6" strokeWidth="2" strokeLinecap="round" />
        <path d="M6.5 13.5l3-4 2.2 2.6 3.3-4.4 2.5 3.2" stroke="#8B5CF6" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </Badge>
  );
}

// 밸류 카드 아이콘 4개 — 원형 배지 없이 큼직하게, 듀오톤(연한 채움 + 굵은 선)으로
// 요즘 SaaS 랜딩에서 흔한 스타일(Linear/Notion류)에 맞춘 버전. 배경 원 없이 아이콘 자체가 주인공.
export function IconDocCheck() {
  return (
    <svg className="l-card-icon" width="40" height="40" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M7 2.5h7l4 4V20a1.6 1.6 0 01-1.6 1.6H7A1.6 1.6 0 015.4 20V4.1A1.6 1.6 0 017 2.5z"
        fill="#FF5630" fillOpacity=".12" stroke="#FF5630" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M14 2.5v4h4" stroke="#FF5630" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M8.3 13.6l2.3 2.3L15.7 11" stroke="#FF5630" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function IconChartSearch() {
  return (
    <svg className="l-card-icon" width="40" height="40" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M3.5 19h17" stroke="#FF5630" strokeOpacity=".25" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M4 18l5-5.5 3.2 3L19 8" stroke="#FF5630" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="17.3" cy="17.3" r="3.6" fill="#FF5630" fillOpacity=".12" stroke="#FF5630" strokeWidth="1.6" />
      <path d="M19.9 19.9L22 22" stroke="#FF5630" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

export function IconCalcBalance() {
  return (
    <svg className="l-card-icon" width="40" height="40" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="2.6" y="2.6" width="8.2" height="8.2" rx="2" fill="#FF5630" fillOpacity=".12" stroke="#FF5630" strokeWidth="1.6" />
      <rect x="13.2" y="13.2" width="8.2" height="8.2" rx="2" fill="#FF5630" fillOpacity=".12" stroke="#FF5630" strokeWidth="1.6" />
      <path d="M4.9 6.7h3.6M15.5 17.3h3.6" stroke="#FF5630" strokeWidth="1.6" strokeLinecap="round" />
      <path d="M14.6 9.4L9.4 14.6" stroke="#FF5630" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  );
}

export function IconTarget() {
  return (
    <svg className="l-card-icon" width="40" height="40" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9.2" fill="#FF5630" fillOpacity=".08" stroke="#FF5630" strokeWidth="1.6" />
      <circle cx="12" cy="12" r="5.2" stroke="#FF5630" strokeWidth="1.6" />
      <circle cx="12" cy="12" r="1.4" fill="#FF5630" />
    </svg>
  );
}

export function IconSparkle({ className }) {
  return (
    <svg className={className} width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 2c.6 3.6 2.2 5.4 6 6-3.8.6-5.4 2.4-6 6-.6-3.6-2.2-5.4-6-6 3.8-.6 5.4-2.4 6-6z"
        fill="currentColor"
      />
    </svg>
  );
}

// 값 섹션 우측 큰 일러스트: 우상향 막대 + 라인 그래프 + 원(₩) 배지
export function IllustrationBarChart() {
  return (
    <svg className="l-illust" viewBox="0 0 420 300" fill="none" aria-hidden="true">
      <rect x="0" y="0" width="420" height="300" rx="24" fill="#FFFFFF" />
      <g opacity=".9">
        <rect x="48" y="190" width="46" height="70" rx="6" fill="#151E2A" />
        <rect x="118" y="160" width="46" height="100" rx="6" fill="#151E2A" />
        <rect x="188" y="130" width="46" height="130" rx="6" fill="#151E2A" />
        <rect x="258" y="95" width="46" height="165" rx="6" fill="#FF5630" />
      </g>
      <path
        d="M40 210 L90 175 L160 190 L230 120 L300 95 L360 55"
        stroke="#FF5630"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
      <circle cx="360" cy="55" r="7" fill="#FF5630" />
      <path d="M338 40l22 15-4 26" stroke="#FF5630" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      <circle cx="366" cy="230" r="30" fill="#151E2A" />
      <text x="366" y="239" textAnchor="middle" fontSize="22" fontWeight="700" fill="#FBF6EE">₩</text>
    </svg>
  );
}

// 최종 CTA 우측 큰 일러스트: 브라우저 창 + 라인차트 + 경고/체크 배지 + 상품 목록 + 돋보기
export function IllustrationBrowserMock() {
  return (
    <svg className="l-illust" viewBox="0 0 460 300" fill="none" aria-hidden="true">
      <rect x="6" y="6" width="448" height="288" rx="20" fill="#FFFFFF" stroke="#F0E6D6" strokeWidth="2" />
      <circle cx="34" cy="32" r="5" fill="#FFD3B0" />
      <circle cx="52" cy="32" r="5" fill="#FFE1AE" />
      <circle cx="70" cy="32" r="5" fill="#CDEBD9" />
      <line x1="6" y1="52" x2="454" y2="52" stroke="#F3EADC" strokeWidth="2" />

      <path
        d="M40 190 L100 160 L150 200 L210 120 L270 150 L330 90 L400 70"
        stroke="#FF5630"
        strokeWidth="4"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M40 190 L100 160 L150 200 L210 120 L270 150 L330 90 L400 70 L400 230 L40 230 Z"
        fill="#FFE9DE"
        opacity=".5"
      />

      <g>
        <circle cx="150" cy="200" r="16" fill="#FFFFFF" stroke="#FF5630" strokeWidth="2.4" />
        <path d="M150 193v9M150 206v.2" stroke="#FF5630" strokeWidth="2.4" strokeLinecap="round" />
      </g>
      <g>
        <circle cx="400" cy="70" r="18" fill="#151E2A" />
        <path d="M393 71l5 5 9-11" stroke="#FBF6EE" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" fill="none" />
      </g>

      <g>
        <rect x="34" y="248" width="112" height="16" rx="8" fill="#F3EADC" />
        <rect x="176" y="248" width="90" height="16" rx="8" fill="#F3EADC" />
        <rect x="296" y="248" width="70" height="16" rx="8" fill="#FFD9C7" />
      </g>

      <g>
        <circle cx="410" cy="238" r="34" fill="#151E2A" />
        <circle cx="404" cy="232" r="10" stroke="#FBF6EE" strokeWidth="2.6" fill="none" />
        <line x1="411" y1="239" x2="419" y2="247" stroke="#FBF6EE" strokeWidth="2.6" strokeLinecap="round" />
        <text x="404" y="236" textAnchor="middle" fontSize="10" fontWeight="700" fill="#FBF6EE">₩</text>
      </g>
    </svg>
  );
}
