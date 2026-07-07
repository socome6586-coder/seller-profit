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
> 런칭에는 사업자등록이 사실상 강제다: **쿠팡 Open API 키**(사업자 인증 필수) 와 **토스페이먼츠 구독료 수금**(PG 계약에 사업자등록+통신판매업 신고 필수) 둘 다 막혀 있다.
> 개인사업자(간이과세)는 홈택스 온라인·무료·1~2일. 실 키 라이브 검증과 실 결제는 사업자등록 전까지 구조적으로 불가(쿠팡 공개 샌드박스 없음).
> 그 전까지는 실 키가 필요 없는 영역(배포·기능 완성)을 진행한다.
>
> **✅ 배포 완료(2026-07-04)** — **https://sellerprofit.co.kr** 에서 실제로 운영 중(iwinv KR1-Lite).
> 더 이상 localhost 전용이 아니다. 재배포 방법은 아래 "운영 배포" 참고.

## 기술 스택

- Java 21 / Spring Boot 3.4
- Spring Data JPA + Hibernate, PostgreSQL
- Flyway (DB 마이그레이션: `src/main/resources/db/migration/`)
- Gradle (wrapper 포함), Lombok
- 프론트: React 18 + Vite + React Router 6, 빌드 산출물이 `src/main/resources/static/` 으로 나가 같은 오리진 서빙

## 패키지 구조 (`com.sellerprofit`)

- `crypto/` — API 키 AES-256-GCM 암복호화 (`AesGcmEncryptor`, `EncryptedStringConverter`)
- `domain/` — JPA 엔티티 (`User`, `MarketAccount`, `Product`, `OrderItem`, `Settlement`, `ReturnItem`, `Cost`), `domain/type/` 에 enum
- `repository/` — Spring Data 리포지토리 + 순이익 집계 네이티브 쿼리(`ProductRepository.findProfitByPeriod`)
- `coupang/` — 쿠팡 Open API 연동. HMAC 서명(`CoupangHmacSigner`) + 클라이언트(`CoupangApiClient`) + 주문/정산/반품 수집 서비스·스케줄러 + DTO(`dto/`)
- `profit/` — 순이익 계산(`ProfitCalculationService`) + 대시보드 API(`ProfitDashboardController` /profit,/returns) + 반품 사유 통계(`ReturnStatsService`)
- `manage/` — 셀러 직접 입력 도메인(원가·기타비용) API. `ManagementService` + 컨트롤러 + `ApiExceptionHandler`
- `auth/` — 세션 인증(BCrypt, HttpSession). `AuthController`(signup/login/logout/me) + `CurrentUser` + `MeController`(내 계정 목록/연동/해제/동기화)
- `account/` — 쿠팡 계정 연동. `AccountConnectionService`(연동/해제, 플랜 한도 강제) + `AccountAccess`(소유권 가드) + `ManualSyncService`(수동 동기화)
- `subscription/` — 요금제. `PlanType`(FREE/PRO 가격·한도 한 곳 고정) + `SubscriptionService` + `SubscriptionController`
- `billing/` — 토스페이먼츠 정기결제 스캐폴딩. `TossBillingClient` + `BillingService` + `BillingScheduler`(매일 03:10 KST) + `BillingController`
- `web/` — `SpaForwardingController`(SPA 딥링크 forward)
- `ads/` — 광고비 인제스트(수기/CSV) + 광고 ROI 집계(SKU 단위 귀속). 상세: `docs/ad-roi-spec.md`
- 프론트: `frontend/`(React18+Vite+React Router6)

## 완료된 기능

