import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, won } from "../api";
import { useAuth } from "../auth.jsx";
import {
  IllustrationBarChart,
  IllustrationBrowserMock,
} from "../components/LandingIcons.jsx";
import "./Landing.css";
import { usePageTitle } from "../hooks/usePageTitle";

function useScrollReveal(dep) {
  useEffect(() => {
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
  }, [dep]);
}

const flowSteps = [
  {
    num: "01",
    title: "계정 연동",
    desc: "쿠팡 Open API 키를 등록하면 정산 데이터를 불러옵니다.",
    image: "/linking.png",
    sourceSize: "large",
  },
  {
    num: "02",
    title: "자동 수집",
    desc: "주문, 정산, 반품 내역을 주기적으로 모아둡니다.",
    image: "/gather.png",
    sourceSize: "large",
  },
  {
    num: "03",
    title: "원가 입력",
    desc: "상품별 매입원가와 기타비용을 바로 반영합니다.",
    image: "/cost.png",
    sourceSize: "compact",
  },
  {
    num: "04",
    title: "광고비 반영",
    desc: "CSV 업로드와 수기 입력으로 SKU별 광고비를 붙입니다.",
    image: "/advertising.png",
    sourceSize: "compact",
  },
  {
    num: "05",
    title: "적자 상품 발견",
    desc: "광고후 순이익이 마이너스인 SKU를 선명하게 표시합니다.",
    image: "/deficit.png",
    sourceSize: "compact",
  },
];

const valueCards = [
  {
    num: "01",
    title: "정산 실수령 기준 진짜 순이익!",
    desc: "주문 금액이 아니라 쿠팡이 실제로 입금한 정산 금액에서 원가와 비용을 뺍니다.",
  },
  {
    num: "02",
    title: "SKU별 광고 손익!",
    desc: "ROAS가 좋아도 순이익을 갉아먹는 상품을 찾아 어떤 광고를 줄일지 보여줍니다.",
  },
  {
    num: "03",
    title: "정직한 계산!",
    desc: "반품 이중차감을 막고, 상품에 붙일 수 없는 광고비는 미할당으로 투명하게 남깁니다.",
  },
  {
    num: "04",
    title: "쿠팡에 맞춘 분석!",
    desc: "쿠팡 정산, 수수료, 반품 구조에 맞춰 한 채널을 더 깊게 계산합니다.",
  },
];

