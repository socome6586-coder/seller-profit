# CLAUDE.md — seller-profit 프로젝트 가이드

> 이 파일은 Claude Code 가 프로젝트 맥락을 자동으로 잡기 위한 핸드오프 문서다.
> 작업을 이어가기 전에 먼저 읽고, 아래 "다음 단계"부터 진행한다.

## 프로젝트 개요

쿠팡 셀러의 **상품별 진짜 순이익(마진율)** 을 자동 계산해 보여주는 도구 (MVP).
매출이 아니라 "수수료·원가·비용 다 빼고 실제로 얼마 남았나"가 핵심 가치.
적자 상품을 자동으로 드러내는 것이 1순위 기능.

- 타깃: 쿠팡 셀러 (MVP 단일 채널)
- 수익 모델: 월 구독
- 상세 기획/스코프: `docs/coupang-profit-mvp-spec.md` 참고

> **⚠️ 이 프로젝트는 포트폴리오가 아니라 실제 수익을 내는 서비스가 목표다.** (2026-07 방향 확정)
> 그래서 "런칭에 필요한 것"이 우선이다. 런칭에는 사업자등록이 사실상 강제인데, 두 군데서 막힌다:
> 1. **쿠팡 Open API 키** — 발급에 **사업자 인증 필수**(쿠팡 공식 문서). 마켓플레이스는 개인 판매자 등급이 없어 셀러 가입 자체가 사업자등록증 + 통신판매업 신고 요구.
> 2. **토스페이먼츠 구독료 수금** — PG 계약이 **사업자등록 + 통신판매업 신고 필수.**
> → 개인사업자(간이과세)는 홈택스 온라인·무료·1~2일. **실 키 라이브 검증과 실 결제는 사업자등록 전까지 구조적으로 불가**(쿠팡 공개 샌드박스 없음). 그 전까지는 실 키가 필요 없는 영역(배포·기능 완성)을 진행한다.
> 그리고 현재 앱은 **localhost 전용** → 고객이 접속하려면 **배포/호스팅(서버·도메인·HTTPS·운영 DB)** 이 실수익으로 가는 진짜 1순위 기술 과제다.

## 기술 스택

- Java 21 / Spring Boot 3.4
- Spring Data JPA + Hibernate, PostgreSQL
- Flyway (DB 마이그레이션: `src/main/resources/db/migration/V1__init.sql`)
- Gradle (wrapper 포함)
- Lombok

## 패키지 구조 (`com.sellerprofit`)

- `crypto/` — API 키 AES-256-GCM 암복호화 (`AesGcmEncryptor`, `EncryptedStringConverter`)
- `domain/` — JPA 엔티티 (`User`, `MarketAccount`, `Product`, `OrderItem`, `Settlement`, `ReturnItem`, `Cost`), `domain/type/` 에 enum
- `repository/` — Spring Data 리포지토리 + 순이익 집계 네이티브 쿼리(`ProductRepository.findProfitByPeriod`, 결과는 `ProductProfitRow`)
- `coupang/` — 쿠팡 Open API 연동. HMAC 서명(`CoupangHmacSigner`) + 클라이언트(`CoupangApiClient`) + 주문/정산/반품 수집 서비스·스케줄러 + DTO(`dto/`). **연동·수집 로직 완료**, 실 키 라이브 대조만 남음.
- `profit/` — 순이익 계산(`ProfitCalculationService`, 기타비용 매출비율 배분) + 대시보드 API(`ProfitDashboardController` /profit,/returns) + 반품 사유 통계(`ReturnStatsService`)
- `manage/` — 셀러 직접 입력 도메인(원가·기타비용) API. `ManagementService` + `ProductManagementController`/`CostController` + `ApiExceptionHandler`, DTO 는 `manage/dto/`
- `auth/` — 세션 인증(BCrypt, HttpSession). `AuthController`(signup/login/logout/me) + `CurrentUser`(세션 주체 헬퍼) + `MeController`(내 계정 목록/연동/해제/동기화)
- `account/` — 쿠팡 계정 연동. `AccountConnectionService`(연동/해제, 플랜 한도 강제) + `AccountAccess`(소유권 가드=계정 열거 차단) + `ManualSyncService`(수동 동기화, 소스별 예외 격리)
- `subscription/` — 요금제. `PlanType`(FREE/PRO 가격·한도 한 곳 고정) + `SubscriptionService` + `SubscriptionController`
- `billing/` — 토스페이먼츠 정기결제 스캐폴딩. `TossBillingClient` + `BillingService`(구독/갱신/해지) + `BillingScheduler`(매일 03:10 KST) + `BillingController`
- `web/` — `SpaForwardingController`(SPA 딥링크 forward)
- 프론트: `frontend/`(React18+Vite+React Router6). 빌드 산출물이 `src/main/resources/static/` 으로 나가 같은 오리진 서빙.
- `ads/` — 광고비 인제스트(수기/CSV) + 광고 ROI 집계. 광고비를 SKU(vendor_item_id) 단위로 귀속해 "광고전 기여이익 vs 광고비"로 광고손실 SKU를 적발. 소스는 v1 수기/CSV, 쿠팡 광고 API 는 `ads/provider/AdSpendProvider` 뒤 후속. 상세: docs/ad-roi-spec.md

