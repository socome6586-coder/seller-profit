# 결정 기록 (Decisions)

프로젝트 진행 중 내린 설계 결정과 그 이유를 남긴다. 코드/스펙만으로는 "왜"가 사라지기 쉬운
결정 위주로 기록한다.

---

## D1. 광고비(AD) 기존 Cost 데이터 처리 — `ad_spends` 로 이전, 신규 입력은 차단

- **날짜**: 2026-07-01
- **관련**: `docs/ad-roi-spec.md` §6·§7, `docs/ad-roi-tasks.md` T3
- **배경**: `CostType` enum 은 이 기능 이전부터 이미 `AD`/`SHIPPING`/`ETC` 를 가지고 있었다
  (Flyway V1 `costs.cost_type CHECK` 포함). 즉 "광고비를 식별할 타입이 없다"는 상황이 아니라,
  **이미 있는 `AD` 타입이 기타비용 배분(매출 비율)에 함께 섞여 있던 것**이 문제였다 —
  광고비를 SKU 단위로 정밀 귀속하는 `ad_spends`(T1)가 생기면, 같은 돈이
  "기타비용 배분"과 "광고비 SKU 귀속" 양쪽에서 빠지는 이중차감이 발생한다.
- **결정**:
  1. **Flyway 스키마 변경(V5) 없음.** `CostType.AD` enum 값은 그대로 둔다(과거 데이터 호환,
     DB CHECK 제약 유지). 대신 **동작 규칙을 바꾼다**: `AD` 타입 비용은 더 이상 기타비용
     배분 대상이 아니다.
  2. `ProfitCalculationService.calculate()` 의 "기타비용 총액" 집계에서
     `CostType.AD` 를 명시적으로 제외한다(`stream().filter(c -> c.getCostType() != CostType.AD)`).
     → 광고비는 이제 `ad_spends` 로만 관리되고, SKU 귀속은 광고 ROI 집계(`com.sellerprofit.ads`,
     T4)가 담당한다.
  3. **신규 입력 차단**: `POST /api/costs`(`ManagementService.createCost`)는 `costType=AD` 요청을
     `IllegalArgumentException`(400)으로 거부하고, `/api/ads/spends`(수기) 또는
     `/api/ads/spends/import`(CSV)로 안내한다. 그대로 두면 셀러가 "광고비"를 기타비용 폼에
     입력했을 때 조용히 배분에서 빠져(위 2번 규칙) 화면 어디에도 반영 안 되는 것처럼
     보이는 혼란이 생기기 때문 — 입력 시점에 막고 올바른 경로로 유도하는 게 낫다.
     프론트(`Dashboard.jsx` `CostPanel`)에서도 "광고비 (AD)" 선택지를 제거하고 안내 문구를 추가했다.
  4. **기존 시드 데이터 이전**: `LocalSeedData` 가 갖고 있던 `Cost(AD, 50000)` 샘플을 삭제하고,
     동일 금액을 SKU 단위 `ad_spends` 두 건(상품 A 30,000 + 상품 B 20,000 = 50,000)으로
     재시드했다. 로컬/개발 환경 외 운영 데이터는 아직 없다(미배포, 실사용자 0명) — 그래서
     별도 SQL 데이터 마이그레이션 스크립트 없이 시드만 갱신하는 것으로 충분하다고 판단했다.
     (실사용자가 생긴 뒤 과거 `Cost(AD,...)` 행이 쌓여 있다면, 그때는 금액을 그대로 보존한 채
     `ad_spends`로 옮기는 1회성 관리자 스크립트가 필요 — 지금은 해당 사항 없음.)
- **결과(불변식 검증)**: `ProfitCalculationServiceAdInvariantTest`
  (`src/test/java/com/sellerprofit/profit/`)로 두 가지를 고정했다.
  - AD 비용은 배분 총액(`totalAllocatedCost`)에 포함되지 않는다(이중차감 0).
  - 광고비 X원을 `Cost(AD)` → `ad_spends` 로 옮겨도 "배분 후 순이익 합계 − 이전된 광고비 합계"는
    옮기기 전(광고비까지 기타비용으로 배분하던 계산) 순이익 합계와 정확히 같다 —
    즉 전체 순이익 합계는 불변, SKU 별 분포만 재배치된다.
