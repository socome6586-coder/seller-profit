import "./ReceiptCard.css";

// 랜딩 히어로의 정산 명세서 시그니처를 다른 화면(회원가입 등)에서도 재사용하기 위해 추출한 공용 컴포넌트.
// (docs/signup-tasks.md T11.1) 기본값은 기존 랜딩과 동일한 적자상품 B 예시 — 가짜 지표 아님, 실제 랜딩 카피 그대로.
const DEFAULT_LINES = [
  { label: "매출 (정산 실수령)", value: "₩270,000" },
  { label: "− 매입원가 (45개)", value: "−₩405,000", sign: "minus" },
  { label: "− 배분 기타비용", value: "−₩4,737", sign: "minus" },
  { label: "− 광고비", value: "−₩20,000", sign: "minus" },
];

export default function ReceiptCard({
  productName = "적자상품 B · 이번 달",
  roas = "13.5×",
  lines = DEFAULT_LINES,
  totalLabel = "진짜 순이익",
  totalValue = "−₩159,737",
  flagText = "적자 · 광고 켤수록 손해!",
  ariaLabel = "적자상품 예시: ROAS 13.5배지만 진짜 순이익은 마이너스 159,737원",
}) {
  return (
    <div className="receipt" role="img" aria-label={ariaLabel}>
      <div className="receipt-top">
        <span className="r-name">{productName}</span>
        <span className="roas">ROAS {roas}</span>
      </div>
      <div className="r-body">
        {lines.map((line, i) => (
          <div className="r-line reveal" key={i}>
            <span className="lab">{line.label}</span>
            <span className={"val mono" + (line.sign === "minus" ? " minus" : "")}>{line.value}</span>
          </div>
        ))}
      </div>
      <div className="r-total">
        <span className="lab">{totalLabel}</span>
        <span className="val mono">{totalValue}</span>
      </div>
      <span className="r-flag">{flagText}</span>
    </div>
  );
}
