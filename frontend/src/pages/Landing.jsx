import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, won } from "../api";
import { useAuth } from "../auth.jsx";
import ReceiptCard from "../components/ReceiptCard.jsx";
import {
  IconTrendUp,
  IconPieChart,
  IconWallet,
  IconLinkApi,
  IconDatabase,
  IconCalcWon,
  IconCsvUpload,
  IconMonitorWarning,
  IconDocCheck,
  IconChartSearch,
  IconCalcBalance,
  IconTarget,
  IconSparkle,
  IllustrationBarChart,
  IllustrationBrowserMock,
} from "../components/LandingIcons.jsx";
import "./Landing.css";

// 공개 랜딩 페이지("/"). 비로그인 방문자에게 보여주는 첫 세일즈 자산.
// 디자인 기준: docs/landing-mockup.html (섹션 순서·카피·색/타이포 그대로 구현, 스타일은 Landing.css 로 격리).
// 가짜 지표 없음(docs/landing-page-tasks.md 절대 규칙) — 히어로의 숫자는 시드 데모(적자상품 B)와 정합.
// 요금제는 /api/plans 에서 받아 렌더(하드코딩 금지, PlanType 이 유일한 정책 소스).
// 스크롤로 뷰포트에 들어오는 요소에 .is-in 을 붙여 CSS 리빌 애니메이션을 트리거한다.
// prefers-reduced-motion 이면 애니메이션 없이 즉시 표시(접근성, docs/landing-page-tasks.md 8.4).
function useScrollReveal(dep) {
  useEffect(() => {
    // 요금제(.l-plan)는 /api/plans 응답 후에야 DOM 에 생기므로 dep(plans) 로 재실행해 다시 관찰한다.
    const targets = document.querySelectorAll(".landing .reveal-up");
    if (!targets.length) return undefined;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      targets.forEach((el) => el.classList.add("is-in"));
      return undefined;
    }
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-in");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.15, rootMargin: "0px 0px -8% 0px" }
    );
    targets.forEach((el) => io.observe(el));
    return () => io.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dep]);
}