- **DB/도메인**: Flyway 스키마, JPA 엔티티+리포지토리, API 키 AES-256-GCM 암호화(엔티티 평문 / DB BYTEA)
- **쿠팡 연동**: HMAC 서명, 발주서(30분)/정산(1시간)/반품(1시간) 수집 스케줄러, 계정별 예외 격리
- **순이익 계산**: 기타비용 매출비율 배분, 반품 반영(COGS 수량 차감), 광고비 반영(광고후 순이익) — `/api/dashboard/profit`, `/api/dashboard/returns`
- **원가·기타비용 수기 입력**: `/api/products/{id}/cogs`, `/api/costs`
- **인증/보안 벽**: 회원가입·로그인·로그아웃(BCrypt+세션), 전 엔드포인트 소유권 검증(`CurrentUser`+`AccountAccess`), 계정 열거 차단
- **쿠팡 계정 연동**: 연동/해제(`/api/me/accounts`) + 플랜별 계정수 한도 게이팅(FREE=1개), 수동 동기화 트리거
- **구독/요금제**: `PlanType`(FREE/PRO), 카탈로그+상태 조회(`/api/plans`, `/api/subscription`), **조회기간 한도(dashboardLookbackDays) 게이팅 완료** — UI(PeriodPicker)와 서버(`SubscriptionService.assertWithinLookback`)가 동일 기준으로 대시보드·반품·광고ROI 3개 엔드포인트 전부 강제
- **토스 빌링 스캐폴딩**: 빌링키 발급/첫결제/갱신/해지, 실 키 미설정 시 503로 안전하게 차단
- **프론트 SPA**: 로그인/가입/랜딩/대시보드/요금제/계정연동/광고ROI, 같은 오리진 서빙(세션 쿠키 CORS 이슈 없음)
- **기간 선택 UX(PeriodPicker)**: 프리셋 칩(오늘/이번 주/이번 달/지난 달/최근 7·30일)+직접선택 달력, 대시보드·광고ROI 공용 컴포넌트, 모바일 반응형+reduced-motion 대응
- **광고 ROI**: CSV/수기 광고비 → SKU(vendor_item_id) 귀속 → 광고손실 SKU 적발(`/api/dashboard/ad-roi`), 이중차감 방지(Cost 테이블에서 AD 타입 제외), 메인 대시보드와 수치 정합성 보장

## 다음 단계 (여기서 이어서)

실수익이 목표라 "런칭에 필요한 순서"로 재정렬했다. 사업자등록 전에도 할 수 있는 것부터.

> **2026-07-07 순서 재확정**(`docs/DECISIONS.md` D11): 지금 목표는 **유료 고객 모집이 아니라 베타테스트**다.
> 사업자등록(홈택스 승인)은 이번 주 내 해결 예정이라 급하지 않고, **토스 실 결제 연동도 베타 기간엔
> 결제를 안 받으므로 자연히 후순위**다. 아래 순서는 이 전제로 재배열했다 — 베타 모집 및 실 데이터
> 검증(1번)이 결제 연동(2번)보다 먼저다.

1. ~~**[실수익 1순위] 배포/호스팅**~~ **완료(2026-07-04)** — `https://sellerprofit.co.kr` 라이브.
   상세는 `docs/DECISIONS.md` D4·D5, 재배포 절차는 아래 "운영 배포" 참고.
2. **[지금 우선] 베타 셀러 5~10명 모집 + 도그푸딩**(조민석 님 본인 쿠팡 데이터로 실사용) —
   `docs/HANDOFF.md` §5 로드맵 7번. 베타 테스터는 본인이 이미 쿠팡 셀러라 자기 사업자등록으로
   발급받은 **실 쿠팡 API 키를 그대로 제공**할 수 있으므로, 우리 회사 사업자등록 여부와 무관하게
   아래 B-1(실 쿠팡 키 라이브 검증)을 지금 바로 진행할 수 있다.
3. **토스 빌링 실 키 마무리**(후순위) — 결제를 아직 안 받을 거라 급하지 않다. 다만 토스는
   **무료 테스트 키를 사업자 없이 즉시 발급** 가능하니(실 수금=라이브 키만 사업자 후), 여유가
   될 때 테스트 키로 SDK 카드등록(`Pricing.jsx` subscribe TODO) + 빌링키 발급·첫 결제·갱신
   플로우를 미리 확정해두는 것도 좋다. 베타테스터에게 지불의사 확인(아래 B, HANDOFF §5-10) 후
   전환해도 늦지 않다.
4. **프론트 빌드 Gradle 통합**(선택) — `npm run build` 를 Gradle 빌드에 묶어 산출물 커밋 제거.
5. (보강 후보) 반품 사유 표준화(쿠팡 사유 코드 매핑), 사유 추세(기간 비교), `AdSpendProvider` 구현체(쿠팡 광고 API 스키마 확정 후).
6. **법적 페이지(`/privacy`,`/terms`)는 베타테스트 기준으로 이미 충분함, 손대지 않아도 됨** —
   남은 `<LegalTodo>` 3곳(사업자등록번호·대표자·주소, 정확한 보유기간, 결제/환불 정책)은
   "아직 존재하지 않는 값이라 비워둔 것"이고 사업자등록 승인·실 결제 시작 시점에 자동으로
   채울 수 있는 항목이라 지금 지어내면 오히려 허위 정보가 된다. 승인 나오면 Privacy.jsx §9만
   채우면 됨(`docs/DECISIONS.md` D11 참고).