## 지금까지 완료된 것

- [x] DB 스키마 (Flyway V1) — 6개 테이블, 멱등 UNIQUE 제약, updated_at 트리거
- [x] 도메인 엔티티 + 리포지토리
- [x] API 키 암호화: 엔티티엔 평문 String, DB엔 BYTEA. `@Convert(converter = EncryptedStringConverter.class)` 로 투명 처리
- [x] `Product.create(...)`, `OrderItem.create(...)` 정적 팩토리 (외부 패키지 생성용)
- [x] `@EnableScheduling` (메인 클래스)
- [x] **`CoupangHmacSigner`** — HMAC-SHA256 인증 헤더 생성
- [x] **`CoupangApiClient`** — 발주서 목록 조회 (RestClient, URI 직접 생성으로 재인코딩 차단)
- [x] **DTO** — `OrderSheetResponse` / `OrderSheet` / `CoupangOrderItem` (record + `@JsonIgnoreProperties`)
- [x] **`OrderIngestionService`** — nextToken 페이징 수집, HTTP는 트랜잭션 밖 / 영속화는 `TransactionTemplate` 페이지 단위, 상품 upsert + 주문 라인 멱등 저장 + `lastOrderSyncedAt` 갱신
- [x] **`OrderSyncScheduler`** — 30분마다 쿠팡 계정 전체 순회, 계정별 예외 격리
- [x] **정산(Settlement) 수집** — `CoupangApiClient.fetchRevenueHistory` + `RevenueHistory*` DTO + `SettlementIngestionService`(payout=판매금액−수수료, 멱등 `external_ref`) + `SettlementSyncScheduler`(1시간 주기, lookback 14일)
  - [x] **[라이브 문서로 정산 엔드포인트 확정 — 404 해결]** 쿠팡 '매출내역 조회'(Sales Detail Query) 문서 기준으로 경로·구조 수정.
    - **경로**: `GET /v2/providers/openapi/apis/api/v1/revenue-history`. ⚠️ 발주서/반품과 달리 **vendorId 가 경로가 아니라 쿼리 파라미터**다(이전 `.../vendors/{vendorId}/revenue-history` 가 404 "No exactly matching API specification" 의 원인). 페이징 요청 키는 `token`(응답 봉투는 `nextToken`).
    - **응답 구조(중첩)**: `data[]`(주문 묶음: `orderId`/`saleType`/`recognitionDate`/`settlementDate`) → 각 묶음의 `items[]`(옵션상품 라인: `vendorItemId`/`vendorItemName`/`salePrice`/`quantity`/`saleAmount`/`serviceFee`/`serviceFeeVat`). DTO 를 `RevenueHistory`(묶음) + `RevenueHistoryItem`(라인)로 분리.
    - **payout** = `saleAmount − (serviceFee + serviceFeeVat)`. **멱등 키** = `orderId:saleType:recognitionDate:vendorItemId:순번`(한 묶음 내 동일 옵션상품 중복 줄은 등장 순번으로 구분).
    - ⚠️ [실 키로 잔여 확인] 필드명/날짜포맷/페이징 토큰 키는 문서 기준 반영했으나, **실 쿠팡 키로 200 응답을 받아 최종 대조** 필요(특히 `recognitionDate` 포맷, REFUND 음수 부호, 정산 라인 고유 id 존재 여부 → 있으면 멱등 키 교체).
- [x] **순이익 계산 서비스 + 대시보드 API** — `ProfitCalculationService`(기타비용 매출 비율 배분, 마진율) + `ProfitDashboardController`(`GET /api/dashboard/profit`). DTO `ProfitSummary`/`ProductProfit`.
- [x] **로컬 샘플 시드** — `LocalSeedData`(`@Profile("seed")`, 멱등). 흑자 2 + 적자 1 시나리오. 쿠팡 키 없이 대시보드 확인 가능.
- [x] **도메인 팩토리 추가** — `User.create`, `MarketAccount.create`, `Cost.create` (가입/연동/비용입력 + 시드 공용)
- [x] **로컬 E2E 검증 완료** — Docker Postgres + `seed` 프로파일로 기동, `GET /api/dashboard/profit?accountId=1` 응답 확인. 적자상품이 맨 위, 기타비용 매출비율 배분·마진율 정상.
  - 수정: `V1__init.sql` 말미의 예시 쿼리(`:account` 등 named param)가 실행돼 Flyway 문법 에러 → 주석 안내로 교체.
  - 수정: Windows `javac` 기본 charset(CP949)로 한글 리터럴 깨짐 → `build.gradle` 에 `options.encoding='UTF-8'` 고정.