export default function Landing() {
  const { user } = useAuth();
  const [plans, setPlans] = useState([]);

  useEffect(() => {
    api("/api/plans").then(setPlans).catch(() => setPlans([]));
  }, []);

  useScrollReveal(plans.length);

  return (
    <div className="landing">
      <nav className="l-nav">
        <div className="l-nav-inner">
          <div className="l-brand">
            SELLER PROFIT
            <span className="l-badge">얼리액세스</span>
          </div>
          <div className="l-nav-cta">
            {user ? (
              <Link className="btn btn-primary" to="/dashboard">
                대시보드로
              </Link>
            ) : (
              <>
                <Link className="btn btn-ghost" to="/login">
                  로그인
                </Link>
                <Link className="btn btn-primary" to="/signup">
                  무료로 시작
                </Link>
              </>
            )}
          </div>
        </div>
      </nav>

      {/* HERO */}
      <header className="l-hero">
        <div className="l-hero-bg" aria-hidden="true" />
        <div className="l-hero-dots" aria-hidden="true" />
        <div className="l-hero-blob l-hero-blob-a" aria-hidden="true" />
        <div className="l-hero-blob l-hero-blob-b" aria-hidden="true" />
        <div className="l-wrap l-hero-grid">
          <div>
            <div className="l-eyebrow">쿠팡 셀러 순이익 분석 · 무료가입 카드 등록 불필요!</div>
            <h1>
              ROAS는 <span className="red">13.5배.</span>
              <br />
              그런데 팔수록
              <br />
              손해입니다.
            </h1>
            <p className="l-sub">
              매출도 광고 효율도 좋아 보이는 상품이, 정산 실수령 기준으로 계산하면 적자인 경우가
              있습니다. seller-profit은 광고비까지 반영한 <b>진짜 순이익</b>을 상품별로 보여줍니다.
            </p>
            <div className="l-hero-cta">
              <Link className="btn btn-primary btn-lg" to="/signup">
                무료로 내 순이익 보기
              </Link>
              <a className="btn btn-ghost btn-lg" href="#how">
                어떻게 계산하나요 →
              </a>
            </div>
            <div className="l-tags">
              <span className="tag">쿠팡 계정 연동</span>
              <span className="tag">정산 실수령 기준</span>
              <span className="tag">반품·광고비 자동 반영</span>
            </div>
          </div>

          {/* 시그니처: 정산 명세서 (공용 컴포넌트, docs/signup-tasks.md T11.1) */}
          <ReceiptCard />
        </div>
      </header>

      {/* PROBLEM */}
      <section className="l-strip" id="how">
        <div className="l-wrap">
          <h2 className="reveal-up">
            매출과 ROAS는 잘 보여요.
            <br />
            그런데 <span className="l-underline">통장</span>은 왜 다를까요?
          </h2>
          <p className="reveal-up">
            쿠팡 대시보드는 매출을 보여주지, 수수료·원가·반품·광고비를 다 뺀 뒤 실제로 남은 돈은
            알려주지 않습니다. 그 격차 속에 적자 상품이 숨어 있습니다.
          </p>
          <div className="contrast">
            <div className="chip reveal-up">
              <IconTrendUp bg="rgba(255,255,255,.08)" />
              <div className="k">쿠팡이 보여주는 것</div>
              <div className="v ok">매출 ₩270,000</div>
            </div>
            <div className="chip reveal-up">
              <IconPieChart bg="rgba(255,255,255,.08)" />
              <div className="k">광고 대시보드</div>
              <div className="v ok">ROAS 13.5×</div>
            </div>
            <div className="chip reveal-up">
              <IconWallet bg="rgba(255,255,255,.08)" />
              <div className="k">실제 통장에 남는 것</div>
              <div className="v bad">−₩159,737</div>
            </div>
          </div>
        </div>
      </section>

      {/* FLOW — 실제 동작 과정(완료된 기능 그대로, 가짜 지표 아님) */}
      <section className="l-flow">
        <div className="l-flow-dots" aria-hidden="true" />
        <IconSparkle className="l-flow-star l-flow-star-a" />
        <IconSparkle className="l-flow-star l-flow-star-b" />
        <div className="l-wrap">
          <div className="l-pill reveal-up">
            어떻게 계산하나요?
          </div>
          <h2 className="reveal-up">
            쿠팡 계정만 연동하면, <span className="l-accent-text">나머지는 자동입니다.</span>
          </h2>
          <div className="l-flow-steps">
            <div className="l-flow-step reveal-up">
              <span className="l-flow-num l-flow-num-1">01</span>
              <IconLinkApi />
              <h3>계정 연동</h3>
              <p>쿠팡 Open API 키를 등록해 연동</p>
            </div>
            <span className="l-flow-arrow" aria-hidden="true">→</span>
            <div className="l-flow-step reveal-up">
              <span className="l-flow-num l-flow-num-2">02</span>
              <IconDatabase />
              <h3>자동 수집</h3>
              <p>정산·주문·반품을 주기적으로 수집</p>
            </div>
            <span className="l-flow-arrow" aria-hidden="true">→</span>
            <div className="l-flow-step reveal-up">
              <span className="l-flow-num l-flow-num-3">03</span>
              <IconCalcWon />
              <h3>원가 입력</h3>
              <p>매입원가·기타비용을 상품에 직접 입력</p>
            </div>
            <span className="l-flow-arrow" aria-hidden="true">→</span>
            <div className="l-flow-step reveal-up">
              <span className="l-flow-num l-flow-num-4">04</span>
              <IconCsvUpload />
              <h3>광고비 반영</h3>
              <p>CSV 업로드 또는 수기 입력으로 SKU에 귀속</p>
            </div>
            <span className="l-flow-arrow" aria-hidden="true">→</span>
            <div className="l-flow-step reveal-up">
              <span className="l-flow-num l-flow-num-5">05</span>
              <IconMonitorWarning />
              <h3>적자 상품 적발</h3>
              <p>광고후 순이익이 마이너스인 SKU를 자동으로 표시</p>
            </div>
          </div>
        </div>
      </section>

      {/* VALUE */}
      <section className="l-value">
        <div className="l-wrap l-value-grid">
          <div>
            <div className="l-pill l-pill-accent reveal-up">
              <IconTrendUp bg="transparent" />
              SELLER PROFIT이 보여주는 것!
            </div>
            <h2 className="reveal-up">
              매출이 아니라,
              <br />
              <span className="l-accent-text l-underline-thick">실제로 남은 돈을 봅니다.</span>
            </h2>
          </div>
          <IllustrationBarChart />
        </div>
        <div className="l-wrap">
          <div className="l-cards">
            <div className="l-card reveal-up">
              <IconDocCheck />
              <span className="l-card-num">01</span>
              <h3>정산 실수령 기준 진짜 순이익</h3>
              <p>
                주문 금액이 아니라 쿠팡이 실제로 입금한 정산 금액에서 원가·비용을 뺍니다. 가장
                정직한 바닥 숫자.
              </p>
            </div>
            <div className="l-card reveal-up">
              <IconChartSearch />
              <span className="l-card-num">02</span>
              <h3>SKU별 광고 손익</h3>
              <p>
                ROAS가 높아도 순이익을 갉아먹는 상품을 빨갛게 적발합니다. 어떤 광고를 꺼야 하는지
                바로 보입니다.
              </p>
            </div>
            <div className="l-card reveal-up">
              <IconCalcBalance />
              <span className="l-card-num">03</span>
              <h3>정직한 계산</h3>
              <p>
                반품 이중차감을 막고, 상품에 붙일 수 없는 광고비는 '미할당'으로 투명하게 남깁니다.
                숫자를 믿을 수 있게.
              </p>
            </div>
            <div className="l-card reveal-up">
              <IconTarget />
              <span className="l-card-num">04</span>
              <h3>쿠팡에 맞춰 정밀하게</h3>
              <p>쿠팡의 정산·수수료·반품 구조에 맞춰 설계했습니다. 넓게 얕은 대신, 한 채널을 제대로.</p>
            </div>
          </div>
        </div>
      </section>

      {/* PRICING — /api/plans 에서 동적으로 받아 렌더(하드코딩 금지) */}
      <section className="l-pricing">
        <div className="l-wrap">
          <div className="l-sec-eyebrow reveal-up">요금제</div>
          <h2 className="reveal-up">필요한 만큼만.</h2>
          <div className="l-plans">
            {plans.map((p) => (
              <div key={p.code} className={"l-plan reveal-up" + (p.code === "PRO" ? " pro" : "")}>
                {p.code === "PRO" ? <span className="l-plan-ribbon">추천</span> : null}
                <div className="pname">{p.name}</div>
                <div className="price">
                  {p.monthlyPrice === 0 ? "₩0" : won(p.monthlyPrice)}
                  <small> / 월</small>
                </div>
                <ul>
                  {p.features.map((f, i) => (
                    <li key={i}>{f}</li>
                  ))}
                </ul>
                <Link
                  className={"btn " + (p.code === "PRO" ? "btn-primary" : "btn-ghost")}
                  to="/signup"
                >
                  {p.code === "PRO" ? "PRO 시작하기" : "무료로 시작"}
                </Link>
              </div>
            ))}
          </div>
          <p className="l-beta">얼리액세스 단계입니다. 가격·플랜 구성은 변경될 수 있습니다.</p>
        </div>
      </section>

      {/* FINAL CTA */}
      <section className="l-final">
        <div className="l-final-blob l-final-blob-a" aria-hidden="true" />
        <div className="l-final-blob l-final-blob-b" aria-hidden="true" />
        <div className="l-wrap l-final-grid reveal-up">
          <div>
            <h2>
              당신의 적자 상품,
              <br />
              <span className="l-underline-thick">지금 확인하세요.</span>
            </h2>
            <p>쿠팡 계정만 연동하면, 광고비까지 반영한 진짜 순이익을 상품별로 바로 보여드립니다.</p>
            <Link className="btn btn-primary btn-lg" to="/signup">
              무료로 내 손익 보기 <span aria-hidden="true">→</span>
            </Link>
          </div>
          <IllustrationBrowserMock />
        </div>
      </section>

      <footer className="l-footer">
        <div className="l-wrap l-footer-row">
          <div className="l-brand l-footer-brand">
            SELLER PROFIT
          </div>
          <div className="l-footer-copy">쿠팡 셀러를 위한 진짜 순이익 분석 · 얼리액세스 단계</div>
        </div>
      </footer>
    </div>
  );
}