- **"기존 `/api/dashboard/profit` 회귀 없음(시드 시나리오 값 유지)" AC 의 해석**: 이 AC 는
  "숫자가 byte-for-byte 예전과 같아야 한다"가 아니라 "배분 메커니즘이 non-AD 비용에 대해
  똑같이 동작하고, 크래시/예외 없이 정상 응답한다"로 해석했다. 시드의 AD 비용을
  `ad_spends` 로 이전한 결과, 대시보드 총 순이익 표시값 자체는 (광고비가 더 이상 전체
  배분에 섞이지 않으므로) 달라지지만 — 그 차액은 정확히 이전된 광고비 총액과 같고
  (위 불변식), 이 차액은 앞으로 `GET /api/dashboard/ad-roi`(T4)에서 "광고후 순이익"으로
  다시 드러난다. 돈이 사라지거나 두 번 빠지지 않는다는 게 진짜 불변식이며, 이건 테스트로
  증명됐다.

---

## D2. 메인 대시보드("진짜 순이익")에 광고비 반영 — `unallocatedAdSpend` 를 문자 그대로 대신 "전체 − 매칭"으로 정의

- **날짜**: 2026-07-01
- **관련**: `ProductRepository.findProfitByPeriod`/`sumAdSpendByPeriod`, `ProfitCalculationService`,
  `ProfitSummary`/`ProductProfit`, `AdRoiService`
- **배경**: D1(T3)까지는 `/ad-roi` 화면만 광고비를 반영했고, 메인 대시보드(`/`,
  `GET /api/dashboard/profit`)의 "진짜 순이익"은 여전히 광고비 반영 前 값이었다. 이번 변경으로
  메인 대시보드 헤드라인·표·정렬·적자 판정을 모두 광고후 기준으로 바꿨다.
- **핵심 설계 결정 두 가지**:
  1. **`ProductProfit` 필드 분리(`preAdProfit` vs `profit`)** — 기존 `profit` 필드는
     `AdRoiService` 가 "광고전 기여이익"으로 그대로 소비하고 있었다. 메인 대시보드가 `profit`
     의미를 광고후로 바꾸면 `AdRoiService` 가 조용히 이중차감(광고비를 또 빼는 셈)하게 된다.
     그래서 옛 의미를 `preAdProfit` 이라는 새 필드로 보존하고, `AdRoiService` 는
     `p.profit()` → `p.preAdProfit()` 한 줄만 바꿔 `/ad-roi` 출력이 byte-for-byte 그대로이게
     했다(회귀 없음은 `AdRoiMainDashboardConsistencyTest` 로 증명).
  2. **`unallocatedAdSpend` 를 "vendor_item_id IS NULL 인 ad_spends 합"이 아니라
     "기간 전체 ad_spends 합 − SKU 로 매칭된 합"으로 정의.** 문자 그대로의 정의는 옳지 않은
     SKU(오타·단종 vendor_item_id 등 이 계정 어떤 상품과도 안 맞는 값)에 걸린 광고비를
     조용히 대시보드 어디에도 안 잡히게 만든다 — money-conservation 위반. 그래서
     `ProductRepository` 에 `products` 테이블과 무관하게 `ad_spends` 전체를 합산하는
     `sumAdSpendByPeriod` 를 별도로 두고, `unallocatedAdSpend = totalAdSpend − Σ(SKU 매칭분)` 로
     계산한다. 이는 `/ad-roi`(`AdRoiService.unassignedAdSpend`)가 이미 쓰던 것과 정확히 같은
     공식이라, 두 화면의 미할당 광고비가 항상 일치한다(같은 `ad_spends` 테이블을 서로 다른
     쿼리로 읽을 뿐).
  3. **`profit` 코어(`profit` 패키지)가 `ads` 도메인에 Java 레벨로 의존하지 않게 유지** —
     `ad_spends` CTE/합계는 `ProductRepository`(네이티브 SQL) 안에서만 등장한다.
     `ads.domain.AdSpendRepository` 를 참조하지 않으므로 `ads → profit`(기존 방향, `AdRoiService`
     가 `ProfitCalculationService` 를 씀) 하나의 의존 방향만 유지되고 순환 의존이 생기지 않는다.
- **결과(검증)**: `AdRoiMainDashboardConsistencyTest`
  (`src/test/java/com/sellerprofit/ads/`)가 (1) 메인 총순이익 = Σ(/ad-roi 광고후 순이익) −
  미할당광고비, (2) 두 화면의 SKU 별 순이익·총광고비·미할당광고비가 항상 같음을 고정한다.
  시드(seed) 라이브 검증: `GET /api/dashboard/profit?accountId=1` → `totalProfit=345000.00`,
  적자상품 B `profit=-159736.84`; `GET /api/dashboard/ad-roi?accountId=1` 의
  `totalAdSpend`/`unassignedAdSpend`/SKU 별 `postAdProfit` 이 메인 대시보드 값과 정확히 일치.

