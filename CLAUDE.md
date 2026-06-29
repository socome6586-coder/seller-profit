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
- `domain/` — JPA 엔티티 (`User`, `MarketAccount`, `Product`, `OrderItem`, `Settlement`, `Cost`), `domain/type/` 에 enum
- `repository/` — Spring Data 리포지토리 + 순이익 집계 네이티브 쿼리(`ProductRepository.findProfitByPeriod`, 결과는 `ProductProfitRow`)
- `coupang/` — 쿠팡 Open API 연동 (`CoupangHmacSigner` 완료, 이하 작업 중)

## 지금까지 완료된 것

- [x] DB 스키마 (Flyway V1) — 6개 테이블, 멱등 UNIQUE 제약, updated_at 트리거
- [x] 도메인 엔티티 + 리포지토리
- [x] API 키 암호화: 엔티티엔 평문 String, DB엔 BYTEA. `@Convert(converter = EncryptedStringConverter.class)` 로 투명 처리
- [x] `Product.create(...)`, `OrderItem.create(...)` 정적 팩토리 (외부 패키지 생성용)
- [x] `@EnableScheduling` (메인 클래스)
- [x] **`CoupangHmacSigner`** — HMAC-SHA256 인증 헤더 생성

## 다음 단계 (여기서 이어서)

1. **`CoupangApiClient`** — 발주서 목록 조회 호출 (RestClient 사용)
   - 엔드포인트: `GET /v2/providers/openapi/apis/api/v4/vendors/{vendorId}/ordersheets`
   - 쿼리: `createdAtFrom`(yyyy-MM-dd), `createdAtTo`, `maxPerPage`, (선택)`status`, 페이징 `nextToken`
   - Base URL: `https://api-gateway.coupang.com`
   - 헤더: `Authorization`(서명), `X-Requested-By`(vendorId), `Content-Type: application/json;charset=UTF-8`
2. **DTO** — `OrderSheetResponse`(code, message, data[], nextToken), `OrderSheet`(orderId, orderedAt, status, orderItems[]), `CoupangOrderItem`(vendorItemId, vendorItemName, shippingCount, salesPrice ...). Jackson `@JsonIgnoreProperties(ignoreUnknown=true)`
3. **`OrderIngestionService`** — 발주서 수집 → 상품 upsert(`findByMarketAccountIdAndVendorItemId`) → `OrderItem` 멱등 저장(`existsBy...` 또는 UNIQUE 위반 처리) → `MarketAccount.lastOrderSyncedAt` 갱신
4. **`OrderSyncScheduler`** — `@Scheduled` 로 쿠팡 `MarketAccount` 전체 순회 수집
5. 이후: 순이익 계산 서비스(기타비용 매출 비율 배분) → 대시보드 API → 인증/구독

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

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=...
export APP_ENCRYPTION_KEY=$(openssl rand -base64 32)
./gradlew bootRun
```