### B. 사업자등록 후에만 가능한 것 (게이팅)

1. **실 쿠팡 키 라이브 검증** — 계정 연동 입구는 완성(`/accounts` 화면 → `POST /api/me/accounts` → `POST /api/me/accounts/{id}/sync`). 실 vendorId/키로 연동→수동 동기화를 돌려 `[검증 필요]` 마커를 라이브로 확정.
   - **정산**: 경로는 문서 기준으로 확정 완료 — `GET /v2/providers/openapi/apis/api/v1/revenue-history` (⚠️ vendorId 는 경로가 아니라 **쿼리 파라미터**), 응답은 `data[](주문 묶음) → items[](옵션상품 라인)` 중첩 구조, payout = `saleAmount − (serviceFee + serviceFeeVat)`. 남은 건 실 키로 200 응답 받아 필드명·REFUND 부호·라인 고유 id 유무 대조.
   - **반품**: 엔드포인트 경로·쿼리 키·JSON 필드명(`receiptId`/`createdAt`/`receiptStatus`/`returnItems[].purchaseCount`)·페이징 토큰 키는 아직 라이브 미검증(코드에 `[검증 포인트]` 주석 있음).
   - 대안: 사업자등록 전이라도 **쿠팡 셀러인 베타 테스터의 실 키**를 받아 검증 가능(키는 AES-GCM 암호화 저장).
2. **토스 실 수금 전환** — 사업자 PG 계약 후 라이브 키(`TOSS_SECRET_KEY`) 주입 + 응답/에러 코드(재시도/연체) 정책 확정.

> 로컬에서 눈으로 확인하는 법은 아래 "빌드 / 실행" 참고.

## 쿠팡 API 핵심 주의사항 (중요)

- **HMAC 서명**: `message = signed-date + method + path + query` (query 는 `?` 제외).
  signed-date 포맷 `yyMMdd'T'HHmmss'Z'`(GMT), 서명은 HMAC-SHA256 의 **소문자 hex**.
  헤더 형식: `CEA algorithm=HmacSHA256, access-key={}, signed-date={}, signature={}`
- **401 의 주원인**: 서명에 쓴 query 문자열과 실제 전송 query 가 1바이트라도 다르면 실패.
  → 동일한 query 문자열을 서명/요청에 함께 사용할 것. URI 를 직접 만들어 RestClient 의 재인코딩을 피한다.
- **vendorId(업체코드)**: 잘못 넣으면 404. 반복되면 쿠팡이 IP/vendorId 를 차단할 수 있으니 정확히.
- **v4 응답**: `salesPrice` 는 단가(숫자), `orderPrice` 는 합계. orderedAt 은 존 정보 없는 `2024-04-08T22:54:46` 형태 → KST(Asia/Seoul)로 간주해 변환.
- **트랜잭션 주의**: HTTP 호출을 긴 단일 트랜잭션 안에 넣지 말 것. 수집(HTTP)은 트랜잭션 밖, 영속화는 페이지/건 단위로.
- **반품/취소**: 발주서 목록에선 완료 반품 조회 불가 → 별도 '반품/취소 요청 목록 조회' API 필요.

## 매핑 규칙 (쿠팡 → 우리 스키마)

- `orderId` → `order_items.coupang_order_id`
- `orderItems[].vendorItemId`(숫자) → `String` 으로 변환 후 `vendor_item_id`
- `shippingCount` → `quantity`, `salesPrice`(단가) → `sale_price`
- `vendorItemName` → `products.name`, 상품 upsert 키 = (market_account_id, vendor_item_id)
- 매출 진실 원천은 **정산(settlements)** 이며 주문은 수량/COGS 곱셈용.

## 보안 규칙 (절대 준수)

- `APP_ENCRYPTION_KEY`(Base64 32 byte), DB 비밀번호는 **환경변수로만**. 절대 커밋 금지.
- 로그에 API access/secret key 출력 금지. (`MarketAccount` 에 `@ToString` 미사용 유지)

## 빌드 / 실행