- [x] **정적 대시보드 + 1-명령 로컬 실행** — `static/index.html`(다크, 적자 빨강+뱃지) + `application-seed.yml`(포트 8088, 개발용 고정 키 → env 불필요) + `docker-compose.yml`(로컬 Postgres).
- [x] **반품/취소 수집** — Flyway `V2__return_items.sql`(반품 테이블 + `market_accounts.last_return_synced_at`) + `ReturnItem` 엔티티/리포 + 반품 DTO(`ReturnRequestResponse`/`ReturnRequest`/`CoupangReturnItem`) + `CoupangApiClient.fetchReturnRequests` + `ReturnIngestionService`(receiptId+상품 멱등) + `ReturnSyncScheduler`(1시간, lookback 14일).
  - **순이익 보정**: `findProfitByPeriod` 에 `return_items` CTE 추가 → **COGS 기준 수량 = 주문수량 − 반품수량**(GREATEST 0). 매출(payout)은 정산이 이미 반품을 음수로 반영하므로 추가 차감 안 함(이중 차감 방지). 대시보드에 `반품` 컬럼/`returnedUnits` 노출, 시드에 반품 시나리오 추가.
  - ⚠️ [검증 필요] 반품요청 **엔드포인트 경로·쿼리 키·JSON 필드명(receiptId/createdAt/receiptStatus/returnItems[].purchaseCount)·페이징 토큰 키**는 라이브 문서로 확정. 코드에 `[검증 포인트]` 주석 표시.
  - **[보강 완료] `external_ref` 충돌 방지** — 한 접수번호에 같은 vendorItemId 라인이 복수로 와도 옵션상품별 등장 순번(ordinal)을 키에 붙여 누락을 막음. 첫 라인(ordinal 0)은 기존 형식(`receiptId:vendorItemId`) 유지, 둘째부터 `#1`,`#2`. `ReturnIngestionService.buildExternalRef`, 단위 테스트 `ReturnExternalRefTest`. (가정: 쿠팡이 returnItems[] 순서를 호출마다 동일하게 줌. 라인 고유 id 있으면 그걸로 교체 권장.)
- [x] **반품 사유별 통계** — `GET /api/dashboard/returns?accountId=&from=&to=` (기간 생략 시 최근 30일). 사유로 묶어 수량/라인수/비중(%)을 많은 순으로 내려준다(빈 사유는 '미상'). `ReturnItemRepository.aggregateReasonsByPeriod`(JPQL) → `ReturnReasonRow` 프로젝션 → `ReturnStatsService` 가 비중 계산 → `ReturnReasonSummary`/`ReturnReasonStat`. 대시보드에 "반품 사유 분석" 표(비중 막대 포함) 추가.
  - 시드 보강: 상품별 총 반품수량은 유지(A=10, B=5)하되 사유를 쪼갬(A: 단순변심 6 + 배송지연 4, B: 상품불량 5) → 순이익 수치 불변, 사유 분포만 다양화. `seedProduct` 는 Product 반환, `seedReturn(reason,qty,ordinal)` 로 라인별 저장.
  - 검증: `seed` 재기동 → A 주문100/반품10 → 판매90·COGS 270k, B 주문50/반품5 → 판매45·COGS 405k(적자 유지), `totalReturnedUnits=15`. 적자상품 B 맨 위 정상.
