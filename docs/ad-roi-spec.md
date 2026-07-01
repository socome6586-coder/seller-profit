# 기능 명세서 — 광고 ROI × 순이익 옵티마이저 (seller-profit 확장)

> 저장소 위치 권장: `docs/ad-roi-spec.md`
> 이 문서는 **WHAT/WHY**(무엇을 왜)를 정의한다. 실제 작업 순서·완료조건은 `docs/ad-roi-tasks.md` 참고.
> 기존 `CLAUDE.md` 의 규약(정산=매출원천, 멱등, NUMERIC, 소유권 가드, Flyway, Hibernate `validate`)을 그대로 계승한다.

---

## 1. 배경 & 가치

seller-profit 은 이미 셀러의 **진짜 순이익**(정산 payout − COGS − 배분된 기타비용)을 SKU 단위로 안다. 여기에 **광고비를 SKU 단위로 귀속**시키면, 셀러가 아무도 명확히 못 보는 질문에 답할 수 있다:

> **"어떤 광고비가 실제로 순이익을 벌어주고, 어떤 광고비는 순이익을 갉아먹는가?"**

핵심 결과물(머니샷)은 **"광고를 돌릴수록 손해인 SKU"를 자동으로 빨갛게 띄우는 화면**이다. 셀러가 이 화면을 보고 즉시 "이 광고 꺼야겠다"를 판단하면, 그 절감액이 곧 이 도구의 값을 스스로 증명한다(적자 상품을 드러내는 기존 1순위 가치의 자연스러운 확장).

이 기능은 **PRO 플랜 후보 기능**이다(플랜 게이팅은 §8, 가격은 임시 정책값).

## 2. 왜 지금 코드에 잘 맞나

- **정산이 매출 원천**이라 순이익이 이미 정확 → 그 위에 광고비만 귀속하면 된다.
- **CTE 기반 집계**(`findProfitByPeriod`)로 JOIN fan-out 을 이미 방지 중 → 광고비 CTE 를 한 줄 더 얹는 구조.
- **멱등 `external_ref` 패턴**·NUMERIC(14,2)·소유권 가드·`ApiExceptionHandler` 를 그대로 재사용.

## 3. 데이터 소스 결정 (시니어 판단 — 중요)

**v1 = 수기 입력 + CSV 업로드** (쿠팡 광고센터에서 내려받은 광고 리포트).
- 이유: **사업자등록·별도 API 승인 없이 지금 당장 가능**하다. `CLAUDE.md` 의 "실 키 불필요 영역부터" 원칙과 정확히 일치.
- 쿠팡 광고 API 연동은 **후속**으로, 기존 Provider 규율대로 `AdSpendProvider` 인터페이스 뒤에 `CoupangAdsProvider` 로 추가한다(지금은 인터페이스 자리만, 구현 X).
- ⚠️ 쿠팡 광고 API 의 존재·접근 게이팅·CSV 컬럼 스키마는 **실제 리포트 파일로 확정 필요**(기존 `[검증 필요]` 마커 방식 그대로).

## 4. 스코프

**v1 In**
- 광고비 데이터: 수기 입력 API + CSV 업로드(멱등)
- SKU(vendor_item_id) 단위 광고비 귀속(직접 귀속)
- 광고 효율 대시보드: SKU별 광고전 기여이익 / 광고비 / 광고후 순이익 / **광고손실 플래그**
- 매칭 안 되는 광고비 = **"미할당" 버킷**으로 투명하게 표기(숨기지 않음)
- 기존 순이익 엔진과의 **이중차감 제거**(§6, 최우선)

**v1 Out (만들지 말 것 → 백로그)**
- 쿠팡 광고 API 실연동, 키워드/시간대 최적화 추천, 자동 입찰, 다채널, 예측/ML 어트리뷰션

## 5. 데이터 모델

새 테이블 `ad_spends` (Flyway **V4**, 다음 번호). 기존 스키마 규율을 따른다: 멱등 UNIQUE, `updated_at` 트리거, 금액 NUMERIC(14,2), 기간 CHECK.

```
ad_spends
  id                BIGSERIAL PK
  market_account_id BIGINT FK -> market_accounts (ON DELETE CASCADE)
  vendor_item_id    VARCHAR      -- SKU. NULL 허용 = 미할당(캠페인 단위만 있는 spend)
  campaign          VARCHAR NULL -- 차원(rollup 용)
  ad_group          VARCHAR NULL -- 차원
  keyword           VARCHAR NULL -- 차원
  spend_date        DATE NOT NULL
  amount            NUMERIC(14,2) NOT NULL CHECK (amount >= 0)
  source            VARCHAR NOT NULL   -- 'MANUAL' | 'CSV' | 'COUPANG_ADS'(후속)
  external_ref      VARCHAR NOT NULL   -- 멱등 키
  created_at        TIMESTAMPTZ (DB 소유)
  updated_at        TIMESTAMPTZ (DB 소유, 트리거)
  UNIQUE (market_account_id, external_ref)
  INDEX (market_account_id, spend_date)
  INDEX (market_account_id, vendor_item_id, spend_date)
```

- **멱등 `external_ref`**: `source:campaign:adGroup:keyword:vendorItemId:spendDate` 정규화 조합(빈 차원은 고정 토큰). CSV 재업로드 시 같은 행이 중복 저장되지 않도록. 동일 키 충돌 시 기존 반품 `ordinal` 패턴처럼 등장 순번을 붙일지 여부는 실제 CSV 구조 확인 후 결정.
- `vendor_item_id` 는 `products` 의 upsert 키(`market_account_id, vendor_item_id`)와 동일 규칙으로 매칭. 매칭 실패해도 저장은 하되 **미할당**으로 집계.

