# 🤝 내기? 내기!

> 친구들과 함께 서로의 습관을 인증하고, 꾸준한 실천을 만들어가는 그룹형 습관 관리 서비스입니다.

## 📖 서비스 소개

* **목적:** 혼자서는 작심삼일로 끝나는 습관을 친구나 지인들과 함께 가벼운 내기를 걸고 꾸준히 실천하도록 돕습니다.
* **자율성 보장:** 하나의 그룹(방) 안에서도 멤버 각자가 원하는 서로 다른 목표(예: 헬스, 독서, 단어 암기 등)를 설정할 수 있습니다.
* **프라이빗 그룹:** 소수의 지인끼리 비밀 방을 만들어 금전적 부담 없는 소소한 벌칙(예: 인디언밥, 커피 사기)을 정하고 서로의 진행 상황을 관리합니다.
* **직관적인 인증:** 방 멤버 전용 게시판을 통해 서로의 인증 사진과 달성률을 실시간으로 확인하며 강력한 동기부여를 제공합니다.

## 👥 멤버
<table>
  <tr>
    <th colspan="5" align="center">개발 팀 · Full Stack (TEAM08)</th>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/swshindev-beep.png" width="100" height="100" /></td>
    <td align="center"><img src="https://github.com/imlmhn.png" width="100" height="100" /></td>
    <td align="center"><img src="https://github.com/nodo112907-png.png" width="100" height="100" /></td>
    <td align="center"><img src="https://github.com/hanjongyeon644-debug.png" width="100" height="100" /></td>
    <td align="center"><img src="https://github.com/GibGui.png" width="100" height="100" /></td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/swshindev-beep">신시원</a></td>
    <td align="center"><a href="https://github.com/imlmhn">이문환</a></td>
    <td align="center"><a href="https://github.com/nodo112907-png">임성준</a></td>
    <td align="center"><a href="https://github.com/hanjongyeon644-debug">한종연</a></td>
    <td align="center"><a href="https://github.com/GibGui">홍승연</a></td>
  </tr>
</table>

## ⚙ 기술 스택
### ✏️ 프론트엔드
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/typescript/typescript-original.svg" width="50" height="50"/><br>TypeScript | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/react/react-original.svg" width="50" height="50"/><br>React | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/nextjs/nextjs-original.svg" width="50" height="50"/><br>Next.js | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/tailwindcss/tailwindcss-original.svg" width="50" height="50"/><br>Tailwind CSS |
| :---: | :---: | :---: | :---: |
* **Language:** TypeScript
* **Framework:** Next.js 16.x, React 19.x
* **Styling:** Tailwind CSS 4.x

### 🛠 백엔드
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/java/java-original.svg" width="50" height="50"/><br>Java | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/spring/spring-original.svg" width="50" height="50"/><br>Spring Boot |
| :---: | :---: |
* **Language:** Java 25
* **Framework:** Spring Boot 4.1.1

### 📦 인프라 & 데이터베이스
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/postgresql/postgresql-original.svg" width="50" height="50"/><br>PostgreSQL | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/supabase/supabase-original.svg" width="50" height="50"/><br>Supabase | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/vercel/vercel-original.svg" width="50" height="50"/><br>Vercel |
| :---: | :---: | :---: |
* **Database:** PostgreSQL (배포: Supabase, 로컬·테스트: Docker)
* **Object / Storage:** Supabase
* **Deployment:** Vercel

### 🛠 협업 도구
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/git/git-original.svg" width="50" height="50"/><br>Git | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/github/github-original.svg" width="50" height="50"/><br>GitHub | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/swagger/swagger-original.svg" width="50" height="50"/><br>Swagger | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/notion/notion-original.svg" width="50" height="50"/><br>Notion |
| :---: | :---: | :---: | :---: |
* **VCS / Tools:** Git, GitHub, Notion
* **API Docs:** Swagger (SpringDoc OpenAPI)

## 📌 핵심 기능

