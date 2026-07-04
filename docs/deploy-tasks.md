# 작업지시서 — 배포/호스팅 (Claude Code 용)

> 저장소 위치 권장: `docs/deploy-tasks.md`
> 제약: 서버 비용 **월 ₩0~1만원**, 서버 운영 초심자(가이드 따라가는 수준). `docs/HANDOFF.md` 의
> 자기 검수 원칙 적용 — 각 단계 완료 시 실제로 접속/재현해보고 보고한다.

## 배경 — 왜 이 조합인가

- **비용**: 소형 VPS **월 ₩5,600~8,500 수준**(국내 iwinv 등 또는 해외 Vultr/DigitalOcean) +
  Caddy(자동 HTTPS, nginx+certbot 조합보다 설정이 훨씬 단순). **도메인 구매는 베타 단계에선
  하지 않음**(T12.2 참고, `sslip.io` 무료 호스트네임으로 대체) — 서버 비용만으로 월 1만원
  예산 안에 들어온다.
- **⚠️ Oracle Cloud Always Free 를 1순위에서 제외한 이유**: Ampere(ARM) 무료 인스턴스는
  인기 리전(서울/춘천 등)에서 신청 경쟁으로 인해 수 주간 생성이 안 되는 경우가 흔하다
  (실제로 2026-07-04 시점, 2주간 시도 실패 확인됨). 시간 낭비를 피하기 위해
  **즉시 생성되는 저가 유료 VPS 를 1순위**로 한다.
- **제공자는 사람이 아무 쪽이나 선택 가능** — 국내(예: iwinv 클라우드 서버) 또는 해외
  (Vultr/DigitalOcean) 모두 Ubuntu + root SSH 를 제공하면 이후 단계(T12.3 이후)는 동일하게
  적용된다. 국내 제공자 선택 시 원화 결제·한국어 약관이라는 실질적 이점이 있다.
  **단, 신청 전 아래 두 가지를 반드시 확인**:
  1. **"공유형 웹호스팅"이 아니라 "클라우드 서버(VPS)" 상품인지.** 공유형은 root 권한이
     없어 Docker/Java/PostgreSQL 을 직접 못 돌린다. Ubuntu 이미지 선택 + root SSH 접속이
     되는 상품이어야 한다.
  2. **RAM 최소 1GB, 가능하면 2GB.** 이 서버에서 PostgreSQL + Spring Boot(JVM) + Caddy
     세 프로세스가 동시에 돈다. 512MB 급 최저가 플랜은 부족할 수 있다.
- **왜 무료 PaaS(Render 등) 대신 상시 VM 인가**: seller-profit 은 정산/주문/반품 **자동 수집
  스케줄러**(`OrderSyncScheduler` 등)와 `BillingScheduler`(매일 03:10)가 핵심 기능이다.
  무료 PaaS 는 트래픽 없으면 서버가 잠들어(sleep) 스케줄러가 멈춘다 — 이 제품엔 치명적.
  상시 가동되는 VM 이 필수라 이 조합을 택한다.
- **역할 구분**: 계정 생성·결제·도메인 구매는 **사람(조민석 님)이 직접** 한다(Claude Code 는
  이런 결제·계정 생성 액션을 대신 수행하지 않음). Claude Code 는 설정 파일 작성 + 서버에서
  실행할 명령 안내 + 검증을 맡는다. 아래에 [사람] / [Claude Code] 로 구분 표시.

## ⚠️ 절대 규칙

- **시드(seed) 프로파일을 운영에 올리지 않는다.** `--spring.profiles.active=seed` 는 로컬
  전용. 운영은 기본 프로파일(또는 `prod`)로, 데모 계정·시드 데이터 없이 뜬다.
- **시크릿은 서버의 환경변수로만.** `APP_ENCRYPTION_KEY`, `DB_PASSWORD`, `app.admin-emails`
  등을 이미지/Git에 넣지 않는다.
- **관리자 부트스트랩 이메일을 실제 값으로.** `app.admin-emails` 에 조민석 님 실제
  이메일을 운영 환경변수로 설정 — 이게 없으면 운영에서 관리자 승격이 안 된다.
- **HTTPS 강제.** 평문 HTTP 로 로그인 정보가 오가지 않게 Caddy 가 443 만 열고 80 은
  443 으로 리다이렉트하게 한다.

## T12.1 — [사람] VPS 생성 (국내 iwinv 클라우드 서버 또는 Vultr/DigitalOcean)