- [x] **원가·기타비용 직접 입력 API + 대시보드 폼** — `manage` 패키지. 셀러가 화면에서 순이익 입력값을 채우는 경로.
  - `GET /api/products?accountId=` (상품+원가 목록), `PATCH /api/products/{id}/cogs` `{"cogs":3000}` (매입원가 입력/수정).
  - `GET /api/costs?accountId=` (기타비용 목록), `POST /api/costs` (광고비/배송비/기타 신규 입력, 201).
  - 검증: record DTO 에 jakarta `@NotNull/@PositiveOrZero/@Digits/@Size`, 기간 역전(periodEnd<periodStart) 거부(스키마 CHECK 와 동일 규칙), 소유관계는 `accountId→MarketAccount→User` 로 해석.
  - `ApiExceptionHandler`(@RestControllerAdvice) 로 `IllegalArgumentException`/검증 실패를 사람이 읽는 **400 JSON**(`{"error":...}`)으로 변환. 기본 동작이면 500 으로 새어 원인이 안 보임.
  - `static/index.html` 하단에 "입력/관리" 패널(원가 입력 select+저장, 기타비용 입력 폼) 추가, 저장 후 대시보드 자동 갱신.
  - ⚠️ 한글 메모를 Windows `curl`/셸로 POST 하면 CP949 → `0xbf` UTF-8 에러. 코드 버그 아님(브라우저 fetch+JSON 은 정상 UTF-8). 검증은 브라우저로.
  - 검증: cogs 음수 → `{"error":"cogs: 0 이상이어야 합니다"}`, 없는 accountId → `{"error":"MarketAccount 없음: 999"}`, 기간 역전 → `{"error":"기간 종료일이 시작일보다 빠를 수 없습니다."}`. PATCH 로 B 의 COGS 낮추면 적자→흑자 전환 확인.