## 6. ⚠️ 이중차감 방지 규칙 (이 기능에서 가장 중요)

현재 광고비는 셀러가 `manage/CostController` 로 **기타비용(Cost)** 에 입력하고, `ProfitCalculationService` 가 이를 **매출비율로 전 SKU에 배분**한다. 새 기능이 `ad_spends` 로 광고비를 또 붙이면 같은 돈을 두 번 뺀다.

**규칙:**
1. 광고비는 이제 **`ad_spends` 로만** 관리한다(정밀 귀속).
2. `ProfitCalculationService`/`findProfitByPeriod` 의 **기타비용 배분에서 "광고성" 비용을 제외**한다.
   - `Cost` 에 비용 타입 구분이 있으면 광고 타입을 배분 대상에서 뺀다. 타입 구분이 불충분하면 마이그레이션에서 정의한다(§7).
3. **불변식(테스트로 증명):** 기존 광고비 Cost 를 동일 금액으로 `ad_spends` 로 이전하면 **전체 순이익 합계는 불변**, SKU별 분포만 재배치된다. (미할당 광고비는 전체에서는 차감되지만 특정 SKU엔 안 붙는다 → 전체 합계 항등 유지.)

## 7. 기존 광고비 Cost 데이터 처리

- 기존에 기타비용으로 쌓인 "광고비" 항목의 처리 방침을 마이그레이션에서 명시(이전 or 배제).
- `Cost` 에 광고를 식별할 타입/카테고리가 없으면, **비용 타입 enum 에 `AD` 추가**(Flyway + enum)하거나, 명세상 "기타비용에는 광고비를 넣지 않는다"로 정하고 UI 문구·검증으로 유도. 어느 쪽이든 §6 규칙과 정합해야 한다. **결정을 `docs/DECISIONS.md` 에 기록.**

## 8. 지표 정의 (투명·단순 — 정교함보다 명확함)

기간·계정 범위 내 각 SKU에 대해:

- **광고전 기여이익** = Σ payout − Σ(units × COGS) − (광고 제외) 배분된 기타비용
  - units = 주문수량 − 반품수량(기존 규칙 그대로; 순이익은 이미 반품 반영)
- **광고비** = Σ `ad_spends.amount` (해당 SKU·기간)
- **광고후 순이익** = 광고전 기여이익 − 광고비
- **ROAS**(참고 지표) = 귀속 매출 / 광고비
- **광고손실 플래그** = (광고비 > 광고전 기여이익) → true. 이 SKU는 **광고를 돌릴수록 손해**다. 화면 최상단·빨강(기존 `적자` UI 관용 재사용).

**헤드라인 숫자(대시보드 상단):**
- **재검토 대상 광고비** = Σ(광고손실 SKU들의 광고비) — "이만큼의 광고비가 순이익보다 큰 손해를 내고 있어요."
- **미할당 광고비** = SKU 매칭 실패한 광고비 합계(별도 표기).

## 9. 어트리뷰션 규칙 (v1)

- **직접 귀속만.** 광고비 행이 가진 `vendor_item_id` + 기간으로 해당 SKU에 귀속.
- 캠페인/광고그룹/키워드는 **차원(rollup)** 일 뿐, v1 핵심 지표는 SKU 단위.
- SKU 없는 spend(캠페인 단위만) = **미할당**. 절대 임의 배분하지 않는다(투명성 > 영리함, 기존 제품 철학).

## 10. 엣지 케이스

- 광고비만 있고 매출 0 → 광고전 기여이익 0 or 음수, 광고후 순이익 = −광고비(순손실). 광고손실 플래그 true.
- SKU 매칭 실패 → 미할당 버킷(전체 헤드라인엔 반영, SKU 표엔 별도 행).
- 기간 경계 → `spend_date` 가 조회 from/to 안일 때만 포함.
- 반품 상호작용 → 광고 계산은 **기존 순이익 위에 얹으므로** 반품은 자동 정합(추가 처리 불필요).
- 통화/부호 → amount ≥ 0(환불/조정 있으면 별도 정책, v1은 양수 spend 만).
- 플랜 게이팅 → FREE는 요약/제한, PRO는 전체(§ `PlanType` 한 곳에서 강제, 기존 게이팅 패턴 재사용).

## 11. API (요약, 상세는 tasks)

- `POST /api/ads/spends` — 수기 1건 입력(로그인+소유)
- `POST /api/ads/spends/import` — CSV 업로드(멱등, 잘못된 행 리포트)
- `GET /api/dashboard/ad-roi?accountId=&from=&to=` — SKU별 광고 효율 + 헤드라인 + 미할당
- 모두 세션 주체·`AccountAccess.assertOwner` 로 소유권 강제, 실패는 `ApiExceptionHandler` 400/401 JSON.

## 12. 성공 기준

- 셀러가 대시보드에서 **"광고 돌릴수록 손해인 SKU"를 3초 안에** 식별할 수 있다.
- 광고비를 `ad_spends` 로 이전해도 **전체 순이익 합계가 이전과 동일**하다(이중차감 없음, 단위테스트 통과).
- CSV 재업로드가 **멱등**(중복 무)이다.