export default function Landing() {
  usePageTitle("셀러프로핏 - 쿠팡 셀러 진짜 순이익 계산");
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
          <Link className="l-brand" to="/">
            SELLER PROFIT
            <span className="l-badge">얼리액세스</span>
          </Link>
          <div className="l-nav-links" aria-label="랜딩 섹션">
            <a href="#how">계산 방식</a>
            <a href="#value">핵심 기능</a>
            <a href="#pricing">요금제</a>
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

      <header className="l-hero">
        <div className="l-wrap">
          <div className="l-hero-copy">
            <div className="l-eyebrow">쿠팡 셀러 순이익 분석 · 카드 등록 불필요</div>
            <h1>
              쿠팡 정산부터 광고비까지,
              <span>진짜 순이익을 한 화면에.</span>
            </h1>
            <p className="l-sub">
              매출과 ROAS만 보고 판단하면 적자 상품을 놓칩니다. 셀러프로핏은 정산 실수령액,
              원가, 반품, 광고비를 한 번에 계산해 상품별로 실제 남는 돈을 보여줍니다.
            </p>
            <div className="l-hero-cta">
              <Link className="btn btn-primary btn-lg" to="/signup">
                무료로 내 손익 보기
              </Link>
              <a className="btn btn-soft btn-lg" href="#how">
                계산 방식 보기
              </a>
            </div>
          </div>

          <div className="l-hero-metrics" aria-label="핵심 지표 예시">
            <div>
              <span>정산 기준</span>
              <strong>실수령액</strong>
            </div>
            <div>
              <span>광고 포함</span>
              <strong>SKU 손익</strong>
            </div>
            <div>
              <span>가입 혜택</span>
              <strong>PRO 1개월</strong>
            </div>
          </div>

          <div className="l-product-stage reveal-up">
            <div className="l-stage-top">
              <div>
                <span className="l-stage-kicker">PROFIT CONTROL CENTER</span>
                <strong>광고후 순이익 실시간 진단</strong>
              </div>
              <span className="l-stage-status">적자 SKU 감지</span>
            </div>
            <div className="l-stage-ledger" aria-label="손익 진단 예시">
              <div className="l-ledger-row">
                <span className="l-ledger-name">흑자상품 A</span>
                <span>정산 ₩720,000</span>
                <span>광고비 ₩30,000</span>
                <strong className="profit">₩407,368</strong>
              </div>
              <div className="l-ledger-row is-loss">
                <span className="l-ledger-name">적자상품 B</span>
                <span>정산 ₩270,000</span>
                <span>광고비 ₩20,000</span>
                <strong className="loss">-₩159,737</strong>
              </div>
              <div className="l-ledger-row">
                <span className="l-ledger-name">흑자상품 C</span>
                <span>정산 ₩150,000</span>
                <span>광고비 ₩0</span>
                <strong className="profit">₩102,368</strong>
              </div>
            </div>
            <div className="l-stage-summary">
              <span>매출은 좋아 보여도</span>
              <strong>광고후 순이익은 다르게 보입니다.</strong>
            </div>
          </div>
        </div>
      </header>

      <section className="l-strip" id="how">
        <div className="l-wrap l-strip-grid">
          <div>
            <div className="l-sec-eyebrow reveal-up">왜 필요한가요?</div>
            <h2 className="reveal-up">
              매출과 ROAS는 잘 보여요.
              <span>그런데 통장은 왜 다를까요?</span>
            </h2>
          </div>
          <p className="reveal-up">
            쿠팡 대시보드는 매출을 보여주지만, 수수료, 원가, 반품, 광고비를 모두 뺀 뒤 실제로 남은
            돈까지 한 번에 말해주지는 않습니다. 그 사이에 적자 상품이 숨어 있습니다.
          </p>
        </div>
        <div className="l-wrap">
          <div className="contrast">
            <div className="chip reveal-up">
              <div className="k">쿠팡이 보여주는 것</div>
              <div className="v ok">매출 ₩270,000</div>
            </div>
            <div className="chip reveal-up">
              <div className="k">광고 대시보드</div>
              <div className="v ok">ROAS 13.5x</div>
            </div>
            <div className="chip danger reveal-up">
              <div className="k">실제 통장에 남는 것</div>
              <div className="v bad">-₩159,737</div>
            </div>
          </div>
        </div>
      </section>

      <section className="l-flow">
        <div className="l-wrap">
          <div className="l-section-head">
            <div className="l-sec-eyebrow reveal-up">자동 계산 흐름</div>
            <h2 className="reveal-up">
              쿠팡 계정만 연동하면,
              <span>나머지는 자동으로 정리됩니다.</span>
            </h2>
            <p className="l-section-copy reveal-up">
              주문과 정산 데이터를 모으고 원가와 광고비를 반영해, 지금 손해 보는 상품까지 한 흐름으로
              찾아냅니다.
            </p>
          </div>
          <div className="l-flow-steps reveal-up" aria-label="자동 계산 단계">
            {flowSteps.map((step) => (
              <div
                className="l-flow-step"
                key={step.num}
              >
                <span className="l-flow-orb">
                  <img
                    className={`l-flow-image is-${step.sourceSize}`}
                    src={step.image}
                    alt=""
                    aria-hidden="true"
                  />
                </span>
                <span className="l-flow-marker" aria-hidden="true">
                  <i />
                </span>
                <span className="l-flow-num">{step.num}</span>
                <strong>{step.title}</strong>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="l-value" id="value">
        <div className="l-wrap l-value-grid">
          <div>
            <div className="l-sec-eyebrow reveal-up">SELLER PROFIT이 보여주는 것!</div>
            <h2 className="reveal-up">
              매출이 아니라,
              <span>실제로 남은 돈을 봅니다.</span>
            </h2>
            <p className="l-section-copy reveal-up">
              정산 기준의 순이익, SKU별 광고 손익, 반품 반영 여부까지 한 화면에서 확인할 수 있게
              구성했습니다.
            </p>
          </div>
          <IllustrationBarChart />
        </div>
        <div className="l-wrap">
          <div className="l-cards">
            {valueCards.map((card) => (
              <div className="l-card reveal-up" key={card.num}>
                <span className="l-card-num">{card.num}</span>
                <h3>{card.title}</h3>
                <p>{card.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="l-pricing" id="pricing">
        <div className="l-wrap">
          <div className="l-section-head">
            <div className="l-sec-eyebrow reveal-up">요금제</div>
            <h2 className="reveal-up">
              필요한 만큼만.
              <span>시작은 부담 없이.</span>
            </h2>
            <p className="l-plan-gift reveal-up">가입 시 한 달 PRO PLAN 무료 지급!</p>
          </div>
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
          <p className="l-beta">얼리액세스 단계입니다. 가격과 플랜 구성은 변경될 수 있습니다.</p>
        </div>
      </section>

      <section className="l-final">
        <div className="l-wrap l-final-grid reveal-up">
          <div>
            <div className="l-sec-eyebrow">바로 확인하기</div>
            <h2>
              당신의 적자 상품,
              <span>오늘부터 숫자로 확인하세요.</span>
            </h2>
            <p>쿠팡 계정만 연동하면 광고비까지 반영한 진짜 순이익을 상품별로 보여드립니다.</p>
            <Link className="btn btn-primary btn-lg" to="/signup">
              무료로 내 손익 보기
            </Link>
          </div>
          <IllustrationBrowserMock />
        </div>
      </section>

      <footer className="l-footer">
        <div className="l-wrap l-footer-row">
          <div className="l-brand l-footer-brand">SELLER PROFIT</div>
          <div className="l-footer-copy">쿠팡 셀러를 위한 진짜 순이익 분석 · 얼리액세스 단계</div>
          <div className="l-footer-legal">
            <Link to="/terms">이용약관</Link>
            <span aria-hidden="true">·</span>
            <Link to="/privacy">개인정보처리방침</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}
