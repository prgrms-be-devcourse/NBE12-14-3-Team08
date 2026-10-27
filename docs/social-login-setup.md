# 구글 소셜 로그인 설정 가이드

로그인 화면의 **"Google로 계속하기"** 를 로컬에서 쓰려면 구글 클라이언트 ID/시크릿을 설정해야 합니다.
**설정하지 않아도** 아이디/비밀번호 로그인과 나머지 기능은 그대로 동작합니다. (구글 버튼만 보이지 않습니다)

## 한눈에 보기

| 위치 | 이름 | 값 | 공개 여부 |
|---|---|---|---|
| 프론트엔드 | `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | 구글 클라이언트 ID | 공개되어도 되는 값 |
| 백엔드 | `GOOGLE_CLIENT_ID` | 위와 **같은** 클라이언트 ID | 공개되어도 되는 값 |
| 백엔드 | `GOOGLE_CLIENT_SECRET` | 구글 클라이언트 시크릿 | **절대 커밋·공유 금지** |

- 세 값은 팀에서 **비공개 경로(DM 등)로 전달**받습니다. 저장소, 이슈, PR, 채널 공개 글에는 올리지 않습니다.
- 클라이언트 ID는 팀이 같은 값을 씁니다. 각자 만들 필요가 없습니다.

---

## 1. 팀원: 받은 키 설정하기

### 1-1. 프론트엔드 (`frontend/.env.local`)

`frontend/.env.example`을 복사해 `frontend/.env.local`을 만들고 클라이언트 ID를 채웁니다.

```bash
cd frontend
cp .env.example .env.local
```

```text
NEXT_PUBLIC_GOOGLE_CLIENT_ID=받은-클라이언트-ID
```

- `.env.local`은 `.gitignore`에 있어 커밋되지 않습니다.
- **개발 서버를 시작할 때 읽히므로**, 값을 바꾸면 `npm run dev`를 다시 시작해야 합니다.
- 따옴표나 앞뒤 공백 없이 값만 적습니다.

### 1-2. 백엔드 (환경변수 2개)

Spring Boot는 `.env` 파일을 자동으로 읽지 않습니다. 서버를 실행하는 방식에 맞게 **환경변수**로 넘깁니다.

**IntelliJ로 실행하는 경우 (권장)**

1. 오른쪽 위 실행 구성 드롭다운 → **Edit Configurations...**
2. Spring Boot 실행 구성(`BackendApplication`)을 선택합니다.
3. **Environment variables** 칸이 보이지 않으면 **Modify options**에서 **Environment variables**를 체크합니다.
4. 칸 오른쪽 아이콘을 눌러 아래 두 줄을 추가합니다. (한 줄로 쓸 때는 세미콜론으로 구분)
   ```text
   GOOGLE_CLIENT_ID=받은-클라이언트-ID;GOOGLE_CLIENT_SECRET=받은-클라이언트-시크릿
   ```
5. **Apply** 후 서버를 **다시 시작**합니다.

> 실행 구성 창의 **Store as project file**이 켜져 있으면 설정이 `.run/` 파일로 저장되어 **시크릿이 커밋될 수 있습니다.** 꺼져 있는지 확인하세요.

**터미널에서 `bootRun`으로 실행하는 경우**

PowerShell:

```powershell
$env:GOOGLE_CLIENT_ID="받은-클라이언트-ID"
$env:GOOGLE_CLIENT_SECRET="받은-클라이언트-시크릿"
./gradlew bootRun
```

bash(Git Bash, macOS, Linux):

```bash
GOOGLE_CLIENT_ID="받은-클라이언트-ID" GOOGLE_CLIENT_SECRET="받은-클라이언트-시크릿" ./gradlew bootRun
```

### 1-3. 확인하기

1. DB, 백엔드, 프론트를 실행합니다. (`docker compose up -d` → 백엔드 → `npm run dev`)
2. `http://localhost:3000/login`에 **"Google로 계속하기"** 가 보이는지 확인합니다.
3. 버튼을 눌러 구글 계정을 선택하면 로그인되어 `/main`으로 이동합니다.
4. 헤더 오른쪽과 마이페이지에 **"Google 계정"** 이 보입니다.

DB에서 확인하려면 (로컬 DB 컨테이너 기준):

```sql
select id, username, nickname, provider, status from member where provider <> 'LOCAL';
```

- 처음 로그인하면 `provider = GOOGLE`인 회원이 한 명 생깁니다.
- 로그아웃 후 같은 구글 계정으로 다시 로그인해도 **새 회원이 생기지 않고 같은 회원**으로 들어갑니다.

---

## 2. 키 발급자: 구글 콘솔에서 키 만들기 (팀에서 한 번만)