- **Gradle 9.1.0 래퍼** + `foojay-resolver-convention 1.0.0`(settings.gradle).
  로컬엔 **JDK 25 만 설치돼 있어도** 컴파일용 **JDK 21 툴체인을 첫 빌드 때 자동 다운로드**한다(최초 1회 인터넷 필요).

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=...
export APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
./gradlew bootRun
```

### 시드로 로컬 확인 (쿠팡 키 불필요) — 명령 2개면 끝

`seed` 프로파일은 `application-seed.yml` 에 포트(8088)와 개발용 암호화 키가 박혀 있어
**환경변수 없이** 그대로 띄울 수 있다. 흑자 2 + 적자 1 샘플이 자동 시드된다.

1. Postgres 띄우기: `docker compose up -d` (호스트 5433→컨테이너 5432, 다른 프로젝트가 5432 점유 시 대비)
2. `seed` 프로파일로 앱 실행: `./gradlew bootRun --args='--spring.profiles.active=seed'`
3. 브라우저에서 `http://localhost:8088/` → 로그인 화면 → "무료로 시작하기"(즉시 자동 로그인) → 대시보드
   - 데모 로그인: `demo@demo.local` / `demo1234`
   - 적자상품이 빨간 배경 + `적자` 뱃지로 맨 위에 보이면 정상.

> **프론트 수정 시**: `cd frontend && npm run build`(산출물이 `static/` 으로 나감) 후 **백엔드 재시작 필요**
> (Gradle `processResources` 가 `src/main/resources` → `build/resources/main` 복사를 앱 기동 시 1회만 하므로, 이미 떠 있는 프로세스는 새 빌드를 자동으로 못 읽는다 — 재시작 안 하면 이전 화면이 계속 보임).
> **프론트 개발(핫리로드)**: `cd frontend && npm run dev` → `http://localhost:5173`(/api 는 :8088 로 프록시, 재시작 불필요).
> 시드를 다시 깔고 싶으면 `docker compose down -v` 로 볼륨까지 지우고 1번부터.

## 운영 배포 (재배포 절차)

운영 서버: iwinv KR1-Lite(`49.247.139.234`, Ubuntu 22.04, RAM 1GB+스왑 2GB), SSH 는
`~/.ssh/id_ed25519` 키로 `root@49.247.139.234` 접속(비밀번호 없음). 저장소가 **private** 이라
서버가 GitHub 자격증명 없이 clone 못 하므로, `git archive` 로 커밋을 내보내 `scp` 로 옮기는
방식을 쓴다(서버는 GitHub 접근 권한을 아예 가질 필요가 없다는 이점도 있음).

**코드를 바꾸고 배포하려면(로컬 개발 PC 에서):**

```bash
git add -A && git commit -m "..."   # 배포는 반드시 커밋된 것만 나간다(워킹트리 변경 무시)
./deploy.sh                          # 기본값 HEAD. 특정 커밋/태그: ./deploy.sh <ref>
```

`deploy.sh` 가 하는 일: ① `git archive` 로 tarball 생성 → ② `scp` 로 서버 전송 → ③ 서버에서
압축 해제 → ④ `rsync` 로 코드만 갱신(운영 데이터·시크릿은 절대 건드리지 않음, 아래 참고) →
⑤ `docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build` 로
재빌드·재기동.

**서버 쪽에서 코드 갱신과 분리되어 절대 지워지지 않는 것들** (`deploy.sh` 의 rsync exclude):
- `.env.production` — 실 시크릿(DB 비번, `APP_ENCRYPTION_KEY` 등)
- `pgdata/` — 운영 Postgres 데이터 디렉터리(바인드 마운트)
- `backups/`, `backup.log`, `backup.sh` — T12.6 일일 백업(cron, 매일 04:30 KST, 14일 보관)

**DB 직접 확인/수정:**
```bash
ssh root@49.247.139.234
docker exec -it seller-profit-postgres psql -U seller_profit_app -d seller_profit
```
(로컬 유닉스 소켓이라 비밀번호 없이 바로 접속됨. `UPDATE`/`DELETE` 는 반드시 `WHERE` 확인,
`password_hash`/`billing_key_encrypted` 같은 해시·암호화 컬럼은 직접 값을 넣지 않는다.)

**관리자 계정 추가**: 새 이메일을 관리자로 만들려면 서버의 `.env.production` 의
`APP_ADMIN_EMAILS` 에 콤마로 추가한 뒤 `docker compose --env-file .env.production -f
docker-compose.prod.yml up -d app` 로 app 컨테이너만 재기동(그 이메일로 회원가입/로그인하면
즉시 ADMIN 승격 — `AdminBootstrapService` 참고).

**주의**: `docker-compose.prod.yml`/`Caddyfile`/`Dockerfile` 자체를 바꾼 배포는 위 절차로
충분하지만, Postgres 메이저 버전 업그레이드처럼 데이터 마이그레이션이 필요한 변경은
`deploy.sh` 범위 밖 — 별도로 신중하게 다뤄야 한다.
