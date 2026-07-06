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

---

## D6. T12.5 스모크 테스트 + T12.6 백업 + 재배포 스크립트(`deploy.sh`) — 배포 완료 확정

- **날짜**: 2026-07-04
- **관련**: `docs/deploy-tasks.md` T12.5·T12.6, `deploy.sh`(신규), `CLAUDE.md` "운영 배포" 절
- **실제 유저 상태**: 조민석 님이 실제 이메일(`socome6586@gmail.com`)로 직접 회원가입해
  `role=ADMIN` 으로 승격됨을 DB 에서 확인(`AdminBootstrapService` 가 가입 시점에 즉시 승격 —
  로그인까지 갈 필요 없음). 이건 시드/데모 데이터가 아니라 실제 첫 사용자다.
- **T12.5 스모크 테스트(curl, 실제 재현)**: 운영 도메인에 대해 회원가입(201)→로그인(200,
  세션쿠키)→`/api/auth/me`(200)→`/api/me/accounts`(200, 빈 배열 — 계정연동 화면 정상)→
  비관리자 계정으로 `/api/admin/users` 접근(**403**, 서버가 실제로 관리자만 통과시킴을 확인)→
  로그아웃(204)→로그아웃 후 `/api/auth/me`(**401**, 세션 무효화 확인) 전부 통과. 테스트용
  임시 계정(`smoke-test-t125@example.com`)은 검증 직후 DB 에서 직접 삭제해 운영 DB 에 QA
  잔재를 남기지 않았다(실제 관리자 계정으로 로그인 테스트는 안 했다 — 조민석 님 실 비밀번호를
  요청하는 건 부적절하다고 판단해 제외. `role=ADMIN` 이 DB 에 정확히 설정된 것과, 기존
  `AdminControllerTest`(D3)가 401/403/200 3케이스를 이미 고정한다는 점으로 대신 확인함).
  스케줄러: `OrderSyncScheduler`(1분 후 최초 실행)/`SettlementSyncScheduler`(2분 후)/
  `ReturnSyncScheduler`(3분 후) 로그에서 "대상 계정=0개"로 실제 기동·주기 실행을 확인
  (연동된 쿠팡 계정이 아직 없어 0개인 게 정상). `BillingScheduler`(매일 03:10 KST cron)는
  다음 새벽에나 실행되므로 이번엔 로그로 확인 못함 — 코드/설정상 cron 표현식만 재확인.
- **재부팅 복구 테스트 — 사용자 승인 하에 실서버를 실제로 재부팅해 검증**(구조적 확인만으로
  끝내지 않음): `reboot` 실행 → 서버 다운 확인(요청 `000`/`502`) → 약 40~50초 후 SSH 복귀,
  `docker ps` 로 3개 컨테이너 전부 자동 기동(`docker.service` enabled + 각 컨테이너
  `restart: unless-stopped`) → `https://sellerprofit.co.kr/` 다시 `200` → `users` 테이블
  row 수(1건, 조민석 님 계정)가 재부팅 전후 동일해 데이터도 그대로 보존됨을 확인.
- **T12.6 백업**: `/root/seller-profit/backup.sh`(pg_dump, 14일 지난 덤프는 자동 삭제 —
  디스크가 25GB 뿐이라 무제한 보관은 디스크 고갈 위험이 있어 범위를 살짝 넘어 최소한의
  보존기한만 추가함) + crontab `30 4 * * *`(서버가 이미 Asia/Seoul 타임존이라 그대로 KST)로
  등록. 수동 트리거로 실제 덤프 파일 1개(27KB, `COPY public` 10개 = 테이블 수와 일치) 생성
  확인.
- **`deploy.sh` 신설 — 앞으로의 재배포 절차를 코드로 고정**: deploy-tasks.md T12.4 가 요구한
  "배포 스크립트" 산출물. `git archive`(커밋된 것만 배포) → `scp` → 서버에서 `rsync` 로 코드만
  갱신 → `docker compose up -d --build`. **rsync exclude 로 `.env.production`/`pgdata/`
  (운영 DB 데이터)/`backups/`/`backup.log`/`backup.sh` 를 절대 건드리지 않게 했다** — 이
  구분이 없으면 다음 배포 때 재배포 스크립트가 운영 DB 볼륨이나 시크릿을 지워버릴 위험이
  있었다(실제로 첫 배포 때 `rm -rf` 로 디렉터리를 통째로 밀어버린 적이 있었는데, 그땐
  마침 빌드 실패로 아직 Postgres 컨테이너가 안 뜬 시점이라 데이터 유실은 없었다 — 그
  경험을 바탕으로 재배포 스크립트는 절대 전체 디렉터리를 밀지 않고 코드 파일만 갱신하도록
  설계했다).