---

## D3. 관리자(Admin) 기능(T10) — role 강제 위치, COMP/PAID 분리, 마지막 관리자 잠금

- **날짜**: 2026-07-03
- **관련**: `docs/admin-tasks.md`, `com.sellerprofit.admin.*`(`AdminAccess`, `AdminController`,
  `AdminGrantService`, `AdminRoleService`, `AdminAuditService`), `SubscriptionService`,
  `BillingScheduler`, `frontend/src/pages/Admin.jsx`, `App.jsx`(`AdminOnly`)
- **배경**: 무상 체험/제휴 등으로 특정 유저에게 PRO 를 결제 없이 지급해야 하는 운영 니즈가
  생겼다. 세션 기반 수동 인증만 있는 프로젝트라 Spring Security 의 role 계층이 없고,
  `@PreAuthorize` 류 선언적 가드도 없다 — role 강제를 어디서/어떻게 걸지가 핵심 결정이었다.
- **결정**:
  1. **role 강제는 컨트롤러 메서드마다 `AdminAccess.requireAdmin(HttpServletRequest)`
     명시 호출로.** 클래스 레벨 인터셉터나 URL 패턴 기반 필터 대신, 각 `@GetMapping`/
     `@PostMapping` 첫 줄에서 명시적으로 호출한다. 코드가 조금 반복되지만, 새 관리자
     엔드포인트를 추가하면서 이 한 줄을 빠뜨리는 실수가 코드 리뷰에서 바로 눈에 띄고,
     "이 메서드는 관리자 전용이다"가 필터 설정 파일이 아니라 메서드 본문에 있어 추적이
     쉽다. UI(Nav 조건부 렌더 + `AdminOnly` 라우트 가드)는 편의일 뿐이고, 실제 방어선은
     이 서버 호출 하나뿐이라는 걸 각 컨트롤러 Javadoc 에 반복 명시했다.
  2. **`User.source`(FREE/PAID/COMP) 컬럼으로 결제 구독과 무상 지급을 분리.**
     `AdminGrantService` 가 지급하는 PRO 는 `source=COMP` 로 마킹되고, `BillingScheduler`
     의 일일 갱신 배치는 `source=COMP` 인 유저를 건너뛴다(결제 시도 자체를 안 함 — 실패
     후 강등이 아니라 애초에 대상에서 제외). `AdminRoleService.revoke` 는 `source=COMP`
     인 유저만 대상으로 하고(버튼도 비활성화 + 서버 400), 결제(PAID) 구독은 관리자가
     실수로라도 건드릴 방법이 없다.
  3. **마지막 ADMIN 강등 방지 + 자기 자신 role 변경 금지를 `AdminRoleService` 에서
     DB count 쿼리로 강제.** 클라이언트 조건부 숨김이 아니라 서버가
     `UserRepository.countByRole(ADMIN)` 로 실시간 확인 후 400 을 던진다 — role 을
     동시에 여러 관리자가 바꾸는 레이스에도(마지막 순간 count 재확인이라) 관리자가
     0 명이 되는 상태로 빠지지 않는다.
  4. **감사 로그(`admin_audit`) 는 모든 지급/회수/role 변경에 동기 기록**, `detail` 은
     Hibernate 6 네이티브 JSON 매핑(`@JdbcTypeCode(SqlTypes.JSON)`)으로 저장하고,
     조회 시(`AdminAuditService.list`) `ObjectMapper` 로 `Map<String,Object>` 로 복원해
     프론트가 구조화된 값을 그대로 렌더링한다(문자열 JSON 을 프론트에서 다시 파싱할
     필요 없음).
  5. **도메인 우회 금지 유지** — `AdminGrantService`/`AdminRoleService` 는 `User` 필드를
     직접 건드리지 않고 항상 `SubscriptionService`(플랜 변경) 또는
     `UserRepository`+엔티티 setter(role) 를 관리자 오케스트레이션 트랜잭션 안에서 호출한다.
  6. **프론트 `AdminOnly` 라우트 가드(`App.jsx`)는 방어가 아니라 UX** — 비관리자가 주소를
     직접 입력해도 즉시 `/dashboard` 로 리다이렉트해 빈 화면/403 에러 페이지를 안 보여주는
     용도이며, 실제 데이터 방어는 위 1번(서버 `AdminAccess`)이 전부 한다. 브라우저로
     `t10-4-check@test.local`(비관리자) 로그인 → `/admin` 직접 진입 시 클라이언트 가드가
     `/dashboard` 로 되돌리는 것을 실측 확인했다.
