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
- `coupang/` — 쿠팡 Open API 연동 (`CoupangHmacSigner` 완료, 이하 작업 중)

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
  - ⚠️ [검증 필요] 쿠팡 매출내역 API의 **엔드포인트 경로·JSON 필드명·페이징 토큰 키·정산 라인 고유 id**는 라이브 문서로 확정해야 함. 해당 부분은 코드에 `[검증 포인트]` 주석으로 표시. 특히 `external_ref` 는 현재 (인식일+상품+유형) 조합 → 고유 id 있으면 교체.
- [x] **순이익 계산 서비스 + 대시보드 API** — `ProfitCalculationService`(기타비용 매출 비율 배분, 마진율) + `ProfitDashboardController`(`GET /api/dashboard/profit`). DTO `ProfitSummary`/`ProductProfit`.
- [x] **로컬 샘플 시드** — `LocalSeedData`(`@Profile("seed")`, 멱등). 흑자 2 + 적자 1 시나리오. 쿠팡 키 없이 대시보드 확인 가능.
- [x] **도메인 팩토리 추가** — `User.create`, `MarketAccount.create`, `Cost.create` (가입/연동/비용입력 + 시드 공용)
- [x] **로컬 E2E 검증 완료** — Docker Postgres + `seed` 프로파일로 기동, `GET /api/dashboard/profit?accountId=1` 응답 확인. 적자상품이 맨 위, 기타비용 매출비율 배분·마진율 정상.
  - 수정: `V1__init.sql` 말미의 예시 쿼리(`:account` 등 named param)가 실행돼 Flyway 문법 에러 → 주석 안내로 교체.
  - 수정: Windows `javac` 기본 charset(CP949)로 한글 리터럴 깨짐 → `build.gradle` 에 `options.encoding='UTF-8'` 고정.
- [x] **정적 대시보드 + 1-명령 로컬 실행** — `static/index.html`(다크, 적자 빨강+뱃지) + `application-seed.yml`(포트 8088, 개발용 고정 키 → env 불필요) + `docker-compose.yml`(로컬 Postgres).
- [x] **반품/취소 수집** — Flyway `V2__return_items.sql`(반품 테이블 + `market_accounts.last_return_synced_at`) + `ReturnItem` 엔티티/리포 + 반품 DTO(`ReturnRequestResponse`/`ReturnRequest`/`CoupangReturnItem`) + `CoupangApiClient.fetchReturnRequests` + `ReturnIngestionService`(receiptId+상품 멱등) + `ReturnSyncScheduler`(1시간, lookback 14일).
  - **순이익 보정**: `findProfitByPeriod` 에 `return_items` CTE 추가 → **COGS 기준 수량 = 주문수량 − 반품수량**(GREATEST 0). 매출(payout)은 정산이 이미 반품을 음수로 반영하므로 추가 차감 안 함(이중 차감 방지). 대시보드에 `반품` 컬럼/`returnedUnits` 노출, 시드에 반품 시나리오 추가.
  - ⚠️ [검증 필요] 반품요청 **엔드포인트 경로·쿼리 키·JSON 필드명(receiptId/createdAt/receiptStatus/returnItems[].purchaseCount)·페이징 토큰 키**는 라이브 문서로 확정. 코드에 `[검증 포인트]` 주석 표시. `external_ref`(receiptId:vendorItemId)는 1접수=상품당 1라인 가정 → 라인이 복수면 순번 추가 필요.
  - 검증: `seed` 재기동 → A 주문100/반품10 → 판매90·COGS 270k, B 주문50/반품5 → 판매45·COGS 405k(적자 유지), `totalReturnedUnits=15`. 적자상품 B 맨 위 정상.

## 다음 단계 (여기서 이어서)

1. **프론트(React/Next)** — 현재는 단일 `static/index.html`(바닐라). 본격 화면: 원가/비용 입력 폼, 키 연동 폼, 기간 필터 UX.
2. **인증/로그인 + 구독 결제**.
3. (보강) 반품 라인이 한 접수번호에 복수로 올 때 `external_ref` 충돌 방지(순번/고유 id), 반품 사유별 통계.

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
   docker compose up -d        # docker-compose.yml 제공됨. 데이터는 볼륨에 보존.
   ```
2. `seed` 프로파일로 앱 실행
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=seed'
   ```
3. 브라우저에서 **대시보드 열기** → `http://localhost:8088/`
   - 적자상품 B 가 빨간 배경 + `적자` 뱃지로 맨 위에 보이면 정상.
   - 원본 JSON 확인: `GET http://localhost:8088/api/dashboard/profit?accountId=1`

> 정적 대시보드는 `src/main/resources/static/index.html` (바닐라 HTML/CSS/JS, 빌드 불필요).
> 시드를 다시 깔고 싶으면 `docker compose down -v` 로 볼륨까지 지우고 1번부터.