- **CLAUDE.md 갱신**: "다음 단계 A-1"을 완료로 표시하고 배포 URL을 상단에 명시. 새 "운영 배포"
  절에 재배포 절차·DB 직접 접속법·관리자 계정 추가법을 남겨, 다음에 어떤 세션이 이어받아도
  같은 절차를 반복할 수 있게 했다.
- **AC 전체 재확인(공통, deploy-tasks.md)**: HTTPS 접속 가능/HTTP 리다이렉트 ✅, 시크릿
  저장소·이미지에 없음 ✅, 시드 없이 깨끗한 시작 ✅, 관리자 부트스트랩 ✅, 스케줄러 지속
  동작 ✅, 재시작 후 자동 복구 ✅(실측), 일 1회 백업 ✅, CLAUDE.md 갱신 ✅ — **전부 충족**.

---

## D7. T13.1 계정 연동 발급 가이드 — 서버 공인 IP 단일 소스는 "백엔드 설정값 + 공개 API" 방식 채택

- **날짜**: 2026-07-06
- **관련**: `docs/onboarding-tasks.md` §2·T13.1, `application.yml`(`app.public-server-ip`),
  `com.sellerprofit.web.PublicConfigController`(신규), `frontend/src/pages/Accounts.jsx`
- **배경**: 계정 연동 화면에 쿠팡 OPEN API 키 발급 가이드를 추가하면서, 4번 단계("IP 주소"
  등록)에 운영 서버 공인 IP(`49.247.139.234`)를 노출해야 했다. 절대 규칙(§2)은 이 값이
  코드 여러 곳에 하드코딩되지 않고 한 곳에서 관리되길 요구했고, 문서 자체가 두 가지 방식을
  예시로 제안했다: (1) 백엔드 설정값을 API 로 내려 프론트가 받아 표시, (2) 최소한 프론트
  단일 상수.
- **결정**: **(1) 백엔드 설정값 + 공개 API** 방식을 택했다.
  1. `application.yml` 에 `app.public-server-ip: ${APP_PUBLIC_SERVER_IP:49.247.139.234}` 추가
     — 기본값은 현재 운영 IP, 배포 환경변수로 오버라이드 가능(다른 `app.*` 설정과 동일한
     패턴, `admin-emails`/`encryption.key` 옆에 나란히 둠).
  2. 인증 불필요한 `GET /api/config` 신설(`PublicConfigController`, `web` 패키지 — 기존
     `SpaForwardingController` 와 같은 위치, 도메인 로직이 아닌 "웹 계층 잡무"라 이 패키지가
     맞다고 판단). `{"publicServerIp": "..."}` 하나만 응답.
  3. 프론트(`Accounts.jsx`)는 마운트 시 `/api/config` 를 호출해 값을 받고, 실패 시
     "조회 실패 — 새로고침 해주세요" 로 표시(값을 프론트에 직접 적지 않으므로 API 실패를
     조용히 숨기지 않고 사용자에게 알림).
  - **(2)안(프론트 상수) 대신 이걸 택한 이유**: 프론트 상수는 빌드 시점에 값이 고정되므로
    서버 이전 시 프론트 재빌드+재배포가 반드시 필요하다. 백엔드 설정값은 서버 쪽
    환경변수(`APP_PUBLIC_SERVER_IP`)만 바꾸고 앱을 재기동하면 끝나, "한 곳만 고치면 된다"는
    원칙에 더 가깝다. API 엔드포인트 하나 늘리는 비용은 이미 `check-email` 같은 공개
    엔드포인트 선례가 있어 크지 않다고 판단.
  - **검증(AC "코드에서 IP 문자열이 2곳 이상 중복되지 않는다")**: `grep -rn "49.247.139.234"
    --include=*.java --include=*.jsx --include=*.js --include=*.yml --include=*.yaml` 결과
    `application.yml` 단 1곳만 매치(`CLAUDE.md` 는 검색 대상 확장자에서 제외되는 문서 파일이라
    별개 — 문서 프로즈는 이 AC 의 "코드"가 아니라고 해석했다).
- **가이드 UI 설계**: 기존 `.landing`/전역 토큰(`--loss`, `--loss-bg`, `.badge`)만 재사용해
  IP 강조 박스를 만들었고, 새 색상은 도입하지 않았다. 아코디언은 별도 JS 상태 없이 네이티브
  `<details open>`/`<summary>` 로 구현(접근성 기본 제공, 기본값 펼침 요구사항 자연스럽게 충족).
