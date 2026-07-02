import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, won } from "../api";
import { useAuth } from "../auth.jsx";
import "./Landing.css";

// 공개 랜딩 페이지("/"). 비로그인 방문자에게 보여주는 첫 세일즈 자산.
// 디자인 기준: docs/landing-mockup.html (섹션 순서·카피·색/타이포 그대로 구현, 스타일은 Landing.css 로 격리).
// 가짜 지표 없음(docs/landing-page-tasks.md 절대 규칙) — 히어로의 숫자는 시드 데모(적자상품 B)와 정합.
// 요금제는 /api/plans 에서 받아 렌더(하드코딩 금지, PlanType 이 유일한 정책 소스).
export default function Landing() {
  const { user } = useAuth();
  const [plans, setPlans] = useState([]);

  useEffect(() => {
    api("/api/plans").then(setPlans).catch(() => setPlans([]));
  }, []);

  return (
    <div className="landing">
      <nav className="l-nav">
        <div className="l-brand">
          seller<b>·</b>profit
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
      </nav>

      {/* HERO */}
      <header className="l-hero">
        <div className="l-wrap l-hero-grid">
          <div>
            <span className="l-eyebrow">쿠팡 셀러 순이익 분석</span>
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
            <p className="l-note">쿠팡 계정 연동 · 정산 실수령 기준 · 반품·광고비 자동 반영</p>
          </div>

          {/* 시그니처: 정산 명세서 */}
          <div
            className="receipt"
            role="img"
            aria-label="적자상품 예시: ROAS 13.5배지만 진짜 순이익은 마이너스 159,737원"
          >
            <div className="receipt-top">
              <span className="r-name">적자상품 B · 이번 달</span>
              <span className="roas">ROAS 13.5×</span>
            </div>
            <div className="r-body">
              <div className="r-line reveal">
                <span className="lab">매출 (정산 실수령)</span>
                <span className="val mono">₩270,000</span>
              </div>
              <div className="r-line reveal">
                <span className="lab">− 매입원가 (45개)</span>
                <span className="val minus mono">−₩405,000</span>
              </div>
              <div className="r-line reveal">
                <span className="lab">− 배분 기타비용</span>
                <span className="val minus mono">−₩4,737</span>
              </div>
              <div className="r-line reveal">
                <span className="lab">− 광고비</span>
                <span className="val minus mono">−₩20,000</span>
              </div>
            </div>
            <div className="r-total">
              <span className="lab">진짜 순이익</span>
              <span className="val mono">−₩159,737</span>
            </div>
            <span className="r-flag">적자 · 광고 켤수록 손해</span>
          </div>
        </div>
      </header>

      {/* PROBLEM */}
      <section className="l-strip" id="how">
        <div className="l-wrap">
          <h2>
            매출과 ROAS는 잘 보여요.
            <br />
            그런데 통장은 왜 다를까요?
          </h2>
          <p>
            쿠팡 대시보드는 매출을 보여주지, 수수료·원가·반품·광고비를 다 뺀 뒤 실제로 남은 돈은
            알려주지 않습니다. 그 격차 속에 적자 상품이 숨어 있습니다.
          </p>
          <div className="contrast">
            <div className="chip">
              <div className="k">쿠팡이 보여주는 것</div>
              <div className="v ok">매출 ₩270,000</div>
            </div>
            <div className="chip">
              <div className="k">광고 대시보드</div>
              <div className="v ok">ROAS 13.5×</div>
            </div>
            <div className="chip">
              <div className="k">실제 통장에 남는 것</div>
              <div className="v bad">−₩159,737</div>
            </div>
          </div>
        </div>
      </section>

      {/* VALUE */}
      <section className="l-value">
        <div className="l-wrap">
          <div className="l-sec-eyebrow">seller-profit이 보여주는 것</div>
          <h2>매출이 아니라, 실제로 남은 돈을 봅니다.</h2>
          <div className="l-cards">
            <div className="l-card">
              <div className="n">01</div>
              <h3>정산 실수령 기준 진짜 순이익</h3>
              <p>
                주문 금액이 아니라 쿠팡이 실제로 입금한 정산 금액에서 원가·비용을 뺍니다. 가장
                정직한 바닥 숫자.
              </p>
            </div>
            <div className="l-card">
              <div className="n">02</div>
              <h3>SKU별 광고 손익</h3>
              <p>
                ROAS가 높아도 순이익을 갉아먹는 상품을 빨갛게 적발합니다. 어떤 광고를 꺼야 하는지
                바로 보입니다.
              </p>
            </div>
            <div className="l-card">
              <div className="n">03</div>
              <h3>정직한 계산</h3>
              <p>
                반품 이중차감을 막고, 상품에 붙일 수 없는 광고비는 '미할당'으로 투명하게 남깁니다.
                숫자를 믿을 수 있게.
              </p>
            </div>
            <div className="l-card">
              <div className="n">04</div>
              <h3>쿠팡에 맞춰 정밀하게</h3>
              <p>쿠팡의 정산·수수료·반품 구조에 맞춰 설계했습니다. 넓게 얕은 대신, 한 채널을 제대로.</p>
            </div>
          </div>
        </div>
      </section>

      {/* PRICING — /api/plans 에서 동적으로 받아 렌더(하드코딩 금지) */}
      <section className="l-pricing">
        <div className="l-wrap">
          <div className="l-sec-eyebrow">요금제</div>
          <h2>필요한 만큼만.</h2>
          <div className="l-plans">
            {plans.map((p) => (
              <div key={p.code} className={"l-plan" + (p.code === "PRO" ? " pro" : "")}>
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
        <div className="l-wrap">
          <h2>당신의 적자 상품, 지금 확인하세요.</h2>
          <p>쿠팡 계정만 연동하면, 광고비까지 반영한 진짜 순이익을 상품별로 바로 보여드립니다.</p>
          <Link className="btn btn-primary btn-lg" to="/signup">
            무료로 내 순이익 보기
          </Link>
        </div>
      </section>

      <footer className="l-footer">seller-profit · 쿠팡 셀러를 위한 진짜 순이익 분석</footer>
    </div>
  );
}