- [x] **구독/인증 Phase 1 — 요금제 카탈로그 + 무료 가입 + 구독 상태 조회** — 결제 PG 는 **토스페이먼츠** 기준(무료 즉시 테스트 가능), 유저 확보 우선이라 **FREE 플랜 상시 제공**(가입 기본값).
  - `subscription` 패키지: `PlanType`(FREE ₩0 / PRO ₩9,900, 가격·한도·혜택을 한 곳에 고정. 한도 -1=무제한) + `SubscriptionService` + `SubscriptionController`.
    - `GET /api/plans` (요금 페이지용 공개 카탈로그), `GET /api/subscription?userId=` (현재 상태+적용 플랜).
  - `auth` 패키지: `POST /api/auth/signup`(이메일/비번 → **BCrypt** 해시 저장, 중복 이메일 거부, 가입 즉시 FREE). `PasswordConfig`(BCrypt 인코더만, 전체 시큐리티 필터체인 없음 → 기존 엔드포인트 잠그지 않음). 응답에 비번 해시 미노출(`AuthUserView`).
  - `build.gradle` 에 `spring-security-crypto` 추가. `ApiExceptionHandler` 적용 범위에 auth/subscription 추가.
  - ⚠️ PRO 가격(₩9,900)·플랜 한도는 임시 정책값 → `PlanType` 한 곳만 고치면 됨. 확정 필요.
  - [x] Phase 2(로그인/세션): `POST /api/auth/login`(BCrypt 검증 → 세션에 `USER_ID` 저장, 세션 고정 방지 위해 로그인 시 새 세션 발급), `POST /api/auth/logout`(세션 무효화, 204·멱등), `GET /api/auth/me`(세션 없으면 401). 실패 사유(미존재/비번불일치) 미구분(계정 열거 방지). `UnauthorizedException` → `ApiExceptionHandler` 에서 **401 JSON**. 시큐리티 필터체인 없이 컨트롤러가 직접 `HttpSession` 처리. `AuthController.SESSION_USER_ID` 키 공유. 검증: signup 201 → me 401 → login 200(JSESSIONID) → me 200 → 오타 비번 400 → logout 204 → me 401.
    - [x] **(Phase 2 잔여) 백엔드 엔드포인트 보호 "벽" — 세션 주체 기반 소유권 강제.** 컨트롤러가 userId/accountId 를 그냥 받던 걸 세션 주체로 검증한다.
      - `auth/CurrentUser` — 세션의 `USER_ID` 를 꺼내는 공통 헬퍼(없으면 `UnauthorizedException`→401). `account/AccountAccess` — `assertOwner(accountId,userId)` 소유권 가드(`MarketAccountRepository.existsByIdAndUserId`). **소유 아님/없음을 동일한 "MarketAccount 없음"** 으로 처리해 **계정 열거 차단**. `myAccounts(userId)`→`AccountView`(민감키 미노출).
      - 적용: `ProfitDashboardController`(/profit,/returns), `ProductManagementController`(list=계정소유, updateCogs=상품→계정→유저 소유를 서비스에서 "상품 없음" 으로), `CostController`(list/create), `SubscriptionController`(/api/subscription 가 `@RequestParam userId` 제거→세션 주체). 신규 `auth/MeController` `GET /api/me/accounts`(내 계정 목록, 대시보드 선택용).
      - 시드: 데모 로그인 계정 `demo@demo.local` / `demo1234`(BCrypt 해시). 로그인 벽 뒤에서 시드 데이터를 보려면 이 계정으로 로그인. (기존 `{noop}seed` 는 BCrypt 와 안 맞아 로그인 불가였음 → 교체.)
      - 프론트: 대시보드가 accountId **수동 입력 → `/api/me/accounts` 드롭다운**(첫 계정 기본, 계정 변경 시 자동 조회, 계정 0개 안내). 요금제는 `/api/subscription` 를 세션으로(userId 미전달). 로그인 화면에 데모 계정 힌트.
      - 검증(curl): 미로그인 dashboard/me 401 → demo 로그인 200 → `/api/me/accounts`=[#1] → 본인 accountId=1 데이터 OK → accountId=999 **400 "없음"**(열거 차단) → `/api/subscription` 세션 FREE → products 200 / 본인 cogs 200 / 타 상품 99999 **400 "상품 없음"** → costs 200 → plans 공개 200 → logout 204 → me 401. seed 볼륨 리셋 후 데모 계정 생성 확인.
      - ⚠️ 남음: **조회 기간 한도(dashboardLookbackDays) 게이팅**은 아직 미적용 — `PlanType` 에 값은 있으나 강제는 후속.

- [x] **쿠팡 계정 연동 플로우 + 플랜 한도(계정 수) 게이팅** — 시드(가짜) → 실사용을 잇는 입구. 로그인 셀러가 자기 쿠팡 키를 화면에서 등록한다.
  - `account/AccountConnectionService`: `connect(userId, vendorId, accessKey, secretKey)`(유저 로드→**플랜 한도 `PlanType.maxMarketAccounts` 강제**: FREE=1개, 초과 시 400 → "PRO 로 업그레이드"; 중복 업체코드 400; 키는 평문으로 받아 `MarketAccount.create`→컨버터가 **AES-GCM 암호화 저장**), `disconnect(userId, accountId)`(소유 아님/없음을 동일 "없음"=열거 차단, DB FK `ON DELETE CASCADE` 라 상품/주문/정산/반품 동반 정리).
  - `MarketAccountRepository`: `countByUserId`(한도), `existsByUserIdAndVendorId`(중복). `AccountConnectRequest`(@NotBlank vendorId/accessKey/secretKey, MVP 단일 채널이라 channel 미수신=COUPANG 기본).
  - `auth/MeController`: `POST /api/me/accounts`(201, 연동), `DELETE /api/me/accounts/{id}`(204, 해제). (`GET` 은 기존 목록.) ApiExceptionHandler basePackages(auth) 로 400/401 JSON 처리.
  - 프론트: 새 화면 `Accounts.jsx`(`/accounts`) — 연동 목록(채널/업체코드/계정ID + 해제 버튼) + 연동 폼(키 입력, Secret 은 password 타입) + **한도 표시(count/max)**, 한도 도달 시 폼 잠그고 요금제 링크. Nav 에 "계정 연동" 추가, `App.jsx` 라우트 + `SpaForwardingController` 에 `/accounts` 포워드 추가. 대시보드 "계정 없음" 안내가 `/accounts` 로 링크.
  - ⚠️ **키 보안**: AccountView/응답/로그에 access/secret 키 절대 미노출(목록은 id/channel/vendorId 만). 연동 폼 입력 키는 저장 후 화면에서 비움.
  - 검증(curl, 신규 유저로): 로그인→1번째 연동 **201** → 목록(키 미노출) → 2번째 연동 **400(FREE 1개 한도)** → 빈 vendorId **400(검증)** → 타 유저 계정 #1 해제 시도 **400 "없음"**(열거 차단) → 본인 계정 해제 **204** → 목록 빈 배열 → 슬롯 풀려 재연동 **201** → 데모 계정 #1·대시보드 무손상 200. (signup 은 세션 미생성 → 연동 전 login 필요 확인.)

- [x] **수동 동기화 트리거(라이브 키 검증용)** — 연동 직후 스케줄러(30분/1시간)를 기다리지 않고 즉시 한 계정 수집. `POST /api/me/accounts/{id}/sync`(로그인+소유 필수). `account/ManualSyncService`(소유 확인→주문/정산/반품 ingest 를 **소스별 예외 격리** 실행, lookback `coupang.manual-sync-lookback-days:14`) + `SyncResult`(소스별 `{ok,count,error}`, 일부 실패해도 전체 200 으로 결과 반환 → 어디가 막혔는지 화면/로그로 즉시 확인). 프론트 `/accounts` 각 계정 행에 "지금 동기화" 버튼 + 결과 표시. 키는 결과/로그에 미노출(transactionId 등만).
  - 🔎 **[라이브 검증 결과 — 중요]** 시드 가짜 키(SEEDVENDOR)로 동기화 시 쿠팡 실 API 에 실제로 도달함:
    - **주문/반품 → 401 "Specified key is not registered"** = 경로·HMAC 서명 **구조는 정상**(키만 가짜). 실 키 넣으면 동작 예상.
    - **정산 → 404 "No exactly matching API specification for `/api/v1/vendors/{vendorId}/revenue-history`"** = **정산 엔드포인트 경로가 틀림**. → **[해결됨]** 라이브 문서('매출내역 조회') 기준으로 경로를 `/v2/providers/openapi/apis/api/v1/revenue-history`(vendorId 는 쿼리), 응답을 중첩 구조(`data[].items[]`)로 수정. 위 "정산 수집" 항목 참고. 실 키로 200 응답 최종 대조만 남음.
  - [x] Phase 3(토스 빌링 스캐폴딩): `billing` 패키지. **실 키 미설정이어도 안전하게 빌드/배포**되는 골격(키 주입만 하면 동작).
    - `TossBillingClient`(RestClient, Basic 인증=secretKey+`:` Base64): `issueBillingKey(authKey,customerKey)` → POST `/v1/billing/authorizations/issue`, `charge(billingKey,customerKey,amount,orderId,orderName)` → POST `/v1/billing/{billingKey}`. 시크릿 키가 placeholder/공백이면 `isConfigured()=false` 라 `ensureConfigured()` 가 `IllegalStateException` 으로 호출 차단.
    - `BillingService`: `subscribe(userId,authKey)`(customerKey 없으면 UUID 생성→빌링키 발급·**암호화 저장**→첫 달 결제→ACTIVE + `currentPeriodEnd=now+1M`), `renewDue(now)`(ACTIVE & 주기 만료분 재청구, 유저별 예외 격리, 실패→PAST_DUE, 빌링키 없음→PAST_DUE), `cancel(userId)`(상태만 CANCELED, 남은 기간 유지). orderId=`customerKey+yyyyMM` 로 **중복청구 방지**.
    - `BillingScheduler`: 매일 03:10 KST `renewDue`. `BillingController`: `GET /api/billing/status`(활성여부), `POST /api/billing/subscribe`(세션 필수, 401/503), `POST /api/billing/cancel`. `SubscribeRequest(@NotBlank authKey)`.
    - DB: `V3__billing.sql` — `users` 에 `billing_key_encrypted`(BYTEA, 암호화), `billing_customer_key`, `last_billed_at` + 정기결제 조회 인덱스. `User` 엔티티에 동 필드(`billingKey` 는 `@Convert(EncryptedStringConverter)`).
    - 설정: `application.yml` `toss.billing.secret-key=${TOSS_SECRET_KEY:test_sk_PLACEHOLDER}`, `base-url=${TOSS_BASE_URL:...}`. `ApiExceptionHandler` 에 `IllegalStateException`→**503**(기능 준비중) 추가, basePackages 에 billing 추가.
    - 검증: status `{"enabled":false}`, 미로그인 subscribe 401, 로그인+키미설정 subscribe **503**("토스 시크릿 키 미설정"), authKey 공백 400, cancel 200(CANCELED). 기존 대시보드/plans 200 회귀 없음. V3 Flyway 적용·Hibernate validate 통과 확인.
    - ⚠️ [확정 필요] **실 토스 키**(`TOSS_SECRET_KEY`) 와 토스 응답 JSON 필드(`billingKey`/`status`/`paymentKey`)·에러 코드 처리(재시도/연체 안내 정책)는 키 확보 후 라이브로 마무리. 현재는 골격만.

- [x] **프론트(React/Vite SPA)** — `frontend/`(Vite+React+React Router). 바닐라 `index.html` 을 대체.
  - **같은 오리진 전략**: `vite build` 산출물이 `src/main/resources/static/` 으로 나가 Spring Boot 가 SPA+API 를 함께 :8088 로 서빙 → **세션 쿠키(JSESSIONID)가 CORS 없이 동작**. 개발(`npm run dev`, :5173)은 `/api` 를 :8088 로 프록시.
  - 화면: **로그인/가입**(`/login`,`/signup`, 가입 즉시 자동 로그인), **대시보드**(`/`, 기존 순이익·반품사유·원가/기타비용 입력 100% 이식), **요금제**(`/pricing`, `/api/plans`+`/api/subscription`+`/api/billing/status`, PRO 구독/해지). 인증 상태는 `auth.jsx`(앱 시작 시 `GET /api/auth/me`), 비로그인이면 `/login` 으로 보내는 **벽** 적용.
  - `SpaForwardingController`(`web` 패키지): `/login`,`/signup`,`/pricing` 딥링크/새로고침을 `index.html` 로 forward(클라이언트 라우팅 유지). 새 라우트 추가 시 여기에도 추가.
  - 결제 버튼은 `billing.status.enabled`(토스 키 설정 여부)로 게이팅 — 키 미설정 시 비활성+안내. 토스 SDK 카드등록(authKey 발급) 실연동은 `Pricing.jsx` 의 `subscribe()` TODO 로 표시(키 확보 후 마무리).
  - ⚠️ **빌드 산출물(`static/index.html`,`static/assets/`)을 커밋**한다(단일 jar 가 그대로 동작하게). **프론트 코드 수정 시 `cd frontend && npm run build` 후 커밋** 필요. (Gradle-node 통합 자동화는 후속 과제 — 지금은 수동 빌드.) `frontend/node_modules`·`dist` 는 gitignore.
  - 검증: :8088 루트가 React 앱 서빙(asset 200, `text/javascript`), `/login`·`/pricing` 딥링크 forward 200, 미리보기로 로그인 화면 렌더+벽 동작 확인(콘솔 에러 0, 라우터 future-flag 경고만). signup→login→me→대시보드/plans API 200.

## 다음 단계 (여기서 이어서)

실수익이 목표라 "런칭에 필요한 순서"로 재정렬했다. 사업자등록 전에도 할 수 있는 것부터.

### A. 사업자등록 전에 지금 할 수 있는 것 (실 키 불필요)

1. **[실수익 1순위] 배포/호스팅** — 현재 localhost 전용이라 고객이 접속 불가. 클라우드 서버 + 도메인 + HTTPS + 운영 DB(PostgreSQL) 구성. 운영 환경변수(`APP_ENCRYPTION_KEY`, DB 접속, 추후 `TOSS_SECRET_KEY`) 주입 체계. 프론트 빌드 산출물 포함 단일 jar 배포.
2. **조회 기간 한도(dashboardLookbackDays) 게이팅** — 계정 수 한도(maxMarketAccounts)는 적용 완료. 남은 건 대시보드 from/to 가 플랜 기간(FREE=30일)을 넘으면 자르거나 거부. 서버 강제.
3. **토스 빌링 실 키 마무리** — 토스는 **무료 테스트 키를 사업자 없이 즉시 발급** 가능(단, 실 수금=라이브 키는 사업자 후). 테스트 키로 SDK 카드등록(`Pricing.jsx` subscribe TODO) + 빌링키 발급·첫 결제·갱신 플로우를 라이브로 확정. 실 키/에러코드 정책은 사업자 후.
4. **프론트 빌드 Gradle 통합**(선택) — `npm run build` 를 Gradle 빌드에 묶어 산출물 커밋 제거.
5. (보강 후보) 반품 사유 표준화(쿠팡 사유 코드 매핑), 사유 추세(기간 비교).
6. **[신규 수익기능] 광고 ROI × 순이익 옵티마이저** — `docs/ad-roi-spec.md` / `docs/ad-roi-tasks.md`. CSV/수기 광고비 → SKU 귀속 → "광고 돌릴수록 손해인 SKU" 적발. 실 키 불필요(사업자 전 가능). ⚠️ 이중차감 주의: 광고비는 이제 `ad_spends` 로만 관리하고 `ProfitCalculationService` 기타비용 배분에서 광고성 비용 제외(불변식: 전체 순이익 합계 불변). Flyway 다음 번호 V4.

### B. 사업자등록 후에만 가능한 것 (게이팅)

1. **실 쿠팡 키 라이브 검증** — 계정 연동 입구 완성(`POST /api/me/accounts` → `/accounts` 화면). 실 vendorId/키로 연동→수동 동기화(`POST /api/me/accounts/{id}/sync`)를 돌려 `[검증 필요]` 마커(JSON 필드명·페이징 토큰·정산/반품 라인 고유 id)를 라이브로 확정.
   - **정산 엔드포인트 경로 404 는 문서 기준으로 수정 완료.** 남은 건 실 키로 200 응답을 받아 필드명·REFUND 음수 부호·고유 id 유무를 대조하는 것.
   - 대안: 사업자등록 전이라도 **쿠팡 셀러인 베타 테스터의 실 키**를 받아 입력하면 검증 가능(키는 AES-GCM 암호화 저장).
2. **토스 실 수금 전환** — 사업자 PG 계약 후 라이브 키(`TOSS_SECRET_KEY`) 주입 + 응답/에러 코드(재시도/연체) 정책 확정.

> 로컬에서 눈으로 확인하는 법은 아래 "빌드 / 실행 → 시드로 로컬 확인" 참고.

## 쿠팡 API 핵심 주의사항 (중요)

- **HMAC 서명**: `message = signed-date + method + path + query` (query 는 `?` 제외).
  signed-date 포맷 `yyMMdd'T'HHmmss'Z'`(GMT), 서명은 HMAC-SHA256 의 **소문자 hex**.
  헤더 형식: `CEA algorithm=HmacSHA256, access-key={}, signed-date={}, signature={}`
- **401 의 주원인**: 서명에 쓴 query 문자열과 실제 전송 query 가 1바이트라도 다르면 실패.
  → 동일한 query 문자열을 서명/요청에 함께 사용할 것. URI 를 직접 만들어 RestClient 의 재인코딩을 피한다.
- **vendorId(업체코드)**: 잘못 넣으면 404. 반복되면 쿠팡이 IP/vendorId 를 차단할 수 있으니 정확히.
- **v4 응답**: `salesPrice` 는 단가(숫자), `orderPrice` 는 합계. orderedAt 은 존 정보 없는 `2024-04-08T22:54:46` 형태 → KST(Asia/Seoul)로 간주해 변환.
- **트랜잭션 주의**: HTTP 호출을 긴 단일 트랜잭션 안에 넣지 말 것. 수집(HTTP)은 트랜잭션 밖, 영속화는 페이지/건 단위로.
- **반품/취소**: 발주서 목록에선 완료 반품 조회 불가 → 별도 '반품/취소 요청 목록 조회' API 필요(추후).

## 매핑 규칙 (쿠팡 → 우리 스키마)

- `orderId` → `order_items.coupang_order_id`
- `orderItems[].vendorItemId`(숫자) → `String` 으로 변환 후 `vendor_item_id`
- `shippingCount` → `quantity`, `salesPrice`(단가) → `sale_price`
- `vendorItemName` → `products.name`, 상품 upsert 키 = (market_account_id, vendor_item_id)
- 매출 진실 원천은 **정산(settlements)** 이며 주문은 수량/COGS 곱셈용. (정산 수집은 주문 다음 단계)

## 보안 규칙 (절대 준수)

- `APP_ENCRYPTION_KEY`(Base64 32 byte), DB 비밀번호는 **환경변수로만**. 절대 커밋 금지.
- 로그에 API access/secret key 출력 금지. (`MarketAccount` 에 `@ToString` 미사용 유지)

## 빌드 / 실행

- **Gradle 9.1.0 래퍼** + `foojay-resolver-convention 1.0.0`(settings.gradle).
  로컬엔 **JDK 25 만 설치돼 있어도** 컴파일용 **JDK 21 툴체인을 첫 빌드 때 자동 다운로드**한다(최초 1회 인터넷 필요).
  프로젝트 타깃은 Java 21(`build.gradle` toolchain).

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=...
export APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
./gradlew bootRun
```

### 시드로 로컬 확인 (쿠팡 키 불필요) — 명령 2개면 끝

`seed` 프로파일은 `application-seed.yml` 에 포트(8088)와 개발용 암호화 키가 박혀 있어
**환경변수 없이** 그대로 띄울 수 있다. 흑자 2 + 적자 1 샘플이 자동 시드된다.

1. Postgres 띄우기 (compose 사용)
   ```bash
   docker compose up -d        # docker-compose.yml 제공됨. 호스트 5433→컨테이너 5432, 데이터는 볼륨에 보존.
   ```
   - 호스트 5432 는 다른 프로젝트(lightdrone-pg)가 점유 → 셀러는 **5433** 사용. 다르게 쓰려면 `DB_PORT` 환경변수.
2. `seed` 프로파일로 앱 실행
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=seed'
   ```
3. 브라우저에서 **앱 열기** → `http://localhost:8088/`
   - 비로그인이면 **로그인 화면**이 뜬다 → "무료로 시작하기"로 가입(즉시 자동 로그인) 후 대시보드 진입.
   - 적자상품 B 가 빨간 배경 + `적자` 뱃지로 맨 위에 보이면 정상.
   - 원본 JSON 확인: `GET http://localhost:8088/api/dashboard/profit?accountId=1`

> 화면은 이제 **React SPA**(`frontend/`, Vite). :8088 이 서빙하는 건 빌드 산출물(`static/`).
> **프론트 수정 시**: `cd frontend && npm run build`(산출물이 `static/` 으로 나감) 후 앱 재기동.
> **프론트 개발(핫리로드)**: `cd frontend && npm run dev` → `http://localhost:5173`(/api 는 :8088 로 프록시).
> 시드를 다시 깔고 싶으면 `docker compose down -v` 로 볼륨까지 지우고 1번부터.
