# 쿠팡 셀러 순이익 분석 도구 (MVP)

쿠팡 셀러의 **상품별 진짜 순이익(마진율)**을 자동으로 보여주는 도구.
매출이 아니라 "수수료·원가·비용 다 빼고 실제로 얼마 남았나"를 계산한다.

> 기획/스코프 문서는 [`docs/coupang-profit-mvp-spec.md`](docs/coupang-profit-mvp-spec.md) 참고.

---

## 기술 스택

- Java 21 / Spring Boot 3.4
- Spring Data JPA + Hibernate
- PostgreSQL
- Flyway (DB 마이그레이션)
- Gradle (Wrapper 포함 — 별도 설치 불필요)

---

## 사전 준비

1. **JDK 21** 설치
2. **PostgreSQL** 설치 후 DB 생성:
   ```sql
   CREATE DATABASE seller_profit;
   ```
3. **암호화 키 생성** (API 키 암복호화용, Base64 32 byte):
   ```bash
   openssl rand -base64 32
   ```

---

## 실행

환경변수를 설정하고 실행한다. (값은 본인 환경에 맞게)

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=본인_DB_비밀번호
export APP_ENCRYPTION_KEY=위에서_생성한_Base64_키

./gradlew bootRun
```

- 첫 실행 시 Flyway 가 `V1__init.sql` 을 적용해 테이블을 생성한다.
- IntelliJ 에서는 **File > Open** 으로 이 폴더를 열면 Gradle 프로젝트로 자동 인식된다.
  실행 구성(Run Configuration)의 Environment variables 에 위 3개를 넣으면 된다.

> ⚠️ `APP_ENCRYPTION_KEY` 와 DB 비밀번호는 **절대 커밋하지 말 것.**
> `.gitignore` 에 `.env` 류가 제외돼 있다.

---

## 프로젝트 구조

```
src/main/java/com/sellerprofit/
├── SellerProfitApplication.java   # 부트 진입점
├── crypto/                        # API 키 AES-GCM 암복호화
├── domain/                        # JPA 엔티티 (+ type: enum)
└── repository/                    # Spring Data 리포지토리 + 순이익 집계 쿼리

src/main/resources/
├── application.yml
└── db/migration/V1__init.sql      # 초기 스키마 (Flyway)
```

---

## 진행 현황 / 다음 단계

- [x] DB 스키마 (Flyway V1)
- [x] 도메인 엔티티 + 리포지토리 (API 키 암호화 포함)
- [ ] 쿠팡 Open API 연동 (인증 HMAC 서명 → 주문/정산 수집 스케줄러)
- [ ] 순이익 계산 서비스 (기타비용 매출 비율 배분)
- [ ] 대시보드 API + 프론트
- [ ] 인증/구독 결제

---

## GitHub 올리기

```bash
git init
git add .
git commit -m "init: 도메인/스키마/암호화"
git branch -M main
git remote add origin https://github.com/<본인계정>/<저장소>.git
git push -u origin main
```
