# API 명세서

## 목차

- [1. 공통 사항](#1-공통-사항)
    - [1.1 기본 정보](#11-기본-정보)
    - [1.2 인증 필터 규칙](#12-인증-필터-규칙)
    - [1.3 인증 예외 경로](#13-인증-예외-경로)
    - [1.4 공통 오류 응답](#14-공통-오류-응답)
- [2. 인증 및 회원 도메인](#2-인증-및-회원-도메인)
    - [2.1 회원 가입](#21-회원-가입)
    - [2.2 일반 로그인](#22-일반-로그인)
    - [2.3 소셜 로그인](#23-소셜-로그인)
    - [2.4 인증 토큰 재발급](#24-인증-토큰-재발급)
    - [2.5 내 정보 조회](#25-내-정보-조회)
    - [2.6 내 정보 수정](#26-내-정보-수정)
    - [2.7 회원 탈퇴](#27-회원-탈퇴)
    - [2.8 회원 관련 Enum](#28-회원-관련-enum)
- [3. 그룹 도메인](#3-그룹-도메인)
    - [3.1 그룹 생성](#31-그룹-생성)
    - [3.2 내 그룹 목록 조회](#32-내-그룹-목록-조회)
    - [3.3 그룹 상세 조회](#33-그룹-상세-조회)
    - [3.4 그룹 수정](#34-그룹-수정)
    - [3.5 그룹 삭제](#35-그룹-삭제)
    - [3.6 관련 Enum](#36-관련-enum)
- [4. 그룹 멤버 API](#4-그룹-멤버-api)
    - [4.1 공통 사항](#41-공통-사항)
    - [4.2 초대 코드로 그룹 조회](#42-초대-코드로-그룹-조회)
    - [4.3 초대 코드로 그룹 가입](#43-초대-코드로-그룹-가입)
    - [4.4 그룹 멤버 목록 조회](#44-그룹-멤버-목록-조회)
    - [4.5 그룹 멤버 상세 조회](#45-그룹-멤버-상세-조회)
    - [4.6 그룹 탈퇴](#46-그룹-탈퇴)
    - [4.7 그룹 멤버 추방](#47-그룹-멤버-추방)
    - [4.8 그룹 방장 위임](#48-그룹-방장-위임)
    - [4.9 관련 Enum](#49-관련-enum)
- [5. 습관 API](#5-습관-api)
    - [5.1 공통 사항](#51-공통-사항)
    - [5.2 습관 생성](#52-습관-생성)
    - [5.3 습관 실패 처리](#53-습관-실패-처리)
    - [5.4 습관 상세 조회](#54-습관-상세-조회)
    - [5.5 그룹 내 활성 습관 조회](#55-그룹-내-활성-습관-조회)
    - [5.6 그룹 내 실패한 습관 목록 조회](#56-그룹-내-실패한-습관-목록-조회)
    - [5.7 그룹 주간 실패 여부 체크 및 정산](#57-그룹-주간-실패-여부-체크-및-정산)
    - [5.8 관련 Enum](#58-관련-enum)
    - [5.9 구현 기준 참고 사항](#59-구현-기준-참고-사항)
- [6. 댓글 및 반응 공통 명세](#6-댓글-및-반응-공통-명세)
- [7. 습관 인증 API](#7-습관-인증-api)
    - [7.1 공통 사항](#71-공통-사항)
    - [7.2 습관 인증 사진 업로드용 서명 URL 발급](#72-습관-인증-사진-업로드용-서명-url-발급)
    - [7.3 습관 인증 등록](#73-습관-인증-등록)
    - [7.4 습관 인증 목록 조회](#74-습관-인증-목록-조회)
    - [7.5 반려된 습관 인증 재제출](#75-반려된-습관-인증-재제출)
    - [7.6 그룹 인증 사진 목록 조회](#76-그룹-인증-사진-목록-조회)
    - [7.7 인증 하트 상태 조회](#77-인증-하트-상태-조회)
    - [7.8 인증에 하트 누르기](#78-인증에-하트-누르기)
    - [7.9 인증에 누른 하트 취소](#79-인증에-누른-하트-취소)
    - [7.10 인증 댓글 목록 조회](#710-인증-댓글-목록-조회)
    - [7.11 인증 댓글 작성](#711-인증-댓글-작성)
    - [7.12 인증 댓글 수정](#712-인증-댓글-수정)
    - [7.13 인증 댓글 삭제](#713-인증-댓글-삭제)
    - [7.14 그룹 검토 대기 인증 목록 조회](#714-그룹-검토-대기-인증-목록-조회)
    - [7.15 단건 인증 승인](#715-단건-인증-승인)
    - [7.16 단건 인증 반려](#716-단건-인증-반려)
    - [7.17 인증 목록 일괄 승인](#717-인증-목록-일괄-승인)
    - [7.18 인증 목록 일괄 반려](#718-인증-목록-일괄-반려)
    - [7.19 관련 Enum](#719-관련-enum)
    - [7.20 주요 동작 및 권한 요약](#720-주요-동작-및-권한-요약)
- [8. 벌칙 인증 API](#8-벌칙-인증-api)
    - [8.1 공통 사항](#81-공통-사항)
    - [8.2 공통 DTO](#82-공통-dto)
    - [8.3 벌칙 인증 등록](#83-벌칙-인증-등록)
    - [8.4 반려된 벌칙 인증 재제출](#84-반려된-벌칙-인증-재제출)
    - [8.5 벌칙 인증 사진 업로드용 서명 URL 발급](#85-벌칙-인증-사진-업로드용-서명-url-발급)
    - [8.6 벌칙 인증 상세 조회](#86-벌칙-인증-상세-조회)
    - [8.7 그룹 내 누적 벌칙 개수 조회](#87-그룹-내-누적-벌칙-개수-조회)
    - [8.8 특정 멤버의 벌칙 목록 조회](#88-특정-멤버의-벌칙-목록-조회)
    - [8.9 단건 벌칙 인증 승인·반려](#89-단건-벌칙-인증-승인반려)
    - [8.10 검토 대기 벌칙 목록 조회](#810-검토-대기-벌칙-목록-조회)
    - [8.11 벌칙 목록 일괄 승인·반려](#811-벌칙-목록-일괄-승인반려)
    - [8.12 벌칙 인증 제출 취소 및 초기화](#812-벌칙-인증-제출-취소-및-초기화)
    - [8.13 벌칙 인증 하트](#813-벌칙-인증-하트)
    - [8.14 벌칙 인증 댓글](#814-벌칙-인증-댓글)

---
## 1. 공통 사항

### 1.1 기본 정보

- **Base URL:** `http://localhost:8080`

- **API Prefix:** `/api`

- **Content-Type:** `application/json`

- **인증 방식:** JWT 기반 Bearer 인증


### 1.2 인증 필터 규칙

인증이 필요한 API는 요청 헤더에 유효한 Access Token을 포함해야 한다.

```
Authorization: Bearer {accessToken}
```

인증 필터는 토큰의 서명과 유효기간을 검증하고, Access Token인지 확인한다. 이후 토큰의 회원 ID에 해당하는 회원이 `ACTIVE` 상태인지 확인한다.

|구분|규칙|
|---|---|
|인증 헤더|`Authorization: Bearer {accessToken}`|
|인증 토큰|Access Token|
|회원 상태|`ACTIVE` 회원만 인증 가능|
|인증 성공|요청의 `loginMemberId`에 회원 ID 설정|
|인증 실패|인증이 필요한 요청은 일반적으로 `401 Unauthorized` 반환|

### 1.3 인증 예외 경로

다음 경로는 인증 필터의 인증 없이 접근할 수 있다.

|HTTP Method|경로|설명|
|---|---|---|
|`POST`|`/api/members`|회원 가입|
|`POST`|`/api/auth/login`|아이디·비밀번호 로그인|
|`POST`|`/api/auth/refresh`|토큰 재발급|
|`POST`|`/api/auth/social/**`|소셜 로그인|
|`GET`|`/api/groups/invite/**`|로그인한 사용자 기준 초대 코드 조회 API 경로의 인증 필터 예외 설정|
|`GET`|`/`|루트 경로|
|`GET`|`/actuator/health`|애플리케이션 상태 확인|
|`GET`|`/api/connection-test`|연결 테스트|
|`OPTIONS`|`/**`|사전 요청|

Swagger UI 및 API 문서 경로(`/swagger-ui/**`, `/v3/api-docs/**`)도 인증 없이 접근할 수 있다.

**주의:** `/api/groups/invite/**`는 Spring Security 필터 규칙상 인증 예외로 설정되어 있지만, 컨트롤러에서 `@LoginMemberId`를 요구하고 서비스에서도 회원을 확인한다. 따라서 실제 초대 코드 조회는 로그인한 사용자만 이용할 수 있다.

위 목록에 해당하지 않는 요청은 인증이 필요하다.

### 1.4 공통 오류 응답

오류 응답은 다음 형식을 사용한다. `errors`는 입력값 검증 오류 등 추가 상세 정보가 있을 때 포함될 수 있다.

```
{
  "status": 400,
  "message": "요청 오류 메시지",
  "timestamp": "2026-10-28T03:00:00Z",
  "errors": [
    "상세 오류 메시지"
  ]
}
```

|HTTP Status|설명|
|---|---|
|`400 Bad Request`|비즈니스 규칙 위반, 입력값 검증 실패, 잘못된 요청 형식|
|`401 Unauthorized`|인증 실패, 로그인 실패, 유효하지 않은 토큰|
|`403 Forbidden`|접근 권한 없음|
|`404 Not Found`|요청한 리소스 또는 회원을 찾을 수 없음|
|`409 Conflict`|중복 아이디 등 상태 충돌|
|`502 Bad Gateway`|소셜 인증 공급자와의 통신 실패|
|`500 Internal Server Error`|처리되지 않은 서버 내부 오류|

오류 응답의 `timestamp`는 서버가 오류를 생성한 시각이다. 실제 오류 메시지는 예외 유형과 처리 경로에 따라 달라진다.

---

## 2. 인증 및 회원 도메인

### 2.1 회원 가입

새로운 일반 회원 계정을 생성한다. 가입 성공 시 회원 정보와 함께 생성된 회원 프로필을 반환한다.

- **Method:** `POST`

- **URL:** `/api/members`

- **인증:** 불필요

- **성공 상태 코드:** `201 Created`


**Request Body**

```
{
  "nickname": "홍길동",
  "username": "hong123",
  "password": "password123"
}
```

|필드|타입|필수|제약 조건|
|---|---|---|---|
|`nickname`|String|O|공백 불가, 최대 255자|
|`username`|String|O|공백 불가, 최대 255자, 중복 불가|
|`password`|String|O|공백 불가|

**Response Body**

```
{
  "memberId": 1,
  "nickname": "홍길동",
  "username": "hong123",
  "provider": "LOCAL",
  "createDate": "2026-10-28T12:00:00",
  "modifyDate": "2026-10-28T12:00:00"
}
```

**주요 오류**

|HTTP Status|상황|
|---|---|
|`400 Bad Request`|필수값 누락, 닉네임 또는 아이디가 255자를 초과함|
|`409 Conflict`|이미 사용 중인 아이디|
|`400 Bad Request`|사용할 수 없는 아이디 접두사(`withdrawn_`) 사용|

### 2.2 일반 로그인

아이디와 비밀번호를 검증하고 Access Token과 Refresh Token을 반환한다.

- **Method:** `POST`

- **URL:** `/api/auth/login`

- **인증:** 불필요

- **성공 상태 코드:** `200 OK`


**Request Body**

```
{
  "username": "hong123",
  "password": "password123"
}
```

|필드|타입|필수|제약 조건|
|---|---|---|---|
|`username`|String|O|공백 불가|
|`password`|String|O|공백 불가|

**Response Body**

```
{
  "accessToken": "{accessToken}",
  "refreshToken": "{refreshToken}",
  "tokenType": "Bearer"
}
```

**주요 오류**

|HTTP Status|상황|
|---|---|
|`400 Bad Request`|아이디 또는 비밀번호 누락|
|`401 Unauthorized`|아이디 또는 비밀번호 불일치, 탈퇴한 회원의 로그인 시도|
|`401 Unauthorized`|DB에 저장된 Refresh Token이 없음|

아이디가 존재하지 않는 경우와 비밀번호가 일치하지 않는 경우에는 동일한 로그인 실패 메시지를 반환한다.

### 2.3 소셜 로그인

소셜 공급자의 인가 코드로 사용자를 인증한다. 현재 지원되는 공급자는 Google이다.

- **Method:** `POST`

- **URL:** `/api/auth/social/{provider}`

- **인증:** 불필요

- **성공 상태 코드:** `200 OK`


**Path Variable**

|필드|타입|설명|
|---|---|---|
|`provider`|String|소셜 공급자 이름. 현재 `google` 지원|

**Request Body**

```
{
  "code": "{authorizationCode}",
  "redirectUri": "http://localhost:3000/oauth/callback"
}
```

|필드|타입|필수|설명|
|---|---|---|---|
|`code`|String|O|소셜 공급자에서 발급한 인가 코드|
|`redirectUri`|String|O|인가 코드를 발급받을 때 사용한 리다이렉트 주소|

**Response Body**

일반 로그인과 동일한 형식이다.

```
{
  "accessToken": "{accessToken}",
  "refreshToken": "{refreshToken}",
  "tokenType": "Bearer"
}
```

**동작 규칙**

- 처음 로그인하는 소셜 계정은 회원을 자동 생성한다.

- 기존 계정은 공급자와 공급자 식별자를 기준으로 조회한다.

- 이메일을 기준으로 일반 회원과 소셜 회원을 통합하지 않는다.

- 소셜 회원은 일반 아이디·비밀번호 로그인을 사용할 수 없다.

- Google 사용자 정보에서 이메일은 수집하거나 저장하지 않는다.


**주요 오류**

|HTTP Status|상황|
|---|---|
|`400 Bad Request`|인가 코드 또는 리다이렉트 주소 누락, 지원하지 않는 공급자|
|`401 Unauthorized`|인가 코드 오류·만료, 소셜 로그인 실패, 탈퇴한 회원|
|`502 Bad Gateway`|소셜 공급자와 통신할 수 없음|

### 2.4 인증 토큰 재발급

Refresh Token을 검증하고 새로운 Access Token을 발급한다.

- **Method:** `POST`

- **URL:** `/api/auth/refresh`

- **인증:** 불필요

- **성공 상태 코드:** `200 OK`


**Request Body**

```
{
  "refreshToken": "{refreshToken}"
}
```

|필드|타입|필수|제약 조건|
|---|---|---|---|
|`refreshToken`|String|O|공백 불가|

**Response Body**

```
{
  "accessToken": "{newAccessToken}",
  "refreshToken": "{existingRefreshToken}",
  "tokenType": "Bearer"
}
```

**검증 규칙**

1. Refresh Token의 서명과 유효성을 검증한다.

2. 토큰 유형이 `refresh`인지 확인한다.

3. 토큰에 포함된 회원 ID를 추출한다.

4. 회원이 활성 상태이며 요청 토큰이 DB에 저장된 Refresh Token과 일치하는지 확인한다.

5. 검증 성공 시 새로운 Access Token을 발급한다.


현재 구현에서는 Refresh Token에 JWT 만료 시간을 설정하지 않는다. 다만 회원의 DB 저장 토큰과 일치해야 재발급할 수 있다.

**주요 오류**

|HTTP Status|상황|
|---|---|
|`400 Bad Request`|Refresh Token 누락|
|`401 Unauthorized`|유효하지 않은 Refresh Token|
|`401 Unauthorized`|DB에 저장된 토큰과 불일치|
|`404 Not Found`|토큰의 회원 ID에 해당하는 회원이 없음|

### 2.5 내 정보 조회

현재 로그인한 회원의 프로필을 조회한다.

- **Method:** `GET`

- **URL:** `/api/members/me`

- **인증:** 필요

- **성공 상태 코드:** `200 OK`


**Request Header**

```
Authorization: Bearer {accessToken}
```

**Response Body**

```
{
  "memberId": 1,
  "nickname": "홍길동",
  "username": "hong123",
  "provider": "LOCAL",
  "createDate": "2026-10-28T12:00:00",
  "modifyDate": "2026-10-28T12:00:00"
}
```

### 2.6 내 정보 수정

현재 로그인한 회원의 닉네임 또는 비밀번호를 수정한다.

- **Method:** `PATCH`

- **URL:** `/api/members/me`

- **인증:** 필요

- **성공 상태 코드:** `200 OK`


**Request Body**

```
{
  "nickname": "새로운 닉네임",
  "currentPassword": "password123",
  "newPassword": "newPassword456"
}
```

|필드|타입|필수|설명|
|---|---|---|---|
|`nickname`|String|선택|최대 255자. null 또는 빈 문자열이면 변경하지 않음|
|`currentPassword`|String|선택|비밀번호 변경 시 기존 비밀번호|
|`newPassword`|String|선택|변경할 새 비밀번호|

**동작 규칙**

- 닉네임은 null 또는 공백 문자열이면 변경하지 않는다.

- 새 비밀번호가 입력되면 기존 비밀번호를 확인해야 한다.

- 기존 비밀번호가 누락되거나 일치하지 않으면 요청을 거절한다.

- 소셜 로그인 회원은 비밀번호를 변경할 수 없다.

- 비밀번호는 암호화하여 저장한다.


**Response Body**

수정 후 회원 프로필을 반환한다. 형식은 `내 정보 조회` 응답과 동일하다.

**주요 오류**

|HTTP Status|상황|
|---|---|
|`400 Bad Request`|닉네임이 255자를 초과함|
|`400 Bad Request`|소셜 회원의 비밀번호 변경 시도|
|`400 Bad Request`|기존 비밀번호 누락 또는 불일치|
|`401 Unauthorized`|인증 실패|

### 2.7 회원 탈퇴

현재 로그인한 회원을 탈퇴 처리한다.

- **Method:** `DELETE`

- **URL:** `/api/members/me`

- **인증:** 필요

- **성공 상태 코드:** `204 No Content`


**Request Header**

```
Authorization: Bearer {accessToken}
```

**Response Body**

없음.

**동작 규칙**

- 진행 중인 그룹에 활성 멤버로 남아 있으면 탈퇴할 수 없다.

- 탈퇴 시 회원 레코드를 물리적으로 삭제하지 않고 익명화한다.

- 닉네임은 `탈퇴한 회원`으로 변경된다.

- 아이디, 비밀번호, 소셜 공급자 및 공급자 식별자도 익명화되며 Refresh Token은 제거된다.

- 탈퇴한 회원은 다시 로그인할 수 없다.


**주요 오류**

|HTTP Status|상황|
|---|---|
|`400 Bad Request`|진행 중인 그룹이 있어 탈퇴할 수 없음|
|`409 Conflict`|이미 탈퇴한 회원|
|`401 Unauthorized`|인증 실패|

### 2.8 회원 관련 Enum

#### MemberProvider

|값|설명|
|---|---|
|`LOCAL`|아이디·비밀번호 가입|
|`GOOGLE`|Google 소셜 로그인|

#### MemberStatus

|값|설명|
|---|---|
|`ACTIVE`|활성 회원|
|`WITHDRAWN`|탈퇴한 회원|

각 도메인에서 발생하는 구체적인 예외 메시지와 조건은 해당 도메인의 API 명세에서 설명한다.

---

## 3. 그룹 도메인

### 3.1 그룹 생성

`**POST /api/groups**`

그룹을 생성하고 요청한 회원을 해당 그룹의 방장으로 등록한다.

**요청 본문**

```
{
  "title": "아침 운동 챌린지",
  "description": "매일 아침 운동하기",
  "deadline": "2026-12-31",
  "penalty": "미인증 시 벌금 1000원",
  "password": "group1234",
  "memberLimit": 10
}
```

|필드|타입|필수|설명|
|---|---|---|---|
|`title`|`String`|O|그룹 제목. 공백 불가|
|`description`|`String`|X|그룹 설명|
|`deadline`|`LocalDate`|X|그룹 마감일|
|`penalty`|`String`|O|그룹 벌칙. 공백 불가|
|`password`|`String`|O|그룹 가입 비밀번호. 공백 불가|
|`memberLimit`|`Integer`|X|최대 인원. 1 이상, 미입력 시 10|

**응답:** `**201 Created**`

응답 본문은 그룹 상세 응답 형식을 따른다.

### 3.2 내 그룹 목록 조회

`**GET /api/groups?status=ACTIVE**`

로그인한 회원이 가입한 그룹 목록을 조회한다.

|Query Parameter|타입|필수|기본값|설명|
|---|---|---|---|---|
|`status`|`GroupStatus`|X|`ACTIVE`|조회할 그룹 상태|

`status`는 `ACTIVE` 또는 `FINISH`를 사용할 수 있다.

**응답:** `**200 OK**`

그룹 목록을 반환한다. 각 항목은 다음 필드를 포함한다.

|필드|타입|설명|
|---|---|---|
|`id`|`Long`|그룹 ID|
|`title`|`String`|그룹 제목|
|`description`|`String`|그룹 설명|
|`memberLimit`|`Integer`|최대 인원|
|`currentMemberCount`|`Long`|현재 활동 중인 그룹 인원|
|`status`|`String`|그룹 상태|
|`role`|`String`|로그인 회원의 그룹 내 역할|

### 3.3 그룹 상세 조회

`**GET /api/groups/{groupId}**`

가입한 그룹의 상세 정보를 조회한다. 해당 그룹의 활동 중인 회원만 조회할 수 있다.

**응답:** `**200 OK**`

그룹 상세 응답을 반환한다.

|필드|타입|설명|
|---|---|---|
|`id`|`Long`|그룹 ID|
|`title`|`String`|그룹 제목|
|`description`|`String`|그룹 설명|
|`startDate`|`LocalDate`|그룹 생성일 기준 날짜|
|`deadline`|`LocalDate`|그룹 마감일|
|`penalty`|`String`|그룹 벌칙|
|`inviteCode`|`String`|초대 코드|
|`memberLimit`|`Integer`|최대 인원|
|`createDate`|`LocalDateTime`|그룹 생성 시각|
|`inviteLink`|`String`|초대 링크|
|`status`|`GroupStatus`|그룹 상태|
|`isJoined`|`Boolean`|로그인 회원의 가입 여부|
|`currentMemberCount`|`Long`|현재 활동 중인 그룹 인원|
|`rankings`|`Rankings`|종료 그룹의 랭킹 정보. 없으면 `null`|

### 3.4 그룹 수정

`**PUT /api/groups/{groupId}**`

그룹 정보를 수정한다. 방장만 수정할 수 있으며, 종료된 그룹은 수정할 수 없다.

**요청 본문 예시**

```
{
  "title": "저녁 운동 챌린지",
  "description": "매일 저녁 운동하기",
  "deadline": "2026-12-31",
  "penalty": "미인증 시 벌금 2000원",
  "password": "newPassword",
  "memberLimit": 15
}
```

|필드|타입|필수|설명|
|---|---|---|---|
|`title`|`String`|X|그룹 제목|
|`description`|`String`|X|그룹 설명. 빈 문자열로 변경 가능|
|`deadline`|`LocalDate`|X|그룹 마감일|
|`penalty`|`String`|X|그룹 벌칙|
|`password`|`String`|X|변경할 가입 비밀번호|
|`memberLimit`|`Integer`|X|최대 인원. 1 이상이며 현재 활동 인원보다 작을 수 없음|

요청에서 `null`인 필드는 기존 값을 유지한다. 단, `description`은 빈 문자열로 전달하면 기존 설명을 비울 수 있다.

**응답:** `**200 OK**`

응답 본문 없음.

### 3.5 그룹 삭제

`**DELETE /api/groups/{groupId}**`

그룹을 삭제한다. 방장만 삭제할 수 있으며, 종료된 그룹은 삭제할 수 없다. 그룹에 방장 외 다른 활동 회원이 남아 있으면 삭제할 수 없다.

**응답:** `**204 No Content**`

응답 본문 없음.

### 3.6 관련 Enum

| Enum                | 값        | 설명        |
| ------------------- | -------- | --------- |
| `GroupStatus`       | `ACTIVE` | 진행 중인 그룹  |
| `GroupStatus`       | `FINISH` | 종료된 그룹    |

---

## 4. 그룹 멤버 API

### 4.1. 공통 사항

- **Base URL:** `/api/groups`

- **인증:** 모든 API에 로그인 필요

- **인증 방식:** `Authorization: Bearer {accessToken}`

- **공통 예외 응답:** 공통 예외 처리 규칙 참고


#### 그룹 멤버 역할

|값|설명|
|---|---|
|`OWNER`|그룹 방장|
|`MEMBER`|일반 그룹 멤버|

#### 그룹 멤버 상태

|값|설명|
|---|---|
|`ACTIVE`|활동 중인 멤버|
|`LEFT`|탈퇴 처리된 멤버|

#### 멤버 탈퇴 및 데이터 보존 정책

|구분|활성화된 그룹|마감된 그룹|
|---|---|---|
|멤버 탈퇴 처리|멤버 및 관련 데이터 하드 삭제|멤버 상태를 `LEFT`로 변경|
|멤버 추방|방장에 의한 하드 삭제 가능|추방 불가|
|탈퇴 멤버 기록|삭제되어 조회할 수 없음|통계 및 이력 보존을 위해 유지|
|탈퇴 멤버 조회|삭제된 멤버는 조회되지 않음|`LEFT` 상태 멤버도 조회 가능|

> 마감된 그룹은 통계 산출과 기록 보존이 필요하므로 탈퇴한 멤버의 데이터와 그룹 멤버 레코드를 유지한다. 따라서 멤버 목록 및 상세 조회 시 `LEFT` 상태의 멤버도 조회 대상에 포함된다.

---

### 4.2. 초대 코드로 그룹 조회

`**GET /api/groups/invite/{inviteCode}**`

초대 코드로 그룹 정보를 조회한다. 아직 그룹에 가입하지 않은 회원도 로그인한 상태라면 조회할 수 있다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`inviteCode`|String|O|그룹 초대 코드|

#### Response

- **200 OK**

- 반환 타입: `GroupResponse.Detail`


|필드|타입|설명|
|---|---|---|
|`id`|Long?|그룹 ID|
|`title`|String|그룹 이름|
|`description`|String?|그룹 설명|
|`startDate`|LocalDate|그룹 시작일|
|`deadline`|LocalDate?|그룹 마감일|
|`penalty`|String|벌칙 정보|
|`inviteCode`|String|초대 코드|
|`memberLimit`|Int|최대 인원|
|`createDate`|LocalDateTime?|생성 일시|
|`inviteLink`|String|초대 링크|
|`status`|String|그룹 상태|
|`isJoined`|Boolean|현재 회원의 그룹 가입 여부|
|`currentMemberCount`|Long|현재 활동 중인 멤버 수|
|`rankings`|Rankings?|그룹 랭킹 정보|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|이미 마감된 그룹|
|`404 Not Found`|유효하지 않거나 존재하지 않는 초대 코드, 존재하지 않는 회원|

#### 참고 사항

- 그룹 가입 여부와 관계없이 초대 코드로 그룹 정보를 조회할 수 있다.

- 로그인이 필요하다.

- 이미 마감된 그룹은 조회할 수 없다.


---

### 4.3. 초대 코드로 그룹 가입

`**POST /api/groups/join/{inviteCode}**`

초대 코드와 그룹 비밀번호를 사용해 그룹에 가입한다. 가입 성공 시 그룹 ID를 반환한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`inviteCode`|String|O|가입할 그룹의 초대 코드|

#### Request Body

```
{
  "password": "group-password"
}
```

|필드|타입|필수|제약 조건|
|---|---|---|---|
|`password`|String|O|그룹 비밀번호. 공백이 아닌 값 필수|

#### Response

- **200 OK**

- 반환 타입: `GroupMemberResponse.JoinSuccess`


```
{
  "groupId": 1
}
```

|필드|타입|설명|
|---|---|---|
|`groupId`|Long|가입한 그룹 ID|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|요청값 검증 실패, 마감된 그룹, 그룹 정원 초과|
|`403 Forbidden`|그룹 비밀번호 불일치|
|`404 Not Found`|유효하지 않거나 존재하지 않는 초대 코드, 존재하지 않는 회원|

#### 참고 사항

- 이미 해당 그룹에 가입한 회원은 그룹이 마감되지 않았다면 기존 그룹 ID를 반환한다.

- 이미 가입한 회원인지와 그룹 정원을 먼저 확인하며, 정원이 초과된 경우 비밀번호를 검증하기 전에 가입을 거부한다.

- 실제 가입 처리 시 그룹을 잠근 상태에서 마감 여부, 중복 가입 여부 및 정원을 다시 확인한다.

- 가입 성공 시 역할은 `MEMBER`, 상태는 `ACTIVE`로 저장된다.


---

### 4.4. 그룹 멤버 목록 조회

`**GET /api/groups/{groupId}/members**`

특정 그룹의 멤버 목록을 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|

#### Response

- **200 OK**

- 반환 타입: `List<GroupMemberResponse.Simple>`


```
[
  {
    "groupMemberId": 10,
    "memberId": 1,
    "nickname": "사용자1",
    "username": "user1",
    "role": "OWNER",
    "habitId": 100,
    "habitTitle": "매일 독서하기",
    "habitDescription": "매일 30분 독서",
    "provider": "LOCAL"
  }
]
```

|필드|타입|설명|
|---|---|---|
|`groupMemberId`|Long|그룹 멤버 ID|
|`memberId`|Long|회원 ID|
|`nickname`|String|닉네임|
|`username`|String|아이디|
|`role`|String|그룹 내 역할 (`OWNER`, `MEMBER`)|
|`habitId`|Long?|활동 중인 습관 ID|
|`habitTitle`|String?|활동 중인 습관 이름|
|`habitDescription`|String?|활동 중인 습관 설명|
|`provider`|MemberProvider|가입 방식 (`LOCAL`, `GOOGLE`)|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|호출 회원이 해당 그룹의 활동 중인 멤버가 아님|

#### 참고 사항

- `ACTIVE` 상태의 멤버뿐 아니라, 기록이 보존된 `LEFT` 상태의 멤버도 조회 결과에 포함될 수 있다.

- 활성화된 그룹에서는 탈퇴 및 추방 시 멤버 레코드가 하드 삭제되므로 삭제된 멤버는 조회되지 않는다.

- 마감된 그룹에서는 탈퇴 멤버 레코드를 보존하므로 해당 멤버도 목록에 포함된다.

- 습관 정보는 `ACTIVE` 상태인 습관을 기준으로 조회한다. 활동 중인 습관이 없으면 관련 필드는 `null`이다.

---

### 4.5. 그룹 멤버 상세 조회

`**GET /api/groups/{groupId}/members/{groupMemberId}**`

특정 그룹 멤버의 상세 정보와 누적 벌칙 인증 건수를 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|
|`groupMemberId`|Long|O|조회할 그룹 멤버 ID|

#### Response

- **200 OK**

- 반환 타입: `GroupMemberResponse.Detail`


```
{
  "groupMemberId": 10,
  "memberId": 1,
  "nickname": "사용자1",
  "username": "user1",
  "role": "MEMBER",
  "penaltyCount": 3,
  "provider": "LOCAL"
}
```

|필드|타입|설명|
|---|---|---|
|`groupMemberId`|Long|그룹 멤버 ID|
|`memberId`|Long|회원 ID|
|`nickname`|String|닉네임|
|`username`|String|아이디|
|`role`|String|그룹 내 역할 (`OWNER`, `MEMBER`)|
|`penaltyCount`|Long|해당 그룹 멤버에게 연결된 벌칙 인증 레코드 수|
|`provider`|MemberProvider|가입 방식 (`LOCAL`, `GOOGLE`)|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|호출 회원이 해당 그룹의 활동 중인 멤버가 아님|
|`404 Not Found`|해당 그룹에 속한 조회 대상 멤버를 찾을 수 없음|

#### 참고 사항

- 마감된 그룹에서는 통계와 이력 보존을 위해 `LEFT` 상태의 멤버도 조회할 수 있다.

- `penaltyCount`는 해당 멤버에게 연결된 `PenaltyVerify` 레코드 전체를 집계한다.

- 조회 대상이 그룹에 속하지 않거나 해당 그룹 멤버 레코드가 존재하지 않으면 예외가 발생한다.


---

### 4.6. 그룹 탈퇴

`**DELETE /api/groups/{groupId}/leave**`

현재 회원이 그룹에서 탈퇴한다. 그룹 상태와 남아 있는 활동 멤버 수에 따라 멤버 및 관련 데이터의 처리 방식이 달라진다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|탈퇴할 그룹 ID|

#### Response

- **200 OK**

- 응답 본문 없음


#### 탈퇴 처리 규칙

|조건|처리 방식|
|---|---|
|활성화된 그룹에 다른 활동 멤버가 남아 있음|탈퇴 멤버의 습관 인증 기록, 벌칙 인증 기록, 습관 및 그룹 멤버 레코드를 하드 삭제|
|마감된 그룹에 다른 활동 멤버가 남아 있음|그룹 멤버 상태를 `LEFT`로 변경하고 관련 기록을 보존|
|현재 회원이 마지막 활동 멤버|관련 인증 데이터, 습관, 그룹 멤버 레코드 및 그룹을 함께 삭제|
|방장이 활성화된 그룹에서 다른 활동 멤버가 남아 있는 상태로 탈퇴 시도|탈퇴 거부. 먼저 방장 권한을 위임해야 함|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|이미 탈퇴한 멤버, 다른 멤버가 남아 있는 활성화된 그룹에서 방장이 탈퇴 시도|
|`404 Not Found`|존재하지 않는 그룹, 해당 그룹의 멤버가 아님|

#### 참고 사항

- 멤버 탈퇴 처리는 그룹 상태에 따라 달라진다.

- 활성화된 그룹에서 멤버가 탈퇴하면 해당 멤버의 관련 데이터를 하드 삭제한다. 따라서 삭제된 멤버는 멤버 목록 및 상세 조회 결과에 나타나지 않는다.

- 마감된 그룹에서 멤버가 탈퇴하면 `LEFT` 상태로 변경하고 기록을 보존한다. 보존된 데이터는 멤버 조회 및 통계 산출에 활용할 수 있다.

- 마지막 활동 멤버가 탈퇴하면 그룹 전체와 관련 데이터도 삭제된다.


---

### 4.7. 그룹 멤버 추방

`**DELETE /api/groups/{groupId}/members/{groupMemberId}/kick**`

그룹 방장이 특정 멤버를 강제 퇴장시킨다. 활성화된 그룹에서 활동 중인 멤버를 대상으로만 처리할 수 있다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|
|`groupMemberId`|Long|O|추방할 그룹 멤버 ID|

#### Response

- **204 No Content**

- 응답 본문 없음


#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|대상 멤버가 해당 그룹에 속하지 않음, 방장 본인을 추방하려는 경우, 그룹이 종료됨, 대상 멤버가 활동 중이 아님|
|`403 Forbidden`|호출 회원이 방장이 아님|
|`404 Not Found`|호출 회원이 그룹 멤버가 아님, 추방 대상 멤버가 존재하지 않음|

#### 참고 사항

- 마감된 그룹에서는 멤버를 추방할 수 없다.

- 추방 대상은 `ACTIVE` 상태여야 한다.

- 방장 본인은 이 API로 추방할 수 없다.

- 추방 시 대상 멤버의 습관 인증 기록, 벌칙 인증 기록, 습관 및 그룹 멤버 레코드를 하드 삭제한다.

- 따라서 추방된 멤버의 기록은 해당 그룹의 멤버 목록 및 상세 조회에서 더 이상 조회되지 않는다.


---

### 4.8. 그룹 방장 위임

`**PATCH /api/groups/{groupId}/members/{groupMemberId}/delegate**`

현재 방장이 같은 그룹의 다른 활동 멤버에게 방장 권한을 위임한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|
|`groupMemberId`|Long|O|방장 권한을 위임할 그룹 멤버 ID|

#### Response

- **200 OK**

- 응답 본문 없음


#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|대상 멤버가 해당 그룹에 속하지 않음, 자기 자신에게 위임 시도, 그룹이 종료됨, 대상 멤버가 활동 중이 아님|
|`403 Forbidden`|호출 회원이 현재 방장이 아님|
|`404 Not Found`|현재 회원이 그룹 멤버가 아님, 위임 대상 멤버가 존재하지 않음|

#### 위임 처리 규칙

- 현재 방장의 역할을 `MEMBER`로 변경한다.

- 대상 멤버의 역할을 `OWNER`로 변경한다.

- 대상 멤버는 `ACTIVE` 상태여야 한다.

- 종료된 그룹에서는 방장 권한을 위임할 수 없다.

- 위임 대상은 현재 방장과 다른 그룹 멤버여야 한다.


---

### 4.9. 관련 Enum

#### GroupMemberRole

|값|설명|
|---|---|
|`OWNER`|그룹 방장|
|`MEMBER`|일반 그룹 멤버|

#### GroupMemberStatus

|값|설명|
|---|---|
|`ACTIVE`|활동 중인 멤버|
|`LEFT`|탈퇴 처리된 멤버|

#### MemberProvider

|값|설명|
|---|---|
|`LOCAL`|자체 회원가입|
|`GOOGLE`|Google 소셜 로그인|

---

## 5. 습관 API


### 5.1. 공통 사항

- **Base URL:** `/api`

- **인증:** 모든 API에 로그인 필요

- **인증 방식:** `Authorization: Bearer {accessToken}`

- **공통 예외 응답:** 공통 예외 처리 규칙 참고


#### 습관 상태 (`HabitStatus`)

|값|설명|
|---|---|
|`ACTIVE`|진행 중인 습관|
|`FAILED`|실패 처리된 습관|

#### 주간 판정 결과 (`WeeklyCheckResult`)

|값|설명|
|---|---|
|`TOO_EARLY`|그룹 가입 후 1주가 지나지 않아 판정 대상이 아님|
|`ALREADY_FAILED`|이미 실패 처리된 습관으로, 재판정하지 않음|
|`GROUP_FINISHED`|그룹이 종료되어 판정하지 않음|
|`BEFORE_DEADLINE`|습관 생성 시점이 이번 판정 구간의 마감 시점 이후이므로 아직 판정 대상이 아님|
|`FIRST_WEEK_EXEMPT`|습관 생성 후 처음 맞는 부분 주이므로 판정 면제|
|`PASSED`|검토가 완료되었으며 승인된 인증 횟수가 목표 횟수 이상임|
|`PENDING_REVIEW`|미검토 인증이 남아 있고 검토 유예시간도 지나지 않아 판정을 보류함|
|`FAILED_INSUFFICIENT_SUBMISSION`|검토가 완료되었지만 승인된 인증 횟수가 목표에 미달하여 습관을 실패 처리함|

---

### 5.2. 습관 생성

`**POST /api/groups/{groupId}/habits**`

특정 그룹에서 실천할 새로운 습관을 생성한다. 한 그룹 멤버는 `ACTIVE` 상태의 습관을 하나만 보유할 수 있다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|습관을 생성할 그룹 ID|

#### Request Body

```
{
  "title": "매일 독서하기",
  "description": "매일 30분 이상 책 읽기",
  "days": 5
}
```

|필드|타입|필수|검증 조건|
|---|---|---|---|
|`title`|String|O|공백이 아닌 값 필수, 최대 255자|
|`description`|String|X|최대 255자, `null` 허용|
|`days`|Int|O|1~7|

#### 요청값 검증

|필드|조건|검증 실패 메시지|
|---|---|---|
|`title`|누락, `null`, 빈 문자열 또는 공백만 입력|`습관 제목은 필수입니다.`|
|`title`|255자 초과|`습관 제목은 255자 이하여야 합니다.`|
|`description`|255자 초과|`습관 설명은 255자 이하여야 합니다.`|
|`days`|1 미만|`실천 일수는 최소 1일입니다.`|
|`days`|7 초과|`실천 일수는 최대 7일입니다.`|

`description`은 필수가 아니며, 빈 문자열도 길이 제한을 넘지 않으면 검증을 통과한다. `days`를 생략하면 기본값 `0`이 적용되므로 검증에 실패한다.

#### Response

- **200 OK**

- 반환 타입: `HabitResponse`


```
{
  "id": 100,
  "createDate": "2026-10-28T10:00:00",
  "modifyDate": "2026-10-28T10:00:00",
  "groupMemberId": 10,
  "title": "매일 독서하기",
  "description": "매일 30분 이상 책 읽기",
  "days": 5,
  "status": "ACTIVE"
}
```

|필드|타입|설명|
|---|---|---|
|`id`|Long?|습관 ID|
|`createDate`|LocalDateTime?|생성 일시|
|`modifyDate`|LocalDateTime?|수정 일시|
|`groupMemberId`|Long?|그룹 멤버 ID|
|`title`|String|습관 제목|
|`description`|String?|습관 설명|
|`days`|Int|주간 목표 인증 횟수|
|`status`|HabitStatus|습관 상태. 최초 생성 시 `ACTIVE`|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|요청값 검증 실패, 해당 그룹의 멤버를 찾을 수 없음, 그룹이 종료됨|
|`409 Conflict`|이미 `ACTIVE` 상태인 습관이 존재함|

#### 참고 사항

- 습관 생성 시 그룹 멤버 레코드를 잠근 후 활성 습관 존재 여부를 확인한다.

- 같은 멤버가 동시에 여러 습관을 생성하려는 경우 순차적으로 처리되도록 한다.

- 그룹이 종료된 경우 습관을 생성할 수 없다.

- 활성 습관이 이미 존재하면 새 습관을 생성할 수 없다.


---

### 5.3. 습관 실패 처리

`**POST /api/habits/fail/{habitId}**`

회원 본인의 진행 중인 습관을 포기하거나 실패 처리한다. 실패 처리 시 습관 상태를 `FAILED`로 변경하고 벌칙 인증 레코드를 생성한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`habitId`|Long|O|실패 처리할 습관 ID|

#### Request Body

없음

#### Response

- **200 OK**

- 응답 본문 없음


#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|해당 회원의 습관을 찾을 수 없음, 그룹이 종료됨|
|`409 Conflict`|습관 상태가 `ACTIVE`가 아니어서 실패 처리할 수 없음|

#### 참고 사항

- 요청 회원이 해당 습관의 소유자인지 먼저 확인한 후 습관 레코드를 잠그고 상태를 재확인한다.

- 이미 실패 처리된 습관을 다시 실패 처리할 수 없다.

- 그룹이 종료된 경우 습관을 포기할 수 없다.

- 실패 처리에 성공하면 습관 상태를 `FAILED`로 변경하고 `PenaltyVerify` 레코드를 생성한다.

- 동일한 습관에 대한 동시 실패 처리 요청은 행 잠금으로 순차 처리된다.


---

### 5.4. 습관 상세 조회

`**GET /api/habits/{id}**`

특정 습관의 기본 정보와 상태를 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`id`|Long|O|조회할 습관 ID|

#### Response

- **200 OK**

- 반환 타입: `HabitResponse`


```
{
  "id": 100,
  "createDate": "2026-10-28T10:00:00",
  "modifyDate": "2026-10-28T10:00:00",
  "groupMemberId": 10,
  "title": "매일 독서하기",
  "description": "매일 30분 이상 책 읽기",
  "days": 5,
  "status": "ACTIVE"
}
```

|필드|타입|설명|
|---|---|---|
|`id`|Long?|습관 ID|
|`createDate`|LocalDateTime?|생성 일시|
|`modifyDate`|LocalDateTime?|수정 일시|
|`groupMemberId`|Long?|그룹 멤버 ID|
|`title`|String|습관 제목|
|`description`|String?|습관 설명|
|`days`|Int|주간 목표 인증 횟수|
|`status`|HabitStatus|습관 상태 (`ACTIVE`, `FAILED`)|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|호출 회원이 해당 습관이 속한 그룹의 멤버가 아님|
|`404 Not Found`|존재하지 않는 습관|

#### 참고 사항

- 습관 ID로 조회하며, `ACTIVE`와 `FAILED` 상태 모두 조회할 수 있다.

- 현재 서비스 코드는 그룹 멤버 레코드의 존재 여부로 접근 권한을 확인하며, 멤버 상태가 반드시 `ACTIVE`인지 별도로 검사하지 않는다.


---

### 5.5. 그룹 내 활성 습관 조회

`**GET /api/groups/{groupId}/habits/active**`

특정 그룹에서 호출 회원에게 연결된 `ACTIVE` 상태의 습관을 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|

#### Response

- **200 OK**

- 반환 타입: `HabitResponse`


```
{
  "id": 100,
  "createDate": "2026-10-28T10:00:00",
  "modifyDate": "2026-10-28T10:00:00",
  "groupMemberId": 10,
  "title": "매일 독서하기",
  "description": "매일 30분 이상 책 읽기",
  "days": 5,
  "status": "ACTIVE"
}
```

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|현재 진행 중인 습관이 없음|

#### 참고 사항

- 회원 ID, 그룹 ID 및 습관 상태가 `ACTIVE`인 조건으로 조회한다.

- 결과가 없으면 `현재 진행 중인 습관이 없습니다.` 예외가 발생한다.

- 현재 서비스 코드에는 그룹 가입 여부를 별도로 검증하는 로직이 없다.


---

### 5.6. 그룹 내 실패한 습관 목록 조회

`**GET /api/groups/{groupId}/habits/fail**`

특정 그룹에서 호출 회원에게 연결된 `FAILED` 상태의 습관 목록을 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|

#### Response

- **200 OK**

- 반환 타입: `List<HabitResponse>`


```
[
  {
    "id": 100,
    "createDate": "2026-10-20T10:00:00",
    "modifyDate": "2026-10-27T10:00:00",
    "groupMemberId": 10,
    "title": "매일 독서하기",
    "description": "매일 30분 이상 책 읽기",
    "days": 5,
    "status": "FAILED"
  }
]
```

#### 참고 사항

- 회원 ID, 그룹 ID 및 습관 상태가 `FAILED`인 조건으로 조회한다.

- 생성 일시 기준 내림차순으로 반환한다.

- 조회 결과가 없으면 빈 배열을 반환한다.

- 현재 서비스 코드에는 그룹 가입 여부를 별도로 검증하는 로직이 없다.


---

### 5.7. 그룹 주간 실패 여부 체크 및 정산

`**POST /api/groups/{groupId}/weekly-check**`

그룹 내 `ACTIVE` 상태의 습관을 판정해 주간 목표 달성 여부를 확인한다. 방장이 수동으로 즉시 정산을 실행할 때 사용하는 API다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|정산할 그룹 ID|

#### Request Body

없음

#### Response

- **200 OK**

- 반환 타입: `List<HabitWeeklyCheckResponse>`


```
[
  {
    "habitId": 100,
    "result": "PASSED",
    "habitStatus": "ACTIVE"
  },
  {
    "habitId": 101,
    "result": "FAILED_INSUFFICIENT_SUBMISSION",
    "habitStatus": "FAILED"
  }
]
```

|필드|타입|설명|
|---|---|---|
|`habitId`|Long?|판정 대상 습관 ID|
|`result`|WeeklyCheckResult|주간 판정 결과|
|`habitStatus`|HabitStatus|판정 처리 후 습관 상태|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|해당 그룹의 멤버가 아님, 그룹이 종료됨|
|`403 Forbidden`|호출 회원이 방장이 아님|

#### 판정 규칙

1. 호출 회원이 그룹 멤버인지 확인하고, 방장인지 검증한다.

2. 그룹이 종료되었다면 판정을 실행하지 않는다.

3. 그룹 내 `ACTIVE` 상태인 습관을 판정 대상으로 조회한다.

4. 습관이 이미 실패 처리되었다면 `ALREADY_FAILED`로 반환하고 재판정하지 않는다.

5. 그룹 가입 후 1주가 지나지 않았다면 `TOO_EARLY`로 반환한다.

6. 습관 생성 시점이 이번 판정 구간의 마감 시점 이후라면 `BEFORE_DEADLINE`으로 반환한다.

7. 습관을 생성한 뒤 처음 맞는 주 경계가 부분 주에 해당하면 `FIRST_WEEK_EXEMPT`로 반환해 해당 주의 목표 달성 판정을 면제한다.

8. 판정 구간에 미검토(`PENDING`) 인증이 남아 있다면 검토 유예시간이 지났는지 확인한다.

9. 검토 유예시간이 지나지 않았다면 `PENDING_REVIEW`로 반환한다.

10. 검토 유예시간이 지났다면 해당 판정 구간의 미검토 인증을 자동 승인한 후 승인 횟수로 결과를 판정한다.

11. 승인 횟수가 습관의 목표 횟수(`days`) 이상이면 `PASSED`로 반환한다.

12. 승인 횟수가 목표 횟수에 미달하면 습관 상태를 `FAILED`로 변경하고 벌칙 인증 레코드를 생성한 뒤 `FAILED_INSUFFICIENT_SUBMISSION`으로 반환한다.


#### 주간 판정 자동 실행

- 배치 스케줄러는 매일 00시 05분에 주간 판정을 실행한다.

- 기본 묶음 크기는 100개이며, `app.habit.weekly-check.chunk-size` 설정으로 변경할 수 있다.

- 묶음별로 별도 트랜잭션을 사용한다.

- 묶음 단위 판정이 실패하면 개별 습관 단위로 다시 시도하며, 재시도에도 실패한 습관 ID는 로그에 기록한다.

- 자동 판정과 방장의 수동 판정은 동일한 습관 판정 로직을 사용한다.


#### 판정 결과별 처리

|결과|습관 상태|의미|
|---|---|---|
|`TOO_EARLY`|기존 상태 유지|그룹 가입 후 1주 미만|
|`ALREADY_FAILED`|`FAILED`|이미 실패한 습관|
|`GROUP_FINISHED`|기존 상태 유지|종료된 그룹|
|`BEFORE_DEADLINE`|기존 상태 유지|이번 판정 구간의 대상이 아님|
|`FIRST_WEEK_EXEMPT`|기존 상태 유지|최초 부분 주 판정 면제|
|`PASSED`|`ACTIVE`|목표 횟수 달성|
|`PENDING_REVIEW`|`ACTIVE`|검토 대기|
|`FAILED_INSUFFICIENT_SUBMISSION`|`FAILED`|목표 횟수 미달 및 벌칙 확정|

---

### 5.8. 관련 Enum

#### HabitStatus

|값|설명|
|---|---|
|`ACTIVE`|진행 중인 습관|
|`FAILED`|실패 처리된 습관|

#### WeeklyCheckResult

|값|설명|
|---|---|
|`TOO_EARLY`|가입 1주 미만|
|`ALREADY_FAILED`|이미 실패 처리된 습관|
|`GROUP_FINISHED`|종료된 그룹|
|`BEFORE_DEADLINE`|이번 판정 구간의 마감 전 대상 조건을 충족하지 못함|
|`FIRST_WEEK_EXEMPT`|최초 부분 주 판정 면제|
|`PASSED`|승인 횟수가 목표 이상|
|`PENDING_REVIEW`|미검토 인증 검토 대기|
|`FAILED_INSUFFICIENT_SUBMISSION`|승인 횟수 미달로 실패 및 벌칙 확정|

---

### 5.9. 구현 기준 참고 사항

- 습관 생성 시 활성 습관 중복 여부는 그룹 멤버 레코드에 대한 비관적 쓰기 잠금으로 보호한다.

- 습관 실패 처리와 주간 판정 모두 습관 레코드를 잠근 뒤 최신 상태를 확인하므로 동일 습관에 대한 동시 처리를 제어한다.

- 습관 상세 조회는 그룹 멤버 레코드의 존재 여부를 확인하지만, `ACTIVE` 상태인지는 검사하지 않는다.

- 그룹의 활성 습관 조회 및 실패 습관 목록 조회는 회원 ID와 그룹 ID를 기준으로 조회하며, 서비스 코드에 별도의 그룹 가입 권한 검사는 없다.

- 주간 판정은 습관 ID 오름차순으로 잠금을 획득해 여러 습관을 동시에 판정할 때 발생할 수 있는 교착을 방지한다.

- 판정 대상 ID를 조회한 이후 습관이 삭제되었다면 해당 습관은 판정 결과에서 제외된다.

---

## 6. 댓글 및 반응 공통 명세

습관 인증과 벌칙 인증은 하트·댓글 기능의 요청 및 응답 형식과 권한 규칙을 공유한다.

### 하트

|응답 필드|타입|설명|
|---|---|---|
|`reacted`|`Boolean`|내가 하트를 눌렀는지 여부|
|`reactionCount`|`Long`|하트 개수|

- 본인 인증에는 하트를 누를 수 없다.

- 하트 작성은 활동 중인 멤버가 진행 중인 방의 승인된 인증에 대해서만 가능하다.

- 하트 취소는 인증 상태가 변경된 뒤에도 가능하다.


### 댓글

#### 요청 필드

|필드|타입|제약|
|---|---|---|
|`content`|`String`|필수, 공백 불가, 최대 500자|

#### 응답 필드

|필드|타입|설명|
|---|---|---|
|`id`|`Long`|댓글 ID|
|`authorMemberId`|`Long?`|작성자 멤버 ID. 탈퇴한 멤버는 `null`|
|`authorNickname`|`String`|작성자 닉네임. 탈퇴한 멤버는 `탈퇴한 멤버`|
|`content`|`String`|댓글 내용|
|`createDate`|`LocalDateTime`|작성 일시|
|`edited`|`Boolean`|수정 여부|
|`mine`|`Boolean`|내가 작성한 댓글인지 여부|
|`canEdit`|`Boolean`|수정 가능 여부|
|`canDelete`|`Boolean`|삭제 가능 여부|

- 댓글 수정은 작성자만 가능하고, 삭제는 작성자 또는 방장만 가능하다.

- 댓글 작성은 활동 중인 멤버가 진행 중인 방의 승인된 인증에 대해서만 가능하다.

- 댓글 수정·삭제는 인증 상태가 변경된 뒤에도 가능하지만, 활동 상태 및 방 종료 여부에 따른 제한은 적용된다.


#### 댓글 목록 응답

|필드|타입|설명|
|---|---|---|
|`items`|`List<CommentResponse>`|댓글 목록|
|`nextCursor`|`Long?`|다음 페이지 커서. `null`이면 마지막 페이지|
|`totalCount`|`Long`|전체 댓글 개수|

- 기본 조회 크기: 30개

- 조회 크기 범위: 1~100개

- 다음 페이지 요청 시 이전 응답의 `nextCursor`를 `cursor`로 전달한다.

- `size` 범위를 벗어나거나 `cursor`가 음수이면 400 오류가 발생한다.


### 공통 권한 및 오류 규칙

|상황|HTTP 상태|
|---|---|
|방을 나간 멤버의 쓰기 요청|403|
|종료된 방에서 쓰기 요청|400|
|승인되지 않은 인증에 쓰기 요청|400|
|본인 인증에 하트 요청|400|
|댓글 내용 누락·공백 또는 500자 초과|400|
|잘못된 댓글 목록 조회 값|400|

---

## 7. 습관 인증 API

### 7.1. 공통 사항

- **Base URL:** `/api`

- **인증:** 모든 API에 로그인 필요

- **인증 방식:** `Authorization: Bearer {accessToken}`

- **공통 예외 응답:** 공통 예외 처리 규칙 참고


#### 습관 인증 상태 (`HabitVerifyStatus`)

|값|설명|
|---|---|
|`PENDING`|방장의 검토 대기|
|`APPROVED`|승인 완료|
|`REJECTED`|반려 완료|

#### 주요 처리 규칙

- 인증 날짜는 서버의 현재 날짜(`LocalDate.now()`)를 사용한다. 요청으로 인증 날짜를 지정하지 않는다.

- 동일한 습관에는 같은 날짜의 인증 기록을 중복 등록할 수 없다.

- 인증 등록 및 재제출에는 인증 사진 URL이 필요하다.

- 인증 설명은 선택 항목이며 최대 255자까지 입력할 수 있다.

- 사진 가로·세로 크기는 선택 항목이다. 전달하는 경우 두 값을 함께 보내야 하며, 각각 1~20,000 범위여야 한다.

- 인증 등록 시 최초 상태는 `PENDING`이다.

- 방장은 `PENDING` 상태의 인증만 승인하거나 반려할 수 있다.

- 인증 목록 및 댓글 읽기는 해당 인증이 속한 그룹의 멤버 여부를 기준으로 확인한다. 서비스 코드상 조회 시 멤버 상태가 반드시 `ACTIVE`여야 하는 것은 아니다.

- 댓글 요청·응답 DTO와 댓글 권한 규칙은 습관 인증과 벌칙 인증이 공유한다.


---

### 7.2. 습관 인증 사진 업로드용 서명 URL 발급

`**POST /api/storage/habits/{habitId}/upload-url**`

습관 인증 사진을 스토리지에 직접 업로드할 수 있도록 Signed URL을 발급한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`habitId`|Long|O|사진을 업로드할 습관 ID|

#### Request Body

요청 DTO: `UploadUrlRequest`

파일 이름을 포함한 요청 필드 및 검증 조건은 해당 DTO 정의를 따른다.

#### 허용 파일 확장자

- `jpg`

- `jpeg`

- `png`

- `webp`


#### Response

- **200 OK**

- 반환 타입: `UploadUrlResponse`


업로드에 필요한 서명 URL 및 관련 값은 `UploadUrlResponse` DTO에 정의된 필드로 반환한다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료되어 사진을 업로드할 수 없음|
|`403 Forbidden`|본인의 습관이 아님|
|`409 Conflict`|습관이 `ACTIVE` 상태가 아님|

#### 참고 사항

- 본인 소유의 습관에 대해서만 URL을 발급한다.

- 그룹이 종료되었거나 습관 상태가 `ACTIVE`가 아니면 발급할 수 없다.

- 서비스는 습관 소유 여부, 그룹 상태, 습관 상태를 확인한 후 스토리지 서비스에 URL 발급을 요청한다.

- 실제 파일 업로드는 발급받은 Signed URL을 사용해 스토리지에 직접 수행한다.


---

### 7.3. 습관 인증 등록

`**POST /api/habits/{habitId}/verifications**`

본인의 진행 중인 습관에 당일 인증 기록을 등록한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`habitId`|Long|O|인증을 등록할 습관 ID|

#### Request Body

```
{
  "description": "오늘 목표한 만큼 독서했습니다.",
  "imageUrl": "https://storage.example.com/verification.jpg",
  "imageWidth": 1200,
  "imageHeight": 1600
}
```

요청 DTO: `HabitVerifyRequest`

|필드|타입|필수|제약 조건|
|---|---|---|---|
|`description`|String?|X|최대 255자|
|`imageUrl`|String|O|공백이 아닌 값 필수|
|`imageWidth`|Int?|X|1~20,000|
|`imageHeight`|Int?|X|1~20,000|

#### 요청값 검증

|필드|조건|검증 실패 메시지|
|---|---|---|
|`description`|255자 초과|`습관 인증 설명은 255자 이하여야 합니다.`|
|`imageUrl`|누락, `null`, 빈 문자열 또는 공백만 입력|`인증 사진은 필수입니다.`|
|`imageWidth`|1 미만|`사진 가로 크기는 1 이상이어야 합니다.`|
|`imageWidth`|20,000 초과|`사진 가로 크기는 20000 이하여야 합니다.`|
|`imageHeight`|1 미만|`사진 세로 크기는 1 이상이어야 합니다.`|
|`imageHeight`|20,000 초과|`사진 세로 크기는 20000 이하여야 합니다.`|
|사진 크기|가로 또는 세로 중 하나만 전달|`사진의 가로와 세로 크기는 함께 보내야 합니다.`|

사진 크기를 전달하지 않으면 두 값 모두 `null`로 저장된다.

#### Response

- **201 Created**

- 반환 타입: `HabitVerifyResponse`


```
{
  "id": 200,
  "habitId": 100,
  "verifyDate": "2026-10-28",
  "status": "PENDING",
  "description": "오늘 목표한 만큼 독서했습니다.",
  "imageUrl": "https://storage.example.com/verification.jpg"
}
```

|필드|타입|설명|
|---|---|---|
|`id`|Long?|인증 ID|
|`habitId`|Long?|습관 ID|
|`verifyDate`|LocalDate|인증 날짜|
|`status`|HabitVerifyStatus|인증 상태|
|`description`|String?|인증 설명|
|`imageUrl`|String?|인증 사진 URL|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|요청값 검증 실패, 같은 날짜에 이미 인증 기록이 있음, 그룹 종료|
|`403 Forbidden`|본인의 습관이 아님|
|`409 Conflict`|습관이 `ACTIVE` 상태가 아님|

#### 참고 사항

- 본인이 소유한 습관에만 인증을 등록할 수 있다.

- 그룹이 종료되었거나 습관이 `ACTIVE` 상태가 아니면 인증을 등록할 수 없다.

- 같은 습관에 대해 하루 한 건만 등록할 수 있으며, 중복 여부는 `(habit_id, verify_date)` 유일성 제약으로도 보호한다.

- 사진 URL은 스토리지 서비스의 검증 절차를 거친다.

- 인증 등록 시 상태는 `PENDING`으로 시작한다.


---

### 7.4. 습관 인증 목록 조회

`**GET /api/habits/{habitId}/verifications**`

특정 습관에 등록된 전체 인증 기록을 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`habitId`|Long|O|인증 목록을 조회할 습관 ID|

#### Response

- **200 OK**

- 반환 타입: `List<HabitVerifyResponse>`


```
[
  {
    "id": 200,
    "habitId": 100,
    "verifyDate": "2026-10-28",
    "status": "PENDING",
    "description": "오늘 목표한 만큼 독서했습니다.",
    "imageUrl": "https://storage.example.com/verification.jpg"
  },
  {
    "id": 199,
    "habitId": 100,
    "verifyDate": "2026-10-27",
    "status": "APPROVED",
    "description": "독서 완료",
    "imageUrl": "https://storage.example.com/verification2.jpg"
  }
]
```

각 항목은 `HabitVerifyResponse` DTO를 따른다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|같은 그룹의 멤버가 아님|
|`404 Not Found`|존재하지 않는 습관|

#### 참고 사항

- 해당 습관이 속한 그룹의 멤버만 조회할 수 있다.

- 멤버 상태가 `ACTIVE`인지 별도로 검사하지 않고 그룹 멤버 레코드의 존재 여부를 확인한다.

- `PENDING`, `APPROVED`, `REJECTED` 상태의 인증을 모두 조회한다.

- 인증 날짜(`verifyDate`) 내림차순으로 반환한다.

- 기록이 없으면 빈 배열을 반환한다.


---

### 7.5. 반려된 습관 인증 재제출

`**PATCH /api/habits/{habitId}/verifications/{verificationId}/resubmit**`

방장에게 반려된 인증 내용을 수정해 다시 제출한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`habitId`|Long|O|인증이 속한 습관 ID|
|`verificationId`|Long|O|재제출할 인증 ID|

#### Request Body

5.3절의 `HabitVerifyRequest`와 동일하다.

```
{
  "description": "인증 내용을 보완했습니다.",
  "imageUrl": "https://storage.example.com/verification.jpg",
  "imageWidth": 1200,
  "imageHeight": 1600
}
```

사진 URL을 변경하지 않는 경우 기존에 저장된 URL을 그대로 전달할 수 있다.

#### Response

- **200 OK**

- 반환 타입: `HabitVerifyResponse`


응답 필드는 5.3절을 따른다. 재제출에 성공하면 인증 상태가 `PENDING`으로 변경된다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹 종료, 요청값 검증 실패|
|`403 Forbidden`|본인의 습관이 아님|
|`404 Not Found`|해당 습관 또는 해당 습관에 속한 인증 기록이 없음|
|`409 Conflict`|습관이 `ACTIVE` 상태가 아님, 인증 상태가 `REJECTED`가 아님, 제출 마감 경과|

#### 참고 사항

- 본인의 습관에 속한 인증만 재제출할 수 있다.

- 그룹이 종료되었거나 습관이 `ACTIVE` 상태가 아니면 재제출할 수 없다.

- 인증 기록은 URL의 `habitId`에 연결되어 있어야 한다.

- `REJECTED` 상태인 인증만 재제출할 수 있다.

- 인증이 속한 주간 구간의 마감 시각보다 현재 시각이 이전인 경우에만 재제출할 수 있다. 마감 시각과 같거나 이후라면 재제출할 수 없다.

- 재제출 시 인증 설명, 사진 URL, 사진 크기를 갱신하고 상태를 `PENDING`으로 변경한다.

- 사진 크기를 생략하면 기존 크기도 `null`로 변경된다.


---

### 7.6. 그룹 인증 사진 목록 조회

`**GET /api/groups/{groupId}/habits/verifications/photos**`

그룹 내 승인된 습관 인증 중 사진이 있는 기록을 최신순으로 조회한다. 그룹 메인 페이지의 인증 사진 갤러리에서 사용한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|

#### Query Parameters

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|`size`|Int|X|`60`|페이지 크기. 1~120|
|`cursor`|String|X|없음|다음 페이지 조회 커서|
|`memberId`|Long|X|없음|특정 회원의 인증만 조회하는 필터|
|`includeEngagement`|Boolean|X|`false`|하트·댓글 관련 필드 포함 여부|

#### 커서 형식

커서는 `인증 날짜:id` 형식의 문자열이다.

```
2026-10-26:123
```

응답의 `nextCursor` 값을 다음 요청의 `cursor`로 그대로 전달한다. `nextCursor`가 `null`이면 마지막 페이지다.

#### Response

- **200 OK**

- 반환 타입: `HabitVerifyPhotoPage`


```
{
  "items": [
    {
      "id": 200,
      "verifyDate": "2026-10-28",
      "habitTitle": "매일 독서하기",
      "memberId": 1,
      "memberNickname": "사용자1",
      "description": "오늘 목표한 만큼 독서했습니다.",
      "imageUrl": "https://storage.example.com/verification.jpg",
      "imageWidth": 1200,
      "imageHeight": 1600
    }
  ],
  "nextCursor": null
}
```

#### `HabitVerifyPhotoResponse` 필드

|필드|타입|설명|
|---|---|---|
|`id`|Long|인증 ID|
|`verifyDate`|LocalDate|인증 날짜|
|`habitTitle`|String|습관 제목|
|`memberId`|Long|인증을 등록한 회원 ID|
|`memberNickname`|String|인증을 등록한 회원의 닉네임|
|`description`|String?|인증 설명|
|`imageUrl`|String|인증 사진 URL|
|`imageWidth`|Int?|사진 가로 크기|
|`imageHeight`|Int?|사진 세로 크기|
|`reactionCount`|Long?|하트 수. `includeEngagement=true`일 때 포함|
|`commentCount`|Long?|댓글 수. `includeEngagement=true`일 때 포함|
|`reactedByMe`|Boolean?|현재 회원의 하트 여부. `includeEngagement=true`일 때 포함|

`reactionCount`, `commentCount`, `reactedByMe`는 `includeEngagement=true`일 때만 응답에 포함된다. 기본값인 `false`에서는 해당 필드 자체가 응답에 나타나지 않는다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|`size`가 1~120 범위를 벗어남, 잘못된 커서|
|`403 Forbidden`|해당 그룹의 멤버가 아님|
|`404 Not Found`|존재하지 않는 그룹|

#### 참고 사항

- 해당 그룹의 멤버만 조회할 수 있다. 현재 구현은 멤버 레코드의 존재 여부를 확인하며, `ACTIVE` 상태만 허용하는 별도 검사는 없다.

- 인증 상태가 `APPROVED`인 기록만 조회한다.

- 사진 URL이 `null`이거나 빈 문자열인 인증은 제외한다.

- 인증 날짜 내림차순, 같은 날짜에서는 인증 ID 내림차순으로 정렬한다.

- `memberId`를 전달하면 해당 회원이 등록한 인증으로 결과를 제한한다.

- `size`만큼 반환할 수 있는지 확인하기 위해 내부적으로 한 건 더 조회한다.

- `imageWidth`와 `imageHeight`가 `null`인 기존 기록도 반환될 수 있다.


---

### 7.7. 인증 하트 상태 조회

`**GET /api/habits/verifications/{verificationId}/reactions**`

특정 인증의 전체 하트 수와 현재 회원의 하트 여부를 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`verificationId`|Long|O|대상 인증 ID|

#### Response

- **200 OK**

- 반환 타입: `ReactionResponse`


|필드|타입|설명|
|---|---|---|
|`reacted`|Boolean|현재 회원이 하트를 눌렀는지 여부|
|`reactionCount`|Long|해당 인증의 전체 하트 수|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|해당 인증이 속한 그룹의 멤버가 아님|
|`404 Not Found`|존재하지 않는 인증|

#### 참고 사항

- 인증이 속한 그룹의 멤버라면 조회할 수 있다.

- 조회 시 그룹 멤버가 `ACTIVE` 상태인지 별도로 검사하지 않는다.


---

### 7.8. 인증에 하트 누르기

`**PUT /api/habits/verifications/{verificationId}/reactions**`

승인된 다른 회원의 인증에 하트를 누른 상태로 만든다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`verificationId`|Long|O|하트를 누를 인증 ID|

#### Request Body

없음

#### Response

- **200 OK**

- 반환 타입: `ReactionResponse`


|필드|타입|설명|
|---|---|---|
|`reacted`|Boolean|현재 회원의 하트 여부|
|`reactionCount`|Long|해당 인증의 전체 하트 수|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨, 인증 상태가 `APPROVED`가 아님, 본인 인증에 하트를 누르려는 경우|
|`403 Forbidden`|해당 그룹의 활동 중인 멤버가 아님|
|`404 Not Found`|존재하지 않는 인증|

#### 참고 사항

- 그룹의 활동 중인 멤버만 진행 중인 그룹에서 하트를 누를 수 있다.

- 인증 상태가 `APPROVED`인 경우에만 하트를 누를 수 있다.

- 본인의 인증에는 하트를 누를 수 없다.

- 이미 하트를 눌렀거나 동일한 요청이 동시에 여러 번 실행되어도 하트는 중복 생성되지 않는다.

- 인증과 그룹 멤버 조합에 유일성 제약이 있으며, 중복 삽입을 방지하는 방식으로 처리한다.


---

### 7.9. 인증에 누른 하트 취소

`**DELETE /api/habits/verifications/{verificationId}/reactions**`

현재 회원이 해당 인증에 눌러 둔 하트를 취소한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`verificationId`|Long|O|하트를 취소할 인증 ID|

#### Request Body

없음

#### Response

- **200 OK**

- 반환 타입: `ReactionResponse`


|필드|타입|설명|
|---|---|---|
|`reacted`|Boolean|취소 이후 현재 회원의 하트 여부|
|`reactionCount`|Long|취소 이후 해당 인증의 전체 하트 수|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨|
|`403 Forbidden`|해당 그룹의 활동 중인 멤버가 아님|
|`404 Not Found`|존재하지 않는 인증|

#### 참고 사항

- 그룹의 활동 중인 멤버만 진행 중인 그룹에서 하트를 취소할 수 있다.

- 취소할 하트가 없더라도 성공으로 처리한다.

- 취소 시 인증 상태가 `APPROVED`인지 확인하지 않는다. 따라서 기존 하트를 취소하는 작업은 신규 하트 등록과 달리 승인 상태를 요구하지 않는다.


---

### 7.10. 인증 댓글 목록 조회

`**GET /api/habits/verifications/{verificationId}/comments**`

특정 인증에 작성된 댓글을 오래된 순서대로 조회한다. 커서 기반 페이지네이션을 사용한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`verificationId`|Long|O|댓글을 조회할 인증 ID|

#### Query Parameters

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|`cursor`|Long|X|없음|마지막으로 받은 댓글 ID. 0 이상|
|`size`|Int|X|`30`|페이지 크기. 1~100|

#### Response

- **200 OK**

- 반환 타입: `CommentPage`


```
{
  "items": [
    {
      "id": 301,
      "authorMemberId": 1,
      "authorNickname": "사용자1",
      "content": "꾸준히 실천하는 모습이 좋네요!",
      "createDate": "2026-10-28T10:00:00",
      "edited": false,
      "mine": false,
      "canEdit": false,
      "canDelete": false
    },
    {
      "id": 302,
      "authorMemberId": null,
      "authorNickname": "탈퇴한 멤버",
      "content": "오늘도 인증 완료!",
      "createDate": "2026-10-28T10:05:00",
      "edited": true,
      "mine": false,
      "canEdit": false,
      "canDelete": false
    }
  ],
  "nextCursor": null,
  "totalCount": 2
}
```

위 JSON은 응답 구조를 설명하기 위한 예시다.

#### `CommentResponse` 필드

|필드|타입|설명|
|---|---|---|
|`id`|Long|댓글 ID|
|`authorMemberId`|Long?|작성자 회원 ID. 탈퇴한 멤버는 `null`|
|`authorNickname`|String|작성자 닉네임. 탈퇴한 멤버는 `탈퇴한 멤버`|
|`content`|String|댓글 내용|
|`createDate`|LocalDateTime|댓글 작성 일시|
|`edited`|Boolean|수정 일시가 작성 일시보다 뒤라면 `true`|
|`mine`|Boolean|현재 회원이 작성한 댓글인지 여부|
|`canEdit`|Boolean|현재 회원이 작성자인지 여부|
|`canDelete`|Boolean|현재 회원이 작성자이거나 방장인지 여부|

#### `CommentPage` 필드

|필드|타입|설명|
|---|---|---|
|`items`|List<CommentResponse>|댓글 목록|
|`nextCursor`|Long?|다음 페이지 조회 커서. `null`이면 마지막 페이지|
|`totalCount`|Long|해당 인증의 전체 댓글 수|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|`size`가 1~100 범위를 벗어남, `cursor`가 0 미만|
|`403 Forbidden`|해당 인증이 속한 그룹의 멤버가 아님|
|`404 Not Found`|존재하지 않는 인증|

#### 참고 사항

- 인증이 속한 그룹의 멤버만 조회할 수 있다. 그룹에서 탈퇴한 멤버도 그룹 멤버 레코드가 보존되어 있다면 댓글을 조회할 수 있다.

- 댓글은 ID 오름차순으로 조회한다.

- `cursor`에는 이전 응답의 `nextCursor` 값을 그대로 전달한다.

- 다음 페이지 존재 여부를 확인하기 위해 내부적으로 요청 크기보다 한 건 더 조회한다.

- `nextCursor`는 다음 페이지가 있을 때 현재 페이지의 마지막 댓글 ID로 반환한다.

- `totalCount`는 페이지의 댓글 수가 아니라 해당 인증에 작성된 전체 댓글 수다.

- 탈퇴한 멤버가 작성한 댓글은 `authorMemberId`를 `null`로 반환하고 `authorNickname`을 `탈퇴한 멤버`로 표시한다.

- `edited`는 `modifyDate`가 `createDate`보다 뒤일 때 `true`다.

- `mine`은 현재 회원이 해당 댓글의 작성자인지 여부를 나타낸다. 탈퇴한 멤버가 작성한 댓글은 `mine`이 `false`다.

- `canEdit`은 작성자 여부, `canDelete`는 작성자 또는 방장 여부를 기준으로 응답한다. 실제 쓰기 요청에서는 서버가 별도로 권한과 그룹 상태를 검증한다.


---

### 7.11. 인증 댓글 작성

`**POST /api/habits/verifications/{verificationId}/comments**`

진행 중인 그룹의 승인된 인증에 댓글을 작성한다. 본인의 인증에도 댓글을 작성할 수 있다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`verificationId`|Long|O|댓글을 작성할 인증 ID|

#### Request Body

요청 DTO: `CommentRequest`

```
{
  "content": "꾸준히 실천하는 모습이 좋네요!"
}
```

|필드|타입|필수|검증 조건|
|---|---|---|---|
|`content`|String|O|공백이 아닌 값 필수, 최대 500자|

**검증 실패 메시지**

|조건|메시지|
|---|---|
|내용 누락 또는 공백만 입력|`댓글 내용을 입력해주세요.`|
|500자 초과|`댓글은 500자 이하여야 합니다.`|

#### Response

- **201 Created**

- 반환 타입: `CommentResponse`


작성한 댓글의 필드는 5.10절의 `CommentResponse`를 따른다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨, 인증이 승인 상태가 아님, 댓글 내용 검증 실패|
|`403 Forbidden`|해당 그룹의 활동 중인 멤버가 아님|
|`404 Not Found`|존재하지 않는 인증|

#### 참고 사항

- 그룹의 활동 중인 멤버만 진행 중인 그룹에서 댓글을 작성할 수 있다.

- 댓글을 작성하려는 인증은 `APPROVED` 상태여야 한다.

- 하트와 달리 본인의 인증에도 댓글을 작성할 수 있다.

- 댓글 내용은 앞뒤 공백을 제거한 후 저장한다.

- 작성자에게 반환되는 `mine`, `canEdit`, `canDelete`는 모두 `true`가 될 수 있다. 실제 반환값은 응답 생성 규칙에 따른다.


---

### 7.12. 인증 댓글 수정

`**PATCH /api/habits/verifications/{verificationId}/comments/{commentId}**`

현재 회원이 작성한 댓글의 내용을 수정한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`verificationId`|Long|O|댓글이 속한 인증 ID|
|`commentId`|Long|O|수정할 댓글 ID|

#### Request Body

5.11절의 `CommentRequest`와 동일하다.

```
{
  "content": "댓글 내용을 수정했습니다."
}
```

#### Response

- **200 OK**

- 반환 타입: `CommentResponse`


수정한 댓글의 필드는 5.10절을 따른다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨, 댓글 내용 검증 실패|
|`403 Forbidden`|해당 그룹의 활동 중인 멤버가 아님, 본인이 작성한 댓글이 아님|
|`404 Not Found`|인증 또는 해당 인증에 속한 댓글이 존재하지 않음|

#### 참고 사항

- 작성자 본인만 댓글을 수정할 수 있다.

- 그룹의 활동 중인 멤버만 진행 중인 그룹에서 수정할 수 있다.

- 인증 상태가 `APPROVED`인지 확인하지 않으므로, 인증 상태가 변경되었더라도 다른 수정 조건을 충족하면 수정할 수 있다.

- 댓글 수정 시 수정 일시가 반영되도록 처리한다.

- 응답의 `edited`는 `modifyDate`가 `createDate`보다 뒤인 경우 `true`다.


---

### 7.13. 인증 댓글 삭제

`**DELETE /api/habits/verifications/{verificationId}/comments/{commentId}**`

댓글 작성자 본인 또는 그룹 방장이 댓글을 삭제한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`verificationId`|Long|O|댓글이 속한 인증 ID|
|`commentId`|Long|O|삭제할 댓글 ID|

#### Request Body

없음

#### Response

- **204 No Content**

- 응답 본문 없음


#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨|
|`403 Forbidden`|해당 그룹의 활동 중인 멤버가 아니거나 댓글 삭제 권한이 없음|
|`404 Not Found`|인증 또는 해당 인증에 속한 댓글이 존재하지 않음|

#### 참고 사항

- 댓글 작성자 본인 또는 방장만 삭제할 수 있다.

- 삭제하려면 그룹의 활동 중인 멤버여야 하며 그룹도 진행 중이어야 한다.

- 인증이 `APPROVED` 상태인지 별도로 확인하지 않는다.

- 댓글을 삭제하면 해당 댓글 레코드가 삭제된다.


---

### 7.14. 그룹 검토 대기 인증 목록 조회

`**GET /api/groups/{groupId}/habits/verifications/pending**`

특정 그룹에서 방장의 검토를 기다리는 `PENDING` 상태의 인증 목록을 조회한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|

#### Response

- **200 OK**

- 반환 타입: `List<HabitVerifySummaryResponse>`


```
[
  {
    "id": 200,
    "verifyDate": "2026-10-28",
    "habitTitle": "매일 독서하기",
    "memberNickname": "사용자1",
    "description": "오늘 목표한 만큼 독서했습니다.",
    "imageUrl": "https://storage.example.com/verification.jpg",
    "status": "PENDING"
  }
]
```

|필드|타입|설명|
|---|---|---|
|`id`|Long?|인증 ID|
|`verifyDate`|LocalDate|인증 날짜|
|`habitTitle`|String|습관 제목|
|`memberNickname`|String|인증 제출 회원의 닉네임|
|`description`|String?|인증 설명|
|`imageUrl`|String?|인증 사진 URL|
|`status`|HabitVerifyStatus|인증 상태|

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|해당 그룹의 멤버가 아니거나 방장이 아님|

#### 참고 사항

- 해당 그룹의 멤버인지 확인한 뒤 역할이 `OWNER`인지 검증한다.

- `PENDING` 상태의 인증만 조회한다.

- 인증 날짜 내림차순으로 반환한다.

- 현재 서비스 구현에는 그룹 종료 여부를 검사하는 로직이 없다. 따라서 이 API의 접근 가능 여부와 실제 승인·반려 가능 여부는 다를 수 있다.

- 방장 권한 확인에서 멤버 상태가 `ACTIVE`인지 별도로 검사하지 않는다.


---

### 7.15. 단건 인증 승인

`**PATCH /api/habits/verifications/{id}/approve**`

방장이 특정 습관 인증을 승인한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`id`|Long|O|승인할 인증 ID|

#### Response

- **200 OK**

- 반환 타입: `HabitVerifyResponse`


승인된 인증의 상태는 `APPROVED`다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨|
|`403 Forbidden`|방장 승인 권한이 없음|
|`404 Not Found`|존재하지 않는 인증|
|`409 Conflict`|인증이 `PENDING` 상태가 아님|

#### 참고 사항

- 인증이 속한 그룹의 방장만 승인할 수 있다.

- 그룹이 종료되었다면 승인할 수 없다.

- `PENDING` 상태에서만 승인할 수 있으며 이미 승인되었거나 반려된 인증은 다시 승인할 수 없다.

- 승인된 인증은 주간 정산 시 승인 횟수에 포함될 수 있다.


---

### 7.16. 단건 인증 반려

`**PATCH /api/habits/verifications/{id}/reject**`

방장이 특정 습관 인증을 반려한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`id`|Long|O|반려할 인증 ID|

#### Response

- **200 OK**

- 반환 타입: `HabitVerifyResponse`


반려된 인증의 상태는 `REJECTED`다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨|
|`403 Forbidden`|방장 반려 권한이 없음|
|`404 Not Found`|존재하지 않는 인증|
|`409 Conflict`|인증이 `PENDING` 상태가 아님|

#### 참고 사항

- 인증이 속한 그룹의 방장만 반려할 수 있다.

- 그룹이 종료되었다면 반려할 수 없다.

- `PENDING` 상태에서만 반려할 수 있다.

- 반려된 인증은 `REJECTED` 상태가 되며 주간 정산의 승인 횟수에 포함되지 않는다.

- 인증 제출 회원은 정해진 주간 구간의 마감 전까지 반려된 인증을 재제출할 수 있다.


---

### 7.17. 인증 목록 일괄 승인

`**PATCH /api/groups/{groupId}/habits/verifications/bulk-approve**`

특정 그룹에 속한 여러 인증을 한 번에 승인한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|

#### Request Body

```
{
  "ids": [200, 201, 202]
}
```

요청 DTO: `BulkActionRequest`

|필드|타입|설명|
|---|---|---|
|`ids`|List<Long>|승인 처리할 인증 ID 목록|

#### Response

- **200 OK**

- 반환 타입: `List<HabitVerifyResponse>`


각 항목은 승인 처리 후의 인증 정보를 반환한다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨|
|`403 Forbidden`|방장 승인 권한이 없음|
|`404 Not Found`|없음. 요청 ID 중 존재하지 않거나 다른 그룹에 속한 인증은 조회 결과에서 제외될 수 있음|
|`409 Conflict`|처리 대상 중 `PENDING` 상태가 아닌 인증이 포함됨|

#### 참고 사항

- 호출 회원이 해당 그룹의 방장인지 확인한 후 처리한다.

- 그룹이 종료되었다면 일괄 승인할 수 없다.

- 요청 ID 중 해당 그룹에 속하는 인증만 조회해 처리한다.

- 요청에 포함된 ID 중 존재하지 않거나 다른 그룹에 속한 ID는 조회 결과에서 제외된다.

- 각 인증은 `PENDING` 상태여야 한다. 처리 대상 인증 중 하나라도 상태가 맞지 않으면 예외가 발생하고 트랜잭션의 변경 사항은 롤백된다.

- 실제 응답 순서는 요청의 ID 순서와 같다고 보장되지 않는다.


---

### 7.18. 인증 목록 일괄 반려

`**PATCH /api/groups/{groupId}/habits/verifications/bulk-reject**`

특정 그룹에 속한 여러 인증을 한 번에 반려한다.

#### Path Parameters

|이름|타입|필수|설명|
|---|---|---|---|
|`groupId`|Long|O|그룹 ID|

#### Request Body

5.17절과 동일하다.

```
{
  "ids": [200, 201, 202]
}
```

#### Response

- **200 OK**

- 반환 타입: `List<HabitVerifyResponse>`


각 항목은 반려 처리 후의 인증 정보를 반환한다.

#### 주요 예외

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹이 종료됨|
|`403 Forbidden`|방장 반려 권한이 없음|
|`409 Conflict`|처리 대상 중 `PENDING` 상태가 아닌 인증이 포함됨|

#### 참고 사항

- 호출 회원이 해당 그룹의 방장인지 확인한 후 처리한다.

- 그룹이 종료되었다면 일괄 반려할 수 없다.

- 요청 ID 중 해당 그룹에 속하는 인증만 조회해 처리한다.

- 존재하지 않거나 다른 그룹에 속한 ID는 조회 결과에서 제외된다.

- 각 인증은 `PENDING` 상태여야 한다. 처리 대상 인증 중 하나라도 상태가 맞지 않으면 예외가 발생하고 트랜잭션의 변경 사항은 롤백된다.

- 실제 응답 순서는 요청의 ID 순서와 같다고 보장되지 않는다.


---

### 7.19. 관련 Enum

#### HabitVerifyStatus

|값|설명|
|---|---|
|`PENDING`|방장의 검토 대기|
|`APPROVED`|승인 완료|
|`REJECTED`|반려 완료|

---

### 7.20. 주요 동작 및 권한 요약

|기능|권한 및 조건|주요 처리|
|---|---|---|
|사진 업로드 URL 발급|본인 소유의 `ACTIVE` 습관, 진행 중인 그룹|Signed URL 발급|
|인증 등록|본인 소유의 `ACTIVE` 습관, 진행 중인 그룹, 당일 중복 인증 없음|`PENDING` 인증 생성|
|인증 목록 조회|인증이 속한 그룹의 멤버|모든 상태의 인증을 날짜 내림차순으로 조회|
|인증 재제출|본인의 `ACTIVE` 습관, 진행 중인 그룹, `REJECTED` 상태, 마감 전|내용 갱신 후 `PENDING`으로 변경|
|사진 갤러리 조회|그룹 멤버|사진이 있는 `APPROVED` 인증을 최신순 조회|
|하트 조회|그룹 멤버|하트 수와 본인의 하트 여부 반환|
|하트 등록|활동 중인 멤버, 진행 중인 그룹, 승인된 다른 회원의 인증|중복 없이 하트 등록|
|하트 취소|활동 중인 멤버, 진행 중인 그룹|하트 레코드 삭제. 하트가 없어도 성공|
|댓글 조회|그룹 멤버|탈퇴 멤버도 기록이 남아 있으면 조회 가능|
|댓글 작성|활동 중인 멤버, 진행 중인 그룹, 승인된 인증|댓글 생성|
|댓글 수정|활동 중인 멤버, 진행 중인 그룹, 작성자 본인|댓글 내용 수정|
|댓글 삭제|활동 중인 멤버, 진행 중인 그룹, 작성자 본인 또는 방장|댓글 삭제|
|검토 대기 목록 조회|방장 역할|`PENDING` 인증 목록 조회|
|단건 승인·반려|방장, 진행 중인 그룹, `PENDING` 인증|인증 상태 변경|
|일괄 승인·반려|방장, 진행 중인 그룹, 대상 인증이 `PENDING` 상태|그룹에 속한 요청 인증들을 일괄 처리|

#### 구현상 주의 사항

- `CommentResponse`의 `canEdit` 및 `canDelete`는 작성자·방장 여부를 나타내는 표시 값이다. 실제 수정·삭제 요청에서는 서버가 활동 상태와 그룹 종료 여부를 다시 검증한다.

- 댓글 작성은 승인된 인증에서만 가능하지만, 댓글 수정·삭제는 인증 상태가 변경된 뒤에도 가능하다. 단, 그룹이 진행 중이고 요청 회원이 활동 중이어야 한다.

- 댓글 작성자는 수정할 수 있지만, 방장은 다른 멤버의 댓글을 수정할 수 없다. 방장은 다른 멤버의 댓글을 삭제할 수 있다.

- 방장의 인증 검토 권한 확인 코드는 그룹 멤버 레코드와 `OWNER` 역할을 검사한다. 멤버 상태가 `ACTIVE`인지 별도로 검사하지 않는다.

- 검토 대기 목록 조회에는 그룹 종료 여부 검사가 없지만, 단건 및 일괄 승인·반려에는 종료 여부 검사가 있다.

---

## 8. 벌칙 인증 API

### 8.1. 공통 사항

- **Base URL:** `/api`

- **인증:** 로그인 필요 (`Authorization: Bearer {accessToken}`)

- **공통 예외 응답:** 공통 예외 처리 규칙 참고


#### 벌칙 인증 상태 (`PenaltyVerifyStatus`)

|값|설명|
|---|---|
|`REQUIRED`|벌칙 제출 필요|
|`PENDING`|제출 완료, 검토 대기|
|`APPROVED`|승인 완료|
|`REJECTED`|반려 완료|

벌칙 기록은 정산 이력 보존을 위해 하드 삭제하지 않는다. 제출 취소 또는 방장 초기화 시 제출일·설명·사진 URL을 비우고 `REQUIRED`로 되돌린다. 습관 이름과 벌칙 문구는 생성 시점의 스냅샷을 유지한다.

### 8.2. 공통 DTO

#### SubmitPenaltyVerifyRequest

벌칙 인증 등록 및 재제출에 사용한다.

|필드|타입|필수|제약 조건|
|---|---|---|---|
|`description`|String?|X|최대 255자|
|`imageUrl`|String?|O|공백이 아닌 값 필수|

검증 실패 메시지: `벌칙 인증 설명은 255자 이하여야 합니다.`, `인증 사진은 필수입니다.`

#### PenaltyVerifyDetailResponse

|필드|타입|설명|
|---|---|---|
|`id`|Long?|벌칙 인증 ID|
|`verifyDate`|LocalDate?|제출일|
|`habitTitle`|String|습관 이름 스냅샷|
|`penaltyText`|String|벌칙 문구 스냅샷|
|`description`|String?|제출 설명|
|`imageUrl`|String?|제출 사진 URL|
|`status`|PenaltyVerifyStatus|벌칙 인증 상태|

#### PenaltyVerifySummaryResponse

|필드|타입|설명|
|---|---|---|
|`id`|Long?|벌칙 인증 ID|
|`habitId`|Long?|습관 ID|
|`verifyDate`|LocalDate?|제출일|
|`habitTitle`|String|습관 이름 스냅샷|
|`description`|String?|제출 설명|
|`memberNickname`|String?|대상 회원 닉네임|
|`imageUrl`|String?|제출 사진 URL|
|`penaltyText`|String|벌칙 문구 스냅샷|
|`status`|PenaltyVerifyStatus|벌칙 인증 상태|

#### BulkActionRequest

|필드|타입|설명|
|---|---|---|
|`ids`|List<Long>|일괄 처리할 벌칙 인증 ID 목록|

### 8.3. 벌칙 인증 등록

`**POST /api/habits/{habitId}/penalties**`

본인의 습관에 부과된 벌칙을 제출한다.

- **Path Parameter:** `habitId` — 벌칙이 부과된 습관 ID

- **Request Body:** `SubmitPenaltyVerifyRequest`

- **Response:** `201 Created`, `PenaltyVerifyDetailResponse`


처리 조건은 본인 소유의 벌칙이고 그룹이 진행 중이며 상태가 `REQUIRED`인 것이다. 성공하면 제출일을 현재 날짜로 기록하고 상태를 `PENDING`으로 변경한다.

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|요청값 검증 실패, 그룹 종료|
|`403 Forbidden`|본인의 습관에 대한 벌칙이 아님|
|`409 Conflict`|벌칙 상태가 `REQUIRED`가 아님|

### 8.4. 반려된 벌칙 인증 재제출

`**PATCH /api/penalties/{id}/resubmit**`

- **Path Parameter:** `id` — 벌칙 인증 ID

- **Request Body:** `SubmitPenaltyVerifyRequest`

- **Response:** `200 OK`, `PenaltyVerifyDetailResponse`


본인의 벌칙 중 `REJECTED` 상태인 기록만 재제출할 수 있다. 그룹이 진행 중이어야 하며, 성공하면 제출일을 갱신하고 상태를 `PENDING`으로 변경한다.

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|요청값 검증 실패, 그룹 종료|
|`403 Forbidden`|본인의 벌칙이 아님|
|`409 Conflict`|상태가 `REJECTED`가 아님|

### 8.5. 벌칙 인증 사진 업로드용 서명 URL 발급

`**POST /api/penalties/{id}/upload-url**`

- **Path Parameter:** `id` — 벌칙 인증 ID

- **Request Body:** `UploadUrlRequest`

- **Response:** `200 OK`, `UploadUrlResponse`


본인의 벌칙이며 그룹이 진행 중이고 상태가 `REQUIRED` 또는 `REJECTED`일 때 발급한다. 허용 확장자는 `jpg`, `jpeg`, `png`, `webp`다.

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹 종료|
|`403 Forbidden`|본인의 벌칙이 아님|
|`409 Conflict`|제출 또는 재제출 가능한 상태가 아님|

### 8.6. 벌칙 인증 상세 조회

`**GET /api/penalties/{id}**`

- **Path Parameter:** `id` — 벌칙 인증 ID

- **Response:** `200 OK`, `PenaltyVerifyDetailResponse`


해당 벌칙이 속한 그룹의 멤버만 조회할 수 있다. 멤버 상태가 `ACTIVE`인지 별도로 검사하지 않는다.

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|해당 그룹의 멤버가 아님|
|`404 Not Found`|존재하지 않는 벌칙 인증|

### 8.7. 그룹 내 누적 벌칙 개수 조회

`**GET /api/groups/{groupId}/penalties/count**`

- **Path Parameter:** `groupId` — 그룹 ID

- **Response:** `200 OK`


응답 예시:

```
{
  "count": 3
}
```

현재 회원에게 해당 그룹에서 부여된 벌칙 레코드 개수를 반환한다. 상태 필터가 없으므로 모든 상태가 집계 대상이며, 초기화 후 `REQUIRED` 상태가 된 기록도 포함된다.

현재 서비스 구현에는 그룹 가입 여부를 별도로 검사하는 로직이 없다.

### 8.8. 특정 멤버의 벌칙 목록 조회

`**GET /api/groups/{groupId}/members/{groupMemberId}/penalties**`

- **Path Parameters:**

    - `groupId` — 그룹 ID

    - `groupMemberId` — 조회 대상 그룹 멤버 ID

- **Response:** `200 OK`, `List<PenaltyVerifySummaryResponse>`


호출 회원이 그룹에 속하는지, 조회 대상 멤버가 해당 그룹에 속하는지 확인한다. 대상 멤버의 전체 벌칙 기록을 ID 내림차순으로 반환하며 상태별 필터는 없다.

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|호출 회원이 해당 그룹의 멤버가 아님|
|`400 Bad Request`|조회 대상 멤버가 해당 그룹에 속하지 않음|

### 8.9. 단건 벌칙 인증 승인·반려

|기능|Method 및 경로|응답|
|---|---|---|
|승인|`PATCH /api/penalties/{id}/approve`|`200 OK`, `PenaltyVerifyDetailResponse`|
|반려|`PATCH /api/penalties/{id}/reject`|`200 OK`, `PenaltyVerifyDetailResponse`|

두 API 모두 해당 그룹의 방장만 실행할 수 있다. 그룹이 진행 중이고 벌칙 인증이 `PENDING` 상태여야 한다.

승인 시 상태를 `APPROVED`, 반려 시 `REJECTED`로 변경한다.

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹 종료|
|`403 Forbidden`|방장이 아님|
|`409 Conflict`|벌칙 인증 상태가 `PENDING`이 아님|

### 8.10. 검토 대기 벌칙 목록 조회

`**GET /api/groups/{groupId}/penalties/pending**`

- **Path Parameter:** `groupId` — 그룹 ID

- **Response:** `200 OK`, `List<PenaltyVerifySummaryResponse>`


방장 역할을 가진 회원에게 해당 그룹의 `PENDING` 상태 벌칙 목록을 반환한다.

|HTTP 상태|발생 조건|
|---|---|
|`403 Forbidden`|해당 그룹의 멤버가 아니거나 방장이 아님|

현재 구현에는 그룹 종료 여부 및 멤버 `ACTIVE` 상태를 별도로 검사하는 로직이 없다. 응답 정렬 순서도 보장되지 않는다.

### 8.11. 벌칙 목록 일괄 승인·반려

|기능|Method 및 경로|응답|
|---|---|---|
|일괄 승인|`PATCH /api/groups/{groupId}/penalties/bulk-approve`|`200 OK`, `List<PenaltyVerifyDetailResponse>`|
|일괄 반려|`PATCH /api/groups/{groupId}/penalties/bulk-reject`|`200 OK`, `List<PenaltyVerifyDetailResponse>`|

- **Path Parameter:** `groupId` — 그룹 ID

- **Request Body:** `BulkActionRequest`


요청 예시:

```
{
  "ids": [300, 301, 302]
}
```

방장만 진행 중인 그룹에서 실행할 수 있으며, 처리 대상 인증은 `PENDING` 상태여야 한다. 승인·반려 결과는 각각 `APPROVED`, `REJECTED`다.

| HTTP 상태           | 발생 조건                           |
| ----------------- | ------------------------------- |
| `400 Bad Request` | 그룹 종료                           |
| `403 Forbidden`   | 방장이 아님                          |
| `409 Conflict`    | 처리 대상 중 `PENDING` 상태가 아닌 인증이 있음 |

요청 ID 중 존재하지 않거나 다른 그룹에 속한 ID는 조회 결과에서 제외될 수 있다. 처리 도중 상태 검증에 실패하면 트랜잭션 변경 사항은 롤백된다. 요청 ID 순서와 응답 순서는 같다고 보장되지 않는다.

### 8.12. 벌칙 인증 제출 취소 및 초기화

`**PATCH /api/penalties/{id}/cancel**`

- **Path Parameter:** `id` — 벌칙 인증 ID

- **Response:** `200 OK`, `PenaltyVerifyDetailResponse`


작성자는 `PENDING` 상태의 본인 벌칙만 제출 취소할 수 있다. 방장은 본인의 벌칙을 포함해 `REQUIRED`가 아닌 기록을 초기화할 수 있다. 그룹이 종료된 경우 실행할 수 없다.

|호출자|허용 상태|처리|
|---|---|---|
|작성자이며 방장이 아님|`PENDING`|제출 취소|
|방장|`PENDING`, `APPROVED`, `REJECTED`|초기화|
|그 외 회원|없음|거부|

성공하면 `verifyDate`, `description`, `imageUrl`을 `null`로 바꾸고 상태를 `REQUIRED`로 변경한다. 벌칙 레코드와 스냅샷은 유지한다. 기존 하트와 댓글은 같은 트랜잭션에서 삭제한다.

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹 종료|
|`403 Forbidden`|작성자도 방장도 아님|
|`409 Conflict`|작성자가 `PENDING` 이외 상태를 취소하거나 방장이 `REQUIRED` 상태를 초기화하려는 경우|

### 8.13. 벌칙 인증 하트

|기능|Method 및 경로|응답|
|---|---|---|
|하트 상태 조회|`GET /api/penalties/{id}/reactions`|`200 OK`, `ReactionResponse`|
|하트 누르기|`PUT /api/penalties/{id}/reactions`|`200 OK`, `ReactionResponse`|
|하트 취소|`DELETE /api/penalties/{id}/reactions`|`200 OK`, `ReactionResponse`|

모든 경로의 `id`는 벌칙 인증 ID다.

#### ReactionResponse

|필드|타입|설명|
|---|---|---|
|`reacted`|Boolean|현재 회원이 하트를 눌렀는지 여부|
|`reactionCount`|Long|전체 하트 수|

- **조회:** 해당 그룹의 멤버가 조회할 수 있다.

- **등록:** 활동 중인 멤버가 진행 중인 그룹에서 승인된 다른 회원의 벌칙 인증에만 등록할 수 있다.

- **취소:** 활동 중인 멤버가 진행 중인 그룹에서 취소할 수 있으며, 하트가 없어도 성공한다. 취소 시 인증의 `APPROVED` 상태는 요구하지 않는다.


|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|그룹 종료, 등록 시 인증 미승인 또는 본인 인증에 하트를 누르려는 경우|
|`403 Forbidden`|그룹 멤버가 아니거나 활동 중인 멤버가 아님|
|`404 Not Found`|존재하지 않는 벌칙 인증|

하트 등록은 같은 회원이 중복 요청하거나 동시에 요청해도 중복 생성되지 않는다.

### 8.14. 벌칙 인증 댓글

벌칙 인증 댓글은 습관 인증과 동일한 DTO 및 `EngagementRules`를 사용한다.

#### CommentRequest

|필드|타입|필수|제약 조건|
|---|---|---|---|
|`content`|String?|O|공백이 아닌 값, 최대 500자|

검증 실패 메시지: `댓글 내용을 입력해주세요.`, `댓글은 500자 이하여야 합니다.`

#### CommentResponse

|필드|타입|설명|
|---|---|---|
|`id`|Long|댓글 ID|
|`authorMemberId`|Long?|작성자 회원 ID. 탈퇴 멤버는 `null`|
|`authorNickname`|String|작성자 닉네임. 탈퇴 멤버는 `탈퇴한 멤버`|
|`content`|String|댓글 내용|
|`createDate`|LocalDateTime|작성 일시|
|`edited`|Boolean|수정 일시가 작성 일시보다 뒤인지 여부|
|`mine`|Boolean|현재 회원이 작성했는지 여부|
|`canEdit`|Boolean|현재 회원이 작성자인지 여부|
|`canDelete`|Boolean|현재 회원이 작성자이거나 방장인지 여부|

#### CommentPage

|필드|타입|설명|
|---|---|---|
|`items`|List<CommentResponse>|댓글 목록|
|`nextCursor`|Long?|다음 페이지 커서. `null`이면 마지막 페이지|
|`totalCount`|Long|전체 댓글 수|

#### 댓글 API

|기능|Method 및 경로|응답|
|---|---|---|
|목록 조회|`GET /api/penalties/{id}/comments`|`200 OK`, `CommentPage`|
|작성|`POST /api/penalties/{id}/comments`|`201 Created`, `CommentResponse`|
|수정|`PATCH /api/penalties/{id}/comments/{commentId}`|`200 OK`, `CommentResponse`|
|삭제|`DELETE /api/penalties/{id}/comments/{commentId}`|`204 No Content`, 본문 없음|

`id`는 벌칙 인증 ID이며, 수정·삭제 경로에는 댓글 ID인 `commentId`가 추가된다.

#### 목록 조회 Query Parameters

|이름|타입|필수|기본값|설명|
|---|---|---|---|---|
|`cursor`|Long|X|없음|마지막으로 받은 댓글 ID. 0 이상|
|`size`|Int|X|`30`|페이지 크기. 1~100|

댓글은 ID 오름차순으로 조회한다. 다음 요청에는 이전 응답의 `nextCursor`를 그대로 전달하며, `totalCount`는 전체 댓글 수다.

#### 권한 및 처리 규칙

|기능|조건|
|---|---|
|목록 조회|그룹 멤버. 탈퇴 멤버도 기록이 보존되어 있으면 조회 가능|
|작성|활동 중인 멤버, 진행 중인 그룹, `APPROVED` 상태의 벌칙 인증|
|수정|활동 중인 멤버, 진행 중인 그룹, 댓글 작성자 본인|
|삭제|활동 중인 멤버, 진행 중인 그룹, 작성자 본인 또는 방장|

본인 벌칙 인증에도 댓글을 작성할 수 있다. 댓글 내용은 앞뒤 공백을 제거한 후 저장한다. 수정·삭제는 인증의 현재 `APPROVED` 상태를 요구하지 않지만, 그룹이 진행 중이고 호출 회원이 활동 중이어야 한다.

`canEdit`과 `canDelete`는 응답에 표시되는 권한 정보이며, 실제 쓰기 요청에서는 서버가 조건을 다시 검증한다.

|HTTP 상태|발생 조건|
|---|---|
|`400 Bad Request`|페이지 크기·커서 오류, 댓글 내용 검증 실패, 그룹 종료 또는 작성 대상 인증 미승인|
|`403 Forbidden`|그룹 멤버가 아니거나 활동 중인 멤버가 아님, 수정·삭제 권한 없음|
|`404 Not Found`|벌칙 인증 또는 해당 인증에 속한 댓글이 없음|