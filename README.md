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
    <td align="center"><img src="https://github.com/GibGui.png" width="100" height="100" /></td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/swshindev-beep">신시원</a></td>
    <td align="center"><a href="https://github.com/imlmhn">이문환</a></td>
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
| <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/kotlin/kotlin-original.svg" width="50" height="50"/><br>Kotlin | <img src="https://raw.githubusercontent.com/devicons/devicon/master/icons/spring/spring-original.svg" width="50" height="50"/><br>Spring Boot |
|:----------------------------------------------------------------------------------------------------------------------------------------:| :---: |
* **Language:** Kotlin 2.3.21
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
* [프로젝트 API 문서 보기](docs/api-spec.md)
<details>
<summary>Swagger 로컬 주소: http://localhost:8080/swagger-ui/index.html</summary>

<details>
  <summary>회원 API</summary>

![회원](https://github.com/user-attachments/assets/432fedda-1759-40ff-93c7-a27fcd5056eb)
</details>
<details>
<summary>그룹/그룹멤버 API</summary>

![그룹/그룹멤버](https://github.com/user-attachments/assets/5b663f6a-6003-4526-b2e5-5710991fd4fe)
</details>
<details>
<summary>습관 API</summary>

![습관](https://github.com/user-attachments/assets/bdf87673-f590-4086-b8cb-250932534090)
</details>

<details>
<summary>습관 인증 API</summary>

![습관인증](https://github.com/user-attachments/assets/01e4c950-7221-4cba-8c38-49340c56256a)
</details>

<details>
<summary>벌칙 인증 API</summary>

![벌칙인증](https://github.com/user-attachments/assets/fb4cc8ae-68b4-46e9-8670-e67f3bc5dc87)
</details>

</details>

### 🏠 방(그룹) 생성 및 관리

* **비밀방 개설:** 데드라인과 우리만의 벌칙을 설정하여 그룹의 기준이 되는 방을 생성합니다.
* **초대 및 참가:** 방장이 생성한 초대 링크와 비밀번호를 통해 허가된 멤버만 방에 입장할 수 있습니다. 비관적 락(`PESSIMISTIC_WRITE`) 기반 동시성 제어를 통해 다수가 동시에 입장을 시도해도 잔여 정원을 초과하지 않도록 보장합니다.

### 🎯 개인별 맞춤 습관 등록

* **독립적인 목표 설정:** 각 멤버는 본인이 실천하고자 하는 서로 다른 습관을 등록하고 개별적으로 관리할 수 있습니다.
* **맞춤형 실천 일수**: 같은 방 안에서도 멤버별로 서로 다른 실천 목표(주당 1~7일)를 자율적으로 등록하고 실천합니다.

### 📸 사진 기반 간편 인증 & 소통

* **대기/심사/재제출 라이프사이클**: 제출 건은 대기(`PENDING`) 상태가 되며, 방장이 승인/반려합니다. 반려된 건은 주간 마감 전까지 수정 재제출이 가능합니다.
* **멤버 간 현황 공유:** 그룹에서 다른 멤버들의 인증 사진을 확인하며, 서로의 작심삼일을 방지하고 꾸준한 실천을 독려할 수 있습니다.
* **방장 검토 유예 자동 승인**: 방장 미검토 건은 마감 후 24시간 경과 시 시스템이 자동 승인 처리합니다.
* **통합 인증 반응 및 댓글 시스템 (`EngagementRules`)**:
* 습관 인증과 벌칙 인증의 반응·댓글 기능을 하나의 도메인 규칙으로 통합하여 관리합니다. (세부 아키텍처: [docs/adr/0002-verify-reaction-comment-tables.md](docs/adr/0002-verify-reaction-comment-tables.md) 참고)
  * **상호작용 조건**: 활동 중인 멤버(`ACTIVE`)만 진행 중인 방의 인증글에 하트나 댓글을 남길 수 있으며, 본인 인증글에는 하트를 누를 수 없습니다.
  * **철저한 익명성 보장**: 방을 나간 멤버(`LEFT`)가 작성한 댓글은 닉네임이 자동으로 '탈퇴한 멤버'로 전환되고 회원 ID 노출이 완전히 차단됩니다.

### ⚖️ 백그라운드 자동 정산 & 벌칙 시스템
* **스케줄러 기반 판정 (매일 00:05)**: 주간 실천 횟수가 목표치에 미달하거나 중도 포기한 멤버에게 습관 실패(`FAILED`) 처리 및 벌칙(`REQUIRED`)을 부과합니다.
* **벌칙 증빙**: 벌칙 수행 사진을 업로드하여 방장의 최종 승인을 받습니다. 거절된 인증은 재제출 할 수 있습니다.
* **그룹 만료 처리 (매일 00:00:00)**: 마감일이 지난 방은 자동으로 `FINISH` 상태로 전이됩니다.

## 🏗 서비스 구상도
![서비스구상도](https://github.com/user-attachments/assets/ceba2e73-3600-4e6e-898c-47cab73a4b3c)


## 🗄 데이터베이스 구조 (ERD)
![ERD](https://github.com/user-attachments/assets/c83bf7b0-27a4-4012-9b93-aeb8a9135e0d)

## 📂 프로젝트 구조 (Project Structure)
```text
.
├── .github/                   # GitHub 액션(CI/CD), 이슈/PR 템플릿 등
│   └── workflows/
├── backend/                   # 백엔드 코틀린 프로젝트
│   ├── src/
│   │   └── main/
│   │       └── kotlin/
│   │           └── com/back/
│   │               ├── BackendApplication.kt
│   │               ├── domain/
│   │               │   ├── group/
│   │               │   ├── groupMember/
│   │               │   ├── habit/
│   │               │   ├── habitVerify/
│   │               │   ├── member/
│   │               │   ├── penaltyverify/
│   │               │   ├── settlement/
│   │               │   └── verifyEngagement/
│   │               └── global/
│   │                   ├── config/
│   │                   ├── entity/
│   │                   └── ... (기타 글로벌 패키지)
│   ├── build.gradle.kts       # 빌드 스크립트
│   └── README.md              # 백엔드 실행 방법 문서
│
├── frontend/                  # 프론트엔드 app 디렉터리 구조
│   ├── src/
│   │   └── app/
│   │       ├── admin/
│   │       ├── auth/
│   │       ├── components/
│   │       ├── group/
│   │       └── ... (기타 앱 페이지/컴포넌트)
│   ├── package.json           # 의존성 관리 파일
│   └── README.md              # 프론트엔드 실행 방법 문서
│
├── .gitignore                 # 깃 추적 제외 설정 파일 (빌드 파일, 환경변수 등)
└── README.md                  # 프로젝트 전체 소개 및 아키텍처 문서
```

### 🚀 시작하기 (Quick Start)

본 프로젝트는 Docker 기반의 인프라 격리를 통해 로컬 환경 구축 비용을 최소화했습니다.

```bash
# 1. 인프라 컨테이너(PostgreSQL) 및 백엔드 서버 실행
docker compose up -d
cd backend && ./gradlew bootRun

# 2. 프론트엔드 의존성 설치 및 개발 서버 실행
cd ../frontend && npm install && npm run dev

# 3. 로컬 확인 주소
# - Frontend: http://localhost:3000
# - Backend : http://localhost:8080
```

### 📚 상세 개발 및 검증 가이드 (Developer Guides)
* [백엔드 환경 구축, Testcontainers 통합 테스트 및 k6 부하 테스트 명세](docs/dev-guide/backend.md)
* [프론트엔드 환경 구축 및 Google OAuth2 소셜 로그인 가이드](docs/dev-guide/frontend.md)