- **자기 검수(HANDOFF.md §3)**: Chrome MCP 로 로그인 후 `/accounts` 실측 — 6단계 가이드 노출,
  IP 강조 박스(배지+색상+복사 버튼)와 실제 값(`49.247.139.234`) 표시, WING 바로가기 링크
  `target="_blank" rel="noopener noreferrer"` 확인. 복사 버튼의 `navigator.clipboard.writeText`
  자체는 표준 패턴(문서 전역에서 흔한 구현)이나, 이 원격 자동화 브라우저 환경 특유의 클립보드
  권한 프롬프트가 CDP 평가를 블로킹해 자동화로 "복사됨!" 문구까지 시각 확인은 못 했다(코드
  로직·권한 상태(`navigator.permissions.query` → `granted`)·포커스(`document.hasFocus()` →
  `true`)는 모두 정상 확인). 모바일 폭은 실제 창 리사이즈가 이 환경에서 반영되지 않아, 같은
  오리진 iframe(375px 폭)으로 대체 검증 — 가이드 박스가 줄바꿈되며 가로 스크롤 없이 정상
  렌더링됨을 확인했다(내비게이션 바 줄바꿈 등 기존 전역 반응형 이슈는 이번 변경 범위 밖).
  기존 "새 쿠팡 계정 연동" 폼(제출/검증/에러 처리) 코드 자체는 손대지 않았음을 diff 로 확인.

---

## D8. T14 개인정보처리방침·이용약관 — 회원가입 동의는 체크박스 1개로 통합, 사업자 미등록 항목은 `[작성 필요]` 마커로 남김

- **날짜**: 2026-07-06
- **관련**: `docs/trust-legal-tasks.md` T14, `frontend/src/components/Legal.jsx`(신규),
  `frontend/src/pages/Privacy.jsx`·`Terms.jsx`(신규), `frontend/src/pages/Signup.jsx`,
  `frontend/src/pages/Landing.jsx`, `SpaForwardingController`
- **배경**: 베타 테스터를 받기 전 법적 필수 페이지(개인정보처리방침/이용약관)가 없었고,
  회원가입도 약관 동의 없이 바로 진행됐다. 동시에 이 프로젝트는 아직
  **사업자등록 전**(`CLAUDE.md`) 이라 상호·대표자·사업자등록번호 등은 실제 값이 존재하지
  않는다 — 없는 값을 지어내면 안 된다는 게 이번 작업의 핵심 제약이었다.
- **결정**:
  1. **동의 체크박스는 이용약관+개인정보 수집·이용을 하나로 묶는다.** task 문서가 베타
     단계에서 이를 명시적으로 허용했고, 초기 가입 퍼널에서 체크박스 2개보다 마찰이 적다.
     각 링크(`/terms`, `/privacy`)는 `target="_blank"` 로 열어 동의 전에 실제 내용을 확인할
     수 있게 했다. 서버 측에는 별도 동의 기록 컬럼을 추가하지 않았다 — 현재 세션 인증
     체계에 감사 로그 인프라가 없고, 이번 범위(T14)는 "동의 없이 가입 자체가 안 됨"까지이며
     agreed 상태는 순수 프론트 게이팅(`disabled={busy || !agreed}`)으로 충분하다고 판단했다.
     (동의 이력을 서버에 영구 기록해야 한다는 요구가 생기면 별도 태스크로 다뤄야 한다.)
  2. **Privacy.jsx 내용은 실제 데이터 모델을 근거로 작성, 템플릿 추측 금지.** `User`/
     `MarketAccount` 엔티티를 직접 읽어 실제로 수집되는 항목(이메일, 비밀번호 해시, 휴대전화
     번호, 쿠팡 vendorId+API 키(AES 암호화), 토스 빌링키(AES 암호화))만 명시했고, 카드번호
     원본은 토스 SDK 위젯이 클라이언트에서 직접 처리해 회사 서버에 닿지 않는다는 점도
     코드 확인 후 그대로 반영했다. 프론트에 제3자 분석/추적 스크립트가 전혀 없음을 grep 으로
     재확인하고 "제3자 제공 없음" 조항에 반영했다. 처리위탁 대상은 `CLAUDE.md`(iwinv)와
     `TossBillingClient`(토스페이먼츠) 기준으로 실제 존재하는 두 곳만 기재했다.
  3. **사업자 미등록으로 실제 값이 없는 항목은 `<LegalTodo>` 컴포넌트로 시각적으로
     표시하고, 완료 보고에 별도로 정리해 사용자에게 전달한다** — 상호/대표자/사업자등록번호/
     주소, 개인정보 보호책임자 성명·연락처, 정확한 보유기간 확정, 시행일자(양쪽 문서),
     환불/해지 정책 세부(제6조). 이 항목들은 사업자등록 완료 또는 실제 결제 개시 이후에만
     정확한 값을 채울 수 있으므로 지어내지 않았다.
  4. **`/privacy`·`/terms` 는 로그인 여부와 무관하게 항상 공개.** `Protected` 래퍼로 감싸지
     않았고(`App.jsx`), `SpaForwardingController` 의 forward 목록에도 추가해 직접 URL 접근/
     새로고침 시 404 가 나지 않게 했다 — 로그인 화면·랜딩과 동일한 처리.
  5. **로그인 여부와 무관한 페이지이므로 전역 `Nav` 컴포넌트에 의존하지 않는 별도
     `LegalPage` 래퍼(`Legal.jsx`)를 만들었다.** 로그아웃 상태에서 이 페이지에 들어오면 `Nav`
     가 없으므로, 자체적으로 "← 홈으로" 링크를 포함시켜 항상 돌아갈 방법을 보장했다.
