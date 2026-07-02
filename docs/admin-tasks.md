# 작업지시서 — 관리자 기능(Role + 회원 등급/플랜 관리) (Claude Code 용)

> 저장소 위치 권장: `docs/admin-tasks.md`
> 작업 전 필독: 루트 `CLAUDE.md`. 기존 규약 계승 — 세션 인증(`CurrentUser`), 소유권/권한 서버 강제,
> `ApiExceptionHandler` 400/401/403 JSON, Hibernate `validate` → 스키마는 **Flyway 다음 번호**로만,
> 구독 도메인(`subscription/`, `PlanType`, `SubscriptionService`)·빌링(`billing/`) 재사용, 프론트 수정 후 `npm run build` 산출물 커밋.

## 배경 — 목적

**베타테스터에게 유료(PRO) 플랜을 개월 단위로 무상 지급**하기 위한 최소 관리자 기능. 지금은 지급하려면 DB를 직접 손대야 하는데, 이건 위험하고 확장 불가. 유저 목록 조회 + PRO N개월 지급 + role 변경 + 감사 로그, 딱 여기까지 만든다. **CMS를 만들지 않는다.**

## ⚠️ 절대 규칙 (보안 — 최우선)

1. **권한은 서버에서 강제.** 모든 `/api/admin/**` 엔드포인트는 세션 주체의 `role == ADMIN` 을 서버에서 검사한다. UI 숨김은 보조일 뿐. 비관리자 접근은 **403**(존재/이유 노출 없이 차단, 기존 열거 차단 스타일).
2. **첫 관리자 부트스트랩은 환경변수로.** 관리자가 없으면 아무도 승격 못 하므로, `app.admin-emails`(env/설정)에 있는 이메일은 로그인/기동 시 ADMIN 으로 승격. 코드/마이그레이션에 이메일 하드코딩 금지, 시크릿처럼 다룬다.
3. **마지막 관리자 잠금 방지.** 자기 자신의 ADMIN 해제 불가, 시스템에 ADMIN 이 0명이 되는 role 변경 불가.
4. **무상 지급은 결제와 구분.** 지급분은 `source=COMP`(무상)로 저장. **결제(PAID)와 절대 섞지 않는다** — 나중에 매출 지표에 무상 계정이 섞이면 안 됨.
5. **감사 로그 필수.** 누가(admin) / 언제 / 누구에게 / 무엇을(지급 개월·plan, role 변경) 했는지 append-only 로 기록.
6. **민감정보 미노출.** 유저 목록 DTO 에 비밀번호 해시·토큰·결제키 등 절대 포함 금지.

## ⚠️ 빌링 스케줄러 상호작용 (이 기능의 숨은 함정 — 반드시 처리)

기존 `BillingScheduler`(매일 03:10)가 구독 만료·갱신을 처리한다. 무상 지급(COMP)은 **결제수단(빌링키)이 없으므로**:
- 스케줄러는 **COMP 구독에 대해 결제를 시도하면 안 된다**(빌링키 없음 → 오류/예외 방지). `source=COMP` 는 결제 대상에서 제외.
- 단, **만료 처리는 동일하게** — COMP 구독도 만료일이 지나면 FREE 로 강등(결제만 스킵, 만료 강등은 유지).
- 이 분기를 명시적으로 구현하고, 단위테스트로 "COMP 구독은 결제 시도 없음 + 만료 시 강등"을 증명한다.

## 데이터 모델 (Flyway 다음 번호 — 기존 최고 버전 확인 후)

기존 구독 엔티티에 만료일/상태가 이미 있으면 재사용하고, 없는 것만 additive 로 추가한다.

- `users.role` VARCHAR NOT NULL DEFAULT 'USER'  (USER | ADMIN)
- 구독에 `source` VARCHAR NOT NULL DEFAULT 'PAID'  (PAID | COMP)  ← 없으면 추가
- `admin_audit` (append-only):
  ```
  id BIGSERIAL PK
  admin_user_id BIGINT FK -> users
  action VARCHAR NOT NULL        -- GRANT_PLAN | CHANGE_ROLE | REVOKE_PLAN
  target_user_id BIGINT FK -> users
  detail JSONB                    -- {months, plan, before, after} 등
  created_at TIMESTAMPTZ (DB 소유)
  ```

## 태스크