1. 제공자 가입 — 국내: iwinv(https://www.iwinv.kr) **"클라우드 서버"** 카테고리(공유형
   웹호스팅 아님). 해외: Vultr(https://www.vultr.com) 또는 DigitalOcean
   (https://www.digitalocean.com).
2. 인스턴스 생성: **RAM 1~2GB 급** 최소 플랜, 리전은 **서울**(국내 제공자는 기본 국내,
   해외 제공자는 Seoul 리전 명시 선택). OS 이미지는 **Ubuntu 22.04/24.04 LTS**.
3. 방화벽 설정: iwinv **KR1-ZONE**은 콘솔(ELCAP)에서 방화벽 규칙 설정 가능. **KR1-Lite
   존은 ELCAP 미지원**이므로 이 경우 서버 접속 후 **OS 레벨 방화벽(ufw)으로 직접 설정**
   (아래 7번 스왑 설정 직후 실행 권장):
   ```bash
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable   # 활성화 확인 메시지에 y 입력
   sudo ufw status    # 22, 80, 443 이 ALLOW 로 보이면 성공
   ```
   (콘솔 방화벽 지원 존이라도 ufw 를 추가로 켜두면 이중 방어가 되어 더 안전하다.)
4. SSH 접속: 콘솔에서 root 비밀번호 확인 또는 키페어 등록(키페어 권장). PowerShell 에서
   `ssh root@{서버IP}`(또는 `-i {키파일}`)로 접속 확인.
5. 서버의 **공인 IP** 를 확보해 다음 단계(도메인 연결)에 사용.
6. (참고) 오라클과 달리 신청 즉시 서버가 생성되므로 대기 없음 — 바로 다음 단계 진행.
7. **[중요] 스왑(swap) 설정 — RAM 1GB 플랜 선택 시 필수.** 예산 때문에 2GB 대신 1GB 플랜을
   선택한 경우, PostgreSQL+JVM+Caddy 세 프로세스 동시 구동 시 메모리 부족으로 프로세스가
   강제 종료(OOM Kill)될 수 있다. 서버 접속 직후 2GB 스왑 파일을 만든다(디스크를 가상
   메모리처럼 사용, 비용 없음):
   ```bash
   sudo fallocate -l 2G /swapfile
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   free -h   # Swap 행에 2.0Gi 가 보이면 성공
   ```

## T12.2 — [사람] 도메인 구매 + DNS 연결 — `sellerprofit.co.kr` 확정

베타테스터에게 정식으로 소개할 목적이라 신뢰도가 중요 → `sslip.io` 같은 IP 기반 임시
주소 대신 **실제 도메인을 구매**하기로 결정(2026-07-04, `docs/DECISIONS.md` 참고).

1. **도메인**: **`sellerprofit.co.kr`**(`.com`은 선점됨). 가비아·후이즈 등에서 구매,
   연 약 ₩17,000.
   - 신청 시 "개인/법인" 선택란에서 **"개인"** 선택. **사업자등록증·등록번호 불필요** —
     KRNIC 규정상 개인 신청자는 본인인증(휴대폰 인증 등)으로 대체되며 사업자등록 없이
     `.co.kr` 등록이 가능하다(사업자등록이 필요한 건 쿠팡 Open API·토스 실 결제뿐,
     도메인과는 무관).
   - `.co.kr`이 오히려 자연스럽다 — 타깃이 국내 쿠팡 셀러라 국내향 서비스라는 인상에
     부합하고, 글로벌 진출 계획이 없어 `.com` 대체제로 부족함 없음.
2. 구매처 DNS 설정에서 **A 레코드**를 T12.1 의 서버 공인 IP(`49.247.139.234`)로 연결
   (`@` 와 `www` 둘 다, 또는 사용할 서브도메인 하나만).
3. 전파 확인(수 분~수 시간): `nslookup sellerprofit.co.kr` 이 서버 IP 를 가리키면 완료.
4. 이후 T12.3 의 Caddyfile 에서 `sellerprofit.co.kr` 을 호스트네임으로 사용한다.

## T12.3 — [Claude Code] 운영용 Docker Compose 작성

- 저장소에 `docker-compose.prod.yml` 신설(로컬 `docker-compose.yml` 과 별도, 참고는 하되
  운영 전용으로 분리). 서비스 3개:
  1. `postgres` — 볼륨 마운트로 데이터 영속화(`./pgdata:/var/lib/postgresql/data`), 환경변수로
     계정/비번 주입(`.env.production` 파일, **git에는 `.env.production.example` 만 커밋**).
  2. `app` — `./gradlew bootJar` 산출물(프론트 빌드 포함)로 만든 이미지 또는 서버에서
     직접 빌드. 환경변수: `DB_USERNAME`,`DB_PASSWORD`,`APP_ENCRYPTION_KEY`,`app.admin-emails`,
     `SPRING_PROFILES_ACTIVE`(비워두거나 `prod`, **`seed` 금지**). `restart: unless-stopped`.
  3. `caddy` — 리버스 프록시. `Caddyfile` 하나로 자동 HTTPS:
     ```
     sellerprofit.co.kr {
       reverse_proxy app:8080
     }
     ```
     Caddy 가 Let's Encrypt 인증서 발급/갱신을 자동 처리(certbot 수동 설정 불필요).
- `.env.production.example` 신설(실제 값 없이 키 이름만, 커밋 대상).
- **AC:** 로컬에서 `docker compose -f docker-compose.prod.yml config` 로 문법 검증 통과.
  시크릿이 파일에 하드코딩되지 않음(`.env.production` 은 `.gitignore` 등록).

## T12.4 — [Claude Code + 사람] 서버 배포

- [Claude Code] 배포 스크립트(`deploy.sh` 또는 명령 목록)를 작성: 서버에서
  `git pull` → `./gradlew bootJar`(또는 이미지 빌드) → `docker compose -f docker-compose.prod.yml up -d --build`.
- [사람] SSH 로 서버 접속 → 저장소 clone(또는 rsync) → `.env.production` 을 서버에 직접
  생성(실제 값 입력, **git에 올리지 않음**) → 배포 스크립트 실행.
- **AC:** `https://sellerprofit.co.kr/` 접속 시 랜딩 페이지가 200 으로 뜨고 브라우저 자물쇠(HTTPS)
  정상. `docker compose ps` 로 3개 컨테이너 모두 `Up`.

## T12.5 — [Claude Code] 스모크 테스트 + 스케줄러 확인

- 회원가입 → 로그인 → 계정 연동 화면 → 로그아웃까지 실제 브라우저(또는 curl)로 재현.
- `app.admin-emails` 로 등록한 계정으로 로그인 시 `/admin` 접근 가능한지 확인.
- 로그에서 `OrderSyncScheduler`/`SettlementSyncScheduler`/`BillingScheduler` 가 정상
  주기로 로그를 남기는지 확인(서버가 잠들지 않고 계속 도는지가 이 배포의 핵심 검증 포인트).
- 시드 데이터가 운영 DB에 없는지 확인(`SELECT * FROM users` 에 demo 계정 없어야 함).
- **AC:** 위 전부 통과. 특히 "서버 재부팅 후에도 컨테이너 자동 기동"(`restart: unless-stopped`
  또는 systemd 등록) 확인 — 오라클 인스턴스가 간헐적으로 재시작될 수 있음.

## T12.6 — [Claude Code] 최소 백업

- Postgres 데이터를 잃으면 안 되므로, 매일 `pg_dump` 로 덤프 파일을 서버 로컬 디스크에
  남기는 cron 하나만 추가(외부 스토리지 업로드는 이번 범위 아님, 로컬 보관만).
- **AC:** cron 등록 확인, 수동 트리거로 덤프 파일 1개 생성 확인.

## AC (완료조건, 공통)

- [ ] `https://sellerprofit.co.kr` HTTPS 로 접속 가능, HTTP 는 자동 리다이렉트.
- [ ] 시크릿이 저장소/이미지에 없음(`.env.production` 만 서버 로컬에 존재).
- [ ] 시드/데모 데이터 없이 운영 DB 가 깨끗하게 시작.
- [ ] 관리자 부트스트랩 이메일로 `/admin` 접근 가능.
- [ ] 스케줄러가 지속 동작(서버가 잠들지 않음).
- [ ] 서버 재시작에도 컨테이너 자동 복구.
- [ ] 일 1회 DB 백업 동작.
- [ ] `CLAUDE.md` "다음 단계 A-1"을 완료로 갱신, 배포 URL·서버 정보(민감 정보 제외)를
      `docs/DECISIONS.md` 에 기록.

## 참고 — 왜 Oracle Always Free 를 배제했는지 (기록)

Oracle Cloud Always Free(무료 VM)를 1순위로 검토했으나, Ampere(ARM) 무료 인스턴스가
인기 리전(서울/춘천 등)에서 신청 경쟁으로 수 주간 생성 불가한 경우가 실제로 발생함
(2026-07-04 시점 2주간 시도 실패 확인). Vultr/DigitalOcean 은 용량 대기 문제가 없어
즉시 생성되므로, 월 $6 대의 비용을 감수하고 이쪽을 1순위로 확정. 이 비용은 애초 예산
(월 1만원 이하)에 여전히 부합하므로 별도 승인 불필요. `docs/DECISIONS.md` 에 이 판단을
간단히 기록해둘 것(무료 대기가 아니라 소액 비용으로 시간을 산 결정).