1. [Google Cloud Console](https://console.cloud.google.com/)에서 **서비스 전용 새 프로젝트**를 만듭니다. (다른 서비스와 앱 이름·동의 화면이 섞이지 않도록 프로젝트를 나누는 것을 권장합니다. 결제 계정은 연결하지 않습니다.)
2. **OAuth 동의 화면**(콘솔 개편 후 "Google Auth Platform"의 브랜딩·대상 메뉴)을 설정합니다.
   - 앱 이름: `내기? 내기!`
   - 사용자 유형(대상): **외부**
   - 범위: `openid`, `profile`만 사용합니다. (이메일은 받지 않으며, 민감한 범위가 아니라 별도 검증이 필요 없습니다.)
3. **사용자 인증 정보 → OAuth 클라이언트 ID 만들기**
   - 애플리케이션 유형: **웹 애플리케이션**
   - **승인된 리디렉션 URI**에 아래를 등록합니다. (글자 하나라도 다르면 `redirect_uri_mismatch`가 납니다)
     - 로컬: `http://localhost:3000/auth/callback/google`
     - 배포: `https://<프론트 서비스 도메인>/auth/callback/google`
4. 생성되면 나오는 **클라이언트 ID**와 **클라이언트 보안 비밀번호**를 팀에 비공개로 전달합니다.
5. 팀원을 프로젝트의 **소유자 또는 편집자**로 추가해 두면 나중에 키를 다시 확인·교체하기 쉽습니다.

> Vercel 프리뷰 주소(`xxx-git-브랜치.vercel.app`)는 매번 바뀌어 리디렉션 URI를 미리 등록할 수 없습니다. 소셜 로그인은 **등록한 주소에서만** 동작합니다.

---

## 3. 배포 환경

- **프론트(Vercel)**: 프로젝트 환경변수에 `NEXT_PUBLIC_GOOGLE_CLIENT_ID`를 추가하고 **재배포**합니다. `NEXT_PUBLIC_` 값은 빌드 때 코드에 들어가서, 추가만 하고 재배포하지 않으면 반영되지 않습니다.
- **백엔드**: 배포 환경에 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`을 추가하고 다시 시작합니다.
- 운영 도메인의 `https://<도메인>/auth/callback/google`이 콘솔에 등록돼 있어야 합니다.
- 서버가 시작할 때 `member` 테이블에 `provider`(기본값 `LOCAL`), `provider_id` 컬럼이 자동으로 추가됩니다. 첫 배포 직후 컬럼이 생겼는지 확인하세요.

---

## 4. 문제 해결

| 증상 | 원인 / 해결 |
|---|---|
| 로그인 화면에 구글 버튼이 없음 | 프론트의 `NEXT_PUBLIC_GOOGLE_CLIENT_ID`가 비어 있거나 `.env.local` 수정 후 개발 서버를 다시 시작하지 않음 |
| 구글 화면에서 `redirect_uri_mismatch` | 콘솔에 등록한 리디렉션 URI와 접속 주소(`http`/`https`, 포트, 끝의 `/`)가 다름 |
| "액세스 차단됨, 앱이 확인되지 않음" | 앱이 "테스트" 상태일 때 나올 수 있음. 콘솔 대상 메뉴의 **테스트 사용자**에 해당 계정 추가 |
| 로그인 후 "구글 로그인이 설정되지 않았습니다" | 백엔드에 `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`이 없거나 다른 실행 구성으로 서버를 실행함 |
| 로그인 후 "구글 로그인에 실패했습니다" | 백엔드의 클라이언트 ID/시크릿이 틀림(공백·따옴표 포함 여부 확인), 또는 인가 코드가 이미 쓰였거나 만료됨 |
| "로그인 요청이 만료되었거나 올바르지 않습니다" | 콜백 주소를 새로고침했거나 다른 탭에서 열었음. 로그인 화면에서 다시 시작 |
| "구글 서버와 통신하지 못했습니다" | 네트워크 문제 또는 구글 서버 장애. 잠시 후 다시 시도 |

## 5. 보안 주의

- **클라이언트 시크릿은 커밋하지 않습니다.** 실수로 올렸다면 구글 콘솔에서 시크릿을 **새로 발급(교체)** 하고 팀에 다시 전달합니다. 저장소 기록에서 지워도 이미 노출된 값은 안전하지 않습니다.
- 프론트에는 클라이언트 **ID만** 둡니다. 시크릿은 서버만 사용하며 프론트 코드나 `NEXT_PUBLIC_` 변수에 넣지 않습니다.
- 소셜 회원은 이메일을 저장하지 않으며, 같은 이메일이어도 아이디/비밀번호 회원과 합쳐지지 않습니다. (공급자 + 공급자 식별자로만 구분)