* **API 명세:** [Notion API 명세서 바로가기](https://app.notion.com/p/API-Mock-Server-eac15a0120548348bdd881b2162b9217?source=copy_link)

### 🏠 방(그룹) 생성 및 관리

* **비밀방 개설:** 데드라인과 우리만의 벌칙을 설정하여 그룹의 기준이 되는 방을 생성합니다.
* **초대 및 참가:** 방장이 생성한 초대 링크와 비밀번호를 통해 허가된 멤버만 방에 입장할 수 있습니다. 비관적 락(`PESSIMISTIC_WRITE`) 기반 동시성 제어를 통해 다수가 동시에 입장을 시도해도 잔여 정원을 초과하지 않도록 보장합니다.

### 🎯 개인별 맞춤 습관 등록

* **독립적인 목표 설정:** 각 멤버는 본인이 실천하고자 하는 서로 다른 습관을 등록하고 개별적으로 관리할 수 있습니다.
* **맞춤형 실천 일수**: 같은 방 안에서도 멤버별로 서로 다른 실천 목표(주당 1~7일)를 자율적으로 등록하고 실천합니다.

### 📸 사진 기반 간편 인증

* **대기/심사/재제출 라이프사이클**: 제출 건은 대기(`PENDING`) 상태가 되며, 방장이 승인/반려합니다. 반려된 건은 주간 마감 전까지 수정 재제출이 가능합니다.
* **멤버 간 현황 공유:** 그룹에서 다른 멤버들의 인증 사진을 확인하며, 서로의 작심삼일을 방지하고 꾸준한 실천을 독려할 수 있습니다.
* **방장 검토 유예 자동 승인**: 방장 미검토 건은 마감 후 24시간 경과 시 시스템이 자동 승인 처리합니다.

### ⚖️ 백그라운드 자동 정산 & 벌칙 시스템
* **스케줄러 기반 판정 (매일 00:05)**: 주간 실천 횟수가 목표치에 미달하거나 중도 포기한 멤버에게 습관 실패(`FAILED`) 처리 및 벌칙(`REQUIRED`)을 부과합니다.
* **벌칙 증빙**: 벌칙 수행 사진을 업로드하여 방장의 최종 승인을 받습니다. 거절된 인증은 재제출 할 수 있습니다.
* **그룹 만료 처리 (매일 00:00:01)**: 마감일이 지난 방은 자동으로 `FINISH` 상태로 전이됩니다.

## 🏗 서비스 구상도
<img width="3817" height="8192" alt="8팀 자쿰" src="https://github.com/user-attachments/assets/0e9fbcf1-4c12-4fa2-8c86-047b5f9a443a" />


## 🗄 데이터베이스 구조 (ERD)
<img width="1805" height="582" alt="내기?내기! ERD-2" src="https://github.com/user-attachments/assets/1034ebca-a875-45e7-a909-0f57db633efa" />

## 🚀 시작하기 (Getting Started)

### Backend (Spring Boot)

**사전 준비: Docker** (로컬 DB와 테스트 DB가 모두 PostgreSQL 컨테이너입니다)

```bash
# 1) 로컬 PostgreSQL 실행 (프로젝트 루트에서)
docker compose up -d

# 2) 서버 실행
cd backend
./gradlew bootRun
```

- 기본 프로필은 `local`이며, `docker compose`로 띄운 PostgreSQL(`localhost:5432`, DB/계정 `naegi`)에 연결합니다.
- 로컬 프로필에서만 시연용 시드 데이터가 자동으로 들어갑니다.
- 사진 업로드(습관/벌칙 인증) 기능까지 테스트하려면 `SUPABASE_SERVICE_ROLE_KEY` 환경변수가 필요합니다.
- 서버는 기본적으로 `http://localhost:8080`에서 실행됩니다.
- DB를 초기화하려면 `docker compose down -v` 후 다시 `docker compose up -d` 하세요.

#### 테스트 실행

```bash
cd backend
./gradlew test
```

- 테스트는 **Docker가 실행 중이어야** 합니다. Testcontainers가 PostgreSQL 컨테이너를 자동으로 띄우므로 로컬 DB(`docker compose`)와는 별개이며, 개발 데이터를 건드리지 않습니다.
- Gradle로 실행하면 `test` 프로필이 자동 적용됩니다. IDE에서 Gradle이 아닌 JUnit 러너로 직접 실행한다면 VM 옵션 `-Dspring.profiles.active=test`를 추가하세요.

#### k6 부하 테스트

[k6](https://grafana.com/docs/k6/latest/set-up/install-k6/)를 설치한 뒤 백엔드를 실행하고, 프로젝트 루트에서 기본 부하 테스트를 실행합니다.

```bash
k6 run k6/smoke.js
```

- 테스트는 공개 연결 확인 API(`/api/connection-test`)를 호출합니다. 기본 대상은 `http://localhost:8080`입니다.
- 다른 환경을 대상으로 실행하려면 `BASE_URL` 환경변수를 지정합니다. 예: `BASE_URL=https://api.example.com k6 run k6/smoke.js`
- 부하는 30초간 최대 5 VU까지 증가하고 1분간 유지한 뒤 종료합니다. 요청 실패율 1% 미만, p95 응답 시간 500ms 미만을 기준으로 검사합니다.

#### Kotlin 혼용 개발 (Java → Kotlin 전환 중)

백엔드는 도메인별로 Java를 Kotlin으로 전환하는 중이라 **Java와 Kotlin이 함께 빌드**됩니다. (Kotlin 2.3.21, Spring Boot가 버전 관리)

| 항목 | 내용 |
|---|---|
| 소스 위치 | Kotlin은 `src/main/kotlin`, `src/test/kotlin` (패키지 경로는 Java와 동일) |
| 플러그인 | `jvm`, `spring`(Spring 클래스 open), `jpa`(엔티티 기본 생성자), `lombok` |
| JPA 엔티티 | `allOpen`으로 `@Entity`, `@MappedSuperclass`, `@Embeddable`이 상속 가능. **엔티티에 `data class`는 쓰지 않습니다.** |
| 테스트 | JUnit5 + AssertJ 그대로, Kotlin 단위 테스트는 MockK 사용 |

**전환할 때 주의할 점**

- **롬복**: Kotlin 컴파일러는 롬복이 만드는 getter/builder를 모릅니다. `kotlin-lombok` 플러그인이 이를 보완하므로 아직 Java인 롬복 클래스(`Group`, `GroupMember` 등)를 Kotlin에서 `group.title`처럼 쓸 수 있습니다. 롬복 클래스가 모두 사라지면 플러그인을 제거합니다. **새 Java 클래스에는 롬복을 추가하지 않습니다.**
- **Java 호출부 유지**: 정적 팩토리는 `@JvmStatic`을 붙이고 getter 이름을 유지해야 Java 코드를 수정하지 않습니다.
- **`record` → `data class`**: Java 접근자 `response.title()`가 `getTitle()`로 바뀌므로, DTO를 전환하는 PR에서 Java 테스트의 접근자도 함께 수정합니다.
- **null 처리**: Java 롬복 getter의 반환 타입은 null 여부를 알 수 없는 플랫폼 타입입니다. 저장 전 엔티티의 `id`처럼 null일 수 있는 값을 `assertThat(entity.id)`로 바로 넘기면 Kotlin이 기본형으로 언박싱하다 NPE가 납니다. `val id: Long? = entity.id`처럼 nullable 타입으로 받으세요.
### Frontend (Next.js)

```bash
cd frontend
npm install
npm run dev
```

실행 후 [http://localhost:3000](http://localhost:3000) 에서 확인할 수 있습니다.

#### 구글 소셜 로그인 설정 (선택)

구글 로그인 버튼은 클라이언트 ID가 설정돼 있을 때만 나타납니다. 설정하지 않아도 아이디/비밀번호 로그인은 그대로 동작합니다.

1. [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트를 만들고 **OAuth 동의 화면**(외부)을 설정합니다. 요청 범위는 `openid`, `profile`만 씁니다. (이메일은 받지 않습니다)
2. **사용자 인증 정보 → OAuth 클라이언트 ID(웹 애플리케이션)** 를 만들고 **승인된 리디렉션 URI**를 등록합니다.
   - 로컬: `http://localhost:3000/auth/callback/google`
   - 배포: `https://<서비스 도메인>/auth/callback/google` (Vercel 미리보기 주소는 매번 바뀌어 미리 등록할 수 없으므로, 소셜 로그인은 등록한 주소에서만 동작합니다)
3. 발급된 값을 환경변수로 설정합니다. **클라이언트 시크릿은 절대 커밋하지 마세요.**

| 위치 | 환경변수 | 설명 |
|---|---|---|
| 프론트엔드 (`frontend/.env.local`) | `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | 클라이언트 ID (공개 값) |
| 백엔드 | `GOOGLE_CLIENT_ID` | 위와 같은 클라이언트 ID |
| 백엔드 | `GOOGLE_CLIENT_SECRET` | 클라이언트 시크릿 (서버에서만 사용) |

- 백엔드에 값이 없으면 구글 로그인 요청만 502로 거절되고 나머지 기능은 영향이 없습니다.
- 소셜 회원은 공급자 + 공급자 식별자로만 구분하며, 이메일이 같아도 아이디/비밀번호 회원과 합쳐지지 않습니다.

## 🤝 우리가 협업하는 법

* 결정은 다 같이
* 비난 없는 의사소통
* 설명은 핵심만
* 공적인 회의는 존댓말 사용