- **결과(검증)**: `AdminControllerTest`(`@WebMvcTest`) 가 모든 `/api/admin/**` 엔드포인트에
  대해 비로그인(401)·비관리자(403)·정상(200) 3 케이스를 전부 고정. `AdminRoleServiceTest`
  가 마지막 관리자 강등 차단·자기 자신 강등 차단을 서비스 레벨에서 증명.
  `SubscriptionServiceTest`/`BillingScheduler` 쪽 테스트가 `source=COMP` 스킵을 고정.
  브라우저(Chrome MCP) 로 실제 로그인 → 지급(2 개월 PRO) → 유저 표 즉시 갱신 → 비관리자
  직접 진입 차단까지 end-to-end 로 확인.

---

## D4. T12.2 도메인 확정(`sellerprofit.co.kr`) + T12.3 운영 배포 설정 작성

- **날짜**: 2026-07-04
- **관련**: `docs/deploy-tasks.md` T12.2·T12.3, `Dockerfile`, `docker-compose.prod.yml`, `Caddyfile`,
  `.env.production.example`, `.gitignore`
- **T12.2 확정**: 도메인 `sellerprofit.co.kr` 로 최종 확정(조민석 님 직접 구매·DNS 연결 예정,
  이 저장소 작업 범위 밖). 서버는 **iwinv KR1-Lite**(Ubuntu, Docker 29.6.1 / Compose v5.3.0),
  `ufw`로 22/80/443 허용, 스왑 2GB 적용 완료 상태에서 T12.3 을 진행함. 도메인 DNS 전파 전이라
  실서버 배포(T12.4)는 이번 범위에 포함하지 않았다 — 그 전에 준비 가능한 T12.3 산출물만 작성.