- **검증(자기 검수)**: Chrome MCP 로 랜딩 푸터의 "이용약관 · 개인정보처리방침" 링크가
  실제 DOM 에 렌더링됨을 확인, `/privacy`·`/terms` 를 직접 열어 전체 내용과 `[작성 필요]`
  하이라이트(6곳)가 정상 렌더링됨을 확인. `/signup` 에서는 체크박스 미동의 시
  `button.disabled === true`(실측), `checkbox.click()`(React 가 checkbox onChange 를
  감지하는 실제 네이티브 이벤트 — 이 자동화 환경에서 좌표 클릭이 간헐적으로 씹히는 문제와
  별개로 상태 변화 자체를 검증하기 위해 사용)로 체크 시 `false` 로 즉시 바뀌고 다시 해제하면
  `true` 로 복귀함을 왕복 확인. 기존 Signup 검증(비밀번호 정규식/비밀번호 확인 일치/휴대전화
  정규식/이메일 중복확인)은 `agreed` 가드가 그 뒤에 순서대로 추가된 것만 diff 로 확인해
  회귀 없음을 검증했다. 모바일 폭(390px, 같은 오리진 iframe 대체 검증 — D7 과 동일 사유)에서
  `/privacy`·`/signup` 모두 가로 스크롤 없이 정상 줄바꿈됨을 확인했다.

---

## D9. 사용자 제보 버그 3건 수정(계정연동 자동완성/로그인 폰트/가입 이메일) + T15 신뢰·지원 보강

- **날짜**: 2026-07-06
- **관련**: `frontend/src/pages/Accounts.jsx`, `Login.jsx`, `Signup.jsx`, `styles.css`,
  `frontend/src/contact.js`(신규), `frontend/src/hooks/usePageTitle.js`(신규),
  `frontend/src/components/Nav.jsx`, `Legal.jsx`, `docs/trust-legal-tasks.md` T15
- **배경**: 조민석 님이 스크린샷과 함께 3가지 버그를 제보했다 — (1) `/accounts` 의 쿠팡 계정
  연동 폼(Access Key/Secret Key)에 브라우저가 로그인 이메일/비밀번호를 자동으로 채워 넣는
  문제, (2) 로그인 화면 전체 글씨가 작다는 피드백 + 데모 계정 안내 문구 제거 요청, (3) 회원가입
  이메일 필드에 한글이 그대로 입력·통과되는 문제. 같은 요청에서 T15(신뢰·지원 빠른 보강)를
  이어서 진행하고 전체를 커밋+푸시하라는 지시도 포함됐다.