### T10.1 — Role + 부트스트랩 + 권한 가드
- `Role` enum(USER/ADMIN), `users.role` 마이그레이션(기본 USER).
- `app.admin-emails` 설정 기반 부트스트랩: 해당 이메일 로그인/기동 시 ADMIN 승격.
- **관리자 권한 가드**(인터셉터 또는 공통 체크)로 `/api/admin/**` 전체를 ADMIN 로 제한. 비관리자 403.
- **AC:** 비관리자가 `/api/admin/*` 호출 시 403. `app.admin-emails` 의 계정은 ADMIN 으로 인식. Hibernate validate 통과.

### T10.2 — 유저 목록 API
- `GET /api/admin/users` — 이메일·가입일·role·현재 plan·구독 만료일·source. 이메일 부분검색(선택), 페이지네이션(선택, 간단히).
- 민감필드 제외한 DTO 만 반환.
- **AC:** ADMIN 만 200, 목록에 plan/만료/role/source 표시, 비밀번호 해시 등 미포함.

### T10.3 — PRO N개월 지급 (핵심)
- `POST /api/admin/users/{id}/grant` body `{ months:int(1..N), plan:"PRO" }`.
- **SubscriptionService 를 통해** 처리(도메인 우회 금지): plan=PRO, 만료일 = **max(now, 기존 만료일) + N개월**(이미 있으면 연장), `source=COMP`.
- 감사 로그 기록. 잘못된 입력(months<=0 등) 400.
- **AC:** 지급 후 대상 유저 plan=PRO·만료일 정확·source=COMP. 기존 만료일 있으면 연장됨. 감사 로그 1건 생성. 이 유저는 PRO 기능(예: 광고ROI/무제한 기간) 접근 가능.

### T10.4 — Role 변경 + (선택)회수
- `POST /api/admin/users/{id}/role` body `{ role }`. **마지막 ADMIN 잠금 방지·자기 강등 방지**(규칙 3).
- (선택) `POST /api/admin/users/{id}/revoke` — plan 을 FREE 로 강등(COMP 회수). 감사 기록.
- **AC:** 마지막 ADMIN 을 USER 로 못 바꿈(400/409). 자기 자신 ADMIN 해제 불가. 모든 변경 감사 기록.

### T10.5 — 관리자 화면(React)
- `/admin` 페이지: 유저 표(이메일·plan·만료·role·source) + **"PRO N개월 지급"**(개월 수 입력) + role 토글 + (선택)회수 + 감사 로그 뷰.
- **ADMIN 에게만 Nav 링크 노출**(서버 가드가 진짜 방어, UI 는 편의). 비관리자가 `/admin` 진입 시 리다이렉트/차단.
- `SpaForwardingController` 에 `/admin` forward 추가. 기존 UI 패턴 재사용, 반응형·키보드 접근성.
- **AC:** ADMIN 로그인 시 /admin 접근·목록 표시·지급/역할변경 동작. 비관리자는 UI 링크 없음 + 직접 진입 차단. `npm run build` 산출물 커밋.

### T10.6 — 빌링 스케줄러 분기 (규칙 참조)
- `BillingScheduler`/`BillingService` 가 `source=COMP` 구독에 결제 시도하지 않도록 분기. 만료 강등은 유지.
- **AC:** 단위테스트 — COMP 구독은 결제 호출 0회, 만료일 경과 시 FREE 강등. PAID 구독 회귀 없음.

## Definition of Done (공통)

- [ ] AC 충족  [ ] `./gradlew build` 통과(테스트 포함)  [ ] Flyway 적용 + validate 통과
- [ ] 모든 `/api/admin/**` 서버 role 강제(403)  [ ] 마지막 ADMIN 잠금 방지  [ ] 감사 로그 남음
- [ ] COMP/PAID 분리 + 스케줄러 분기 테스트 통과  [ ] 민감정보 미노출  [ ] 시크릿/이메일 미커밋
- [ ] Conventional Commits  [ ] 프론트 변경 시 산출물 커밋  [ ] 결정사항 `docs/DECISIONS.md` 기록

## 권장 순서

T10.1(role+가드) → T10.6(스케줄러 분기, 먼저 안전장치) → T10.2(목록) → T10.3(지급) → T10.4(role/회수) → T10.5(화면).
각 태스크 완료 시 AC 체크리스트로 보고하고 커밋.