- **T12.3 설계 결정**:
  1. **`app` 서비스는 자체 `Dockerfile`(멀티스테이지)로 빌드.** deploy-tasks.md 는
     "`./gradlew bootJar` 산출물로 만든 이미지 또는 서버에서 직접 빌드" 둘 다 허용했는데,
     서버에 Node/Gradle을 별도 설치하지 않고 Docker 만으로 재현 가능하게 하기 위해 이미지
     빌드 쪽을 택했다. 1단계(node:20-alpine)에서 프론트(Vite) 빌드 → 2단계
     (eclipse-temurin:21-jdk)에서 그 산출물을 `src/main/resources/static` 에 넣고 Gradle
     `bootJar`(`-x test`, 테스트 제외 — 배포 이미지 빌드가 테스트 DB 가용성에 좌우되지 않게)
     → 3단계(eclipse-temurin:21-jre)는 jar 만 복사해 이미지 크기를 최소화했다.
  2. **JVM 힙 상한을 `docker-compose.prod.yml` 의 `JAVA_TOOL_OPTIONS: -Xmx384m` 로 명시.**
     KR1-Lite 는 RAM 이 넉넉하지 않아(스왑 2GB 를 적용해야 했던 이유와 동일) Postgres+JVM+Caddy
     세 프로세스가 동시에 뜬다. 힙을 무제한으로 두면 JVM 이 컨테이너 메모리를 다 차지해 다른
     두 프로세스가 OOM Kill 될 위험이 있어, 기본값을 보수적으로 잡고 `.env.production` 의
     `APP_JAVA_OPTS` 로 서버 RAM 에 맞춰 조정 가능하게 열어뒀다.
  3. **`.gitignore` 의 시크릿 차단 규칙에 구멍 발견 및 수정.** 기존 규칙(`.env`, `*.env`)은
     정확히 `.env` 로 끝나는 파일만 걸러 `.env.production`(중간에 `.production` 이 붙어
     `.env` 로 끝나지 않음)은 커밋 방지 대상이 아니었다 — deploy-tasks.md AC("시크릿이
     저장소에 없음, `.env.production` 은 .gitignore 등록")를 그대로 따르려면 반드시
     고쳐야 하는 부분이라 `.gitignore` 에 `.env.production` 을 명시적으로 추가했다.
     `.env.production.example`(값 없는 템플릿)은 파일명이 달라 이 규칙에 걸리지 않으므로
     그대로 커밋 대상이다. 같은 이유로 운영 Postgres 바인드 마운트 디렉터리 `pgdata/` 도
     추가했다(서버에서 저장소 안에 생성될 경우 DB 파일이 git 추적되는 것을 막기 위해).
  4. **Caddyfile 에 `www.sellerprofit.co.kr` → `sellerprofit.co.kr` 리다이렉트 블록 추가.**
     deploy-tasks.md T12.2 는 "`@` 와 `www` 둘 다, 또는 서브도메인 하나만" 연결 가능하다고
     명시했다 — `www` 를 연결하고도 Caddyfile 에 해당 블록이 없으면 그 호스트로 오는 요청은
     어떤 site block 에도 안 걸려 인증서도 못 받고 실패한다. 연결 안 해도 이 블록은 트래픽을
     받지 않으므로 무해해서 미리 넣어뒀다.
  5. **프로젝트명 고정(`name: seller-profit-prod`).** 로컬 검증 중 실제로 발견 — 지정하지
     않으면 디렉터리명이 기본 프로젝트명이 되어, 같은 디렉터리의 로컬 개발용
     `docker-compose.yml`(프로젝트명도 기본 `seller-profit`)과 default 네트워크를 공유하는
     것을 확인했다(운영 서버엔 로컬 compose 파일이 없어 실제 배포엔 영향 없지만, 명시적으로
     분리해두는 게 더 안전하다고 판단).
  6. **AC 재현(자기 검수) — "될 것 같다"가 아니라 실제로 돌려봄:**
     - `docker compose -f docker-compose.prod.yml config` 문법 검증 통과(값 없이도,
       `.env.production.example` 로 치환해도 둘 다 exit 0).
     - `caddy validate` 로 `Caddyfile` 문법 검증 통과 — "enabling automatic HTTP->HTTPS
       redirects" 로그로 HTTPS 강제가 Caddy 기본 동작으로 충족됨을 확인.
     - `docker build .` 로 3단계 전체(프론트 빌드→`bootJar`→런타임) 이미지 빌드 성공(53초,
       BUILD SUCCESSFUL) 확인.
     - **`docker compose --env-file <임시 시크릿> -f docker-compose.prod.yml up -d` 로
       postgres+app+caddy 3개 컨테이너를 실제로 함께 기동.** 앱 로그에서 Flyway 가
       `v1~v6` 마이그레이션을 빈 DB 에 자동 적용하고 `Started SellerProfitApplication` 까지
       확인, `SELECT count(*) FROM users` = `0`으로 시드/데모 데이터 없이 깨끗하게 시작함을
       실측 확인(AC "시드 없이 운영 DB 가 깨끗하게 시작"의 사전 증거 — 실제 운영 DB 검증은
       T12.5 몫이지만 compose/이미지 설정 자체가 이 조건을 만족하는지는 지금 확인 가능해 확인함).
       Caddy 는 예상대로 `sellerprofit.co.kr`/`www.sellerprofit.co.kr` 인증서 발급에 실패했다
       (`no valid A records found` — DNS 가 아직 이 로컬 머신을 가리키지 않으므로 당연한
       실패이자 오히려 Caddyfile 이 실제 도메인으로 정상 동작을 "시도"한다는 증거). 이후
       Let's Encrypt 재시도 요청이 쌓이는 것을 막기 위해 검증 즉시
       `docker compose down -v` 로 컨테이너·볼륨·임시 이미지·임시 env 파일을 모두 정리했다.
- **범위 밖으로 남긴 것**: T12.4(실서버 배포)·T12.5(스모크 테스트)·T12.6(백업 cron)은 도메인
  DNS 연결 전이라 이번에 진행하지 않았다. `CLAUDE.md` "다음 단계 A-1"도 아직 완료로 갱신하지
  않았다 — 실제 `https://sellerprofit.co.kr` 접속 확인 전까지는 미완료로 두는 게 맞다고 판단.

---

## D5. T12.4 실서버 배포 완료 — `git archive` + Windows `core.autocrlf` 로 `gradlew` 깨지는 문제 발견·수정

- **날짜**: 2026-07-04
- **관련**: `docs/deploy-tasks.md` T12.4, `.gitattributes`(신규), 서버(iwinv KR1-Lite, `49.247.139.234`)
- **배포 방식**: 저장소가 private 이라 서버에서 직접 `git clone` 이 안 됨(비인증 GitHub API 확인:
  404) → deploy-tasks.md 가 허용한 대안인 "rsync" 대신, 커밋된 파일만 정확히 옮기기 위해
  `git archive HEAD` 로 tarball 을 만들어 로컬(Windows)에서 `scp` 로 전송 → 서버에서 `tar -xzf`.
  이 방식은 운영 서버가 GitHub 자격증명(PAT 등)을 전혀 가질 필요가 없다는 부수적 이점도 있다.
- **버그 발견**: 첫 배포 시도에서 `RUN ./gradlew bootJar` 가 `/bin/sh: 1: ./gradlew: not found`
  로 실패(exit 127). 컨테이너에 직접 들어가 확인한 결과 실제 에러는
  `bad interpreter: /bin/sh^M: No such file or directory` — `git archive` 가 Windows 의
  `core.autocrlf=true` 설정을 그대로 적용해 `gradlew`(셸 스크립트)의 개행을 LF→CRLF 로 바꿔
  버린 것이 원인이었다(`git show HEAD:gradlew` 로 본 blob 자체는 LF 였지만, `git archive` 산출물은
  CRLF — 즉 문제는 저장소가 아니라 **Windows 에서의 export 시점**에 있었다).
- **즉시 조치**: `git -c core.autocrlf=false archive ...` 로 재생성해 배포를 통과시켰다(1회성 우회).
- **근본 수정**: `.gitattributes` 신설, `gradlew text eol=lf` / `*.sh text eol=lf` 선언.
  커밋 후 `git archive HEAD gradlew`(옵션 없이, autocrlf 오버라이드 없이)가 LF 를 유지하는 것을
  실측 확인했다 — 이제부터는 어떤 로컬 git 설정에서 archive/clone 하든 이 문제가 재발하지 않는다.
  (이 저장소는 원래 Java/Gradle 프로젝트라 실행 가능한 셸 스크립트가 `gradlew` 뿐이라 범위를
  `gradlew`+`*.sh` 로 좁혔다 — 과도한 일반화 없이 실제 겪은 문제만 고쳤다.)
- **배포 결과(자기 검수, 실제 재현)**:
  - 서버: iwinv KR1-Lite(RAM 957Mi, 스왑 2GB), Ubuntu 22.04.5, Docker 29.6.1/Compose v5.3.0 —
    문서에 적힌 사양과 SSH 로 직접 접속해 실측 일치 확인.
  - `docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build` 로
    3개 컨테이너(postgres/app/caddy) 전부 기동. `SPRING_PROFILES_ACTIVE` 는 비워둬 기본
    프로파일로 뜸(`seed` 아님) — 로그에 "No active profile set, falling back to 1 default
    profile" 확인.
  - Flyway 가 빈 DB 에 `v1~v6` 마이그레이션 자동 적용, `Started SellerProfitApplication`
    확인(부팅 22초).
  - Caddy 가 `sellerprofit.co.kr`/`www.sellerprofit.co.kr` 양쪽 다 Let's Encrypt 인증서
    발급 성공(로그: "certificate obtained successfully").
  - 로컬 머신에서 `curl` 로 3가지 실측: `https://sellerprofit.co.kr/` → `200`(랜딩 페이지
    HTML 정상 응답), `http://sellerprofit.co.kr/` → `308`(HTTPS 로 자동 리다이렉트),
    `https://www.sellerprofit.co.kr/` → `301`(비 `www` 로 리다이렉트, Caddyfile D4-4 설계대로
    동작).
  - `.env.production` 의 `DB_PASSWORD`/`APP_ENCRYPTION_KEY` 는 서버에서 `openssl rand` 로
    직접 생성해 파일에만 저장(`chmod 600`), 채팅/커밋 어디에도 값 자체를 남기지 않았다.
    `APP_ADMIN_EMAILS` 는 조민석 님 실제 이메일(`socome6586@gmail.com`)로 설정.
- **범위 밖으로 남긴 것(이번 요청은 T12.4 까지)**: T12.5(회원가입→로그인→계정연동→로그아웃
  스모크 테스트, `/admin` 접근 확인, 스케줄러 로그 확인, 재부팅 후 컨테이너 자동 복구 확인)와
  T12.6(일 1회 DB 백업 cron)은 아직 진행하지 않았다. `CLAUDE.md` "다음 단계 A-1"도 T12.5/T12.6
  의 공통 AC(스케줄러 지속 동작, 백업 동작 등)가 남아 있어 아직 완료로 갱신하지 않았다.