- **결정**:
  1. **자동완성 문제는 필드 타입/순서를 바꾸지 않고 표준 브라우저 신호만으로 억제.**
     Chrome 비밀번호 관리자는 "이메일 필드 1개 + password 타입 필드 1개"가 있는 폼을 로그인
     폼으로 오인하는 경향이 있다. 폼 자체 필드 구성(vendorId/accessKey/secretKey, 서버 요구
     스펙)은 바꿀 이유가 없으므로, `<form autoComplete="off">` + 각 입력에 고유 `name` +
     Secret Key(`type="password"`)에는 `autoComplete="new-password"`(브라우저가 "저장된
     비밀번호 자동완성 금지"로 인식하는 표준값 — 단순 `off`는 Chrome 이 password 타입 필드에서
     종종 무시함)를 적용했다. 서버 검증 로직·API 스펙은 전혀 손대지 않았다.
  2. **로그인 화면 폰트 확대는 새 CSS 클래스를 만들지 않고 기존 공유 선택자(`.auth-wrap`,
     `.auth-card`, `.auth-switch`) 값만 키웠다.** `.auth-wrap` 은 `Login.jsx` 전용(grep 으로
     다른 사용처 없음을 확인)이고, `Signup.jsx` 는 항상 더 구체적인 `.auth-form-pane .auth-card
     ...` 선택자로 이 값을 재정의하므로(CSS 우선순위상 항상 이긴다) Signup 화면 타이포에는
     영향이 없다 — 실제로 edit 후 Signup 쪽 override 블록이 그대로 남아있음을 재확인했다.
     데모 계정 안내(`데모 체험: demo@demo.local...`)는 요청대로 완전히 제거했다(seed 데모
     계정 자체는 유지 — 로그인 기능에는 영향 없음, 화면 문구만 삭제).
  3. **가입 이메일의 한글 입력 차단은 2중 방어로 구현.** `type="email"` 의 네이티브 브라우저
     검증만으로는 한글 등 비ASCII 문자가 통과되는 사례가 실제로 재현됐다. 기존 `PASSWORD_RE`/
     `PHONE_RE` 패턴과 동일한 스타일로 `EMAIL_RE`(ASCII local-part@domain.tld) 를 추가하고,
     (a) 타이핑 즉시 비ASCII 문자를 제거하는 `stripNonEmailChars`(기존 `formatPhone` 과 같은
     "입력 중 정제" 패턴)와 (b) 제출 시 `EMAIL_RE.test()` 가드를 함께 둬 우회 경로를 없앴다.
  4. **T15.1 문의 채널은 이메일 주소를 한 곳(`contact.js`)에만 두고 여러 화면에서 재사용.**
     Nav(전 화면 공통) + Accounts(연동 실패가 실제로 가장 많이 발생하는 지점, IP 가이드
     바로 아래)에 배치했다 — task 문서가 권장한 배치와 일치.
  5. **T15.2 비밀번호 재설정은 정식 기능을 새로 만들지 않았다.** `AuthController` 를 확인한
     결과 재설정 엔드포인트도 이메일 발송 인프라도 없다(신규 인프라 구축은 이번 요청 범위를
     크게 벗어난다). task 문서가 명시적으로 허용한 축소 범위대로, 로그인 화면 비밀번호 필드
     아래에 "비밀번호를 잊으셨나요? 문의하기"(mailto) 링크만 추가해 최소 안전판을 뒀다.
  6. **T15.3 페이지 제목은 공용 훅(`usePageTitle`)으로 통일.** `index.html` 의 정적
     `<title>`이 모든 라우트에 고정돼 있던 문제라, 각 페이지 컴포넌트 최상단에서 훅을 호출해
     라우트 전환마다 갱신되게 했다. 대시보드는 기존 문구를 그대로 유지(`usePageTitle()` 인자
     없이 호출 → 훅 내부 기본값). `/privacy`·`/terms` 는 각자 반복하지 않고 공용 래퍼
     `LegalPage` 한 곳에서만 `title` prop 을 그대로 넘겨 설정했다. 파비콘은 기존에 이미
     `frontend/public/`에 정상 존재함을 재확인(추가 작업 불필요).
- **검증 관련 특이사항(방법론 기록)**: 이번 세션에서 `npm run build` + 백엔드 재시작 후
  Chrome MCP 로 라이브 검증을 시도했으나, 같은 `localhost:8088` 요청인데도 Bash/PowerShell
  도구(`curl`, `netstat`)가 보는 서버 응답(최신 빌드 해시)과 실제 Chrome 브라우저가 받는
  응답(하루 전 시각의 `Last-Modified`, 존재하지 않는 에셋 해시)이 서로 달랐다 —
  `performance.getEntriesByType('navigation')` 의 `transferSize`(>0, 캐시 아님)와
  `document.lastModified` 로 대조해 확인. 즉 이 자동화 환경에서 셸 도구와 브라우저 도구가
  같은 머신의 같은 포트를 가리키지 않는(또는 브라우저 쪽이 별도 프로세스를 보는) 네트워크
  불일치가 있어, **이 세션에서는 Chrome 을 통한 라이브 화면 검증이 신뢰할 수 없다고 판단**하고
  코드 diff 재검토 + 빌드 성공 확인으로 갈음했다. 조민석 님 본인 브라우저에서 새로고침(또는
  개발 서버 재시작 후)으로 최종 확인을 권장한다.
