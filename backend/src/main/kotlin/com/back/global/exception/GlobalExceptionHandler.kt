package com.back.global.exception

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
// 우리 응답 DTO(ErrorResponse)와 이름이 같아 별칭을 쓴다. 상태 코드를 스스로 가진 Spring MVC 예외들이 구현하는 인터페이스
import org.springframework.web.ErrorResponse as SpringErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler {

    // 401 UNAUTHORIZED: 인증 실패 (로그인 필요, 토큰 누락/만료)
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.UNAUTHORIZED, e.message)

    // DTO @Valid 유효성 검증 실패 (400 Bad Request)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        errorResponse(
            HttpStatus.BAD_REQUEST,
            "입력값 검증에 실패했습니다.",
            errors = e.bindingResult.fieldErrors.map { it.defaultMessage },
        )

    // 요청 JSON 본문 파싱 오류 / 잘못된 데이터 타입 (400 Bad Request)
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, "요청 본문(JSON) 형식이 올바르지 않습니다.")

    // 잘못된 파라미터 / 잘못된 인자 전달 (400 Bad Request)
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.FORBIDDEN, e.message)

    // 403 FORBIDDEN: 인가 실패
    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(e: ForbiddenException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.FORBIDDEN, e.message)

    // 조회 대상 데이터가 존재하지 않음 (404 Not Found)
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(e: NoSuchElementException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.NOT_FOUND, e.message ?: "존재하지 않는 데이터입니다.")

    // 비즈니스 상태 충돌 (409 Conflict)
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(e: IllegalStateException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.CONFLICT, e.message)

    // 경로 변수·쿼리 값 형식 오류 (/api/habits/abc, ?status=WRONG 등) (400 Bad Request)
    // Spring의 ErrorResponse를 구현하지 않아 아래 일괄 처리에서 상태 코드를 꺼낼 수 없으므로 따로 받는다
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다.")

    // 서버 내부 미처리 예외 방어선 (500 Internal Server Error)
    // 단, Spring MVC 표준 오류(없는 경로 404, 허용되지 않는 메서드 405, 본문 형식 415 등)는
    // 예외가 가진 4xx 상태 코드와 헤더(405의 Allow 등)를 그대로 쓴다
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(e: Exception): ResponseEntity<ErrorResponse> {
        if (e is SpringErrorResponse && e.statusCode.is4xxClientError) {
            val status = HttpStatus.resolve(e.statusCode.value()) ?: HttpStatus.BAD_REQUEST
            return errorResponse(status, clientErrorMessage(status), headers = e.headers)
        }
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.")
    }

    // 그룹 최대 참여 인원 초과 (400 BAD_REQUEST)
    @ExceptionHandler(GroupLimitExceededException::class)
    fun handleGroupLimitExceeded(e: GroupLimitExceededException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, e.message)

    // 비즈니스 정책 및 규칙 위반 (400 BAD_REQUEST)
    @ExceptionHandler(BusinessRuleException::class)
    fun handleBusinessRule(e: BusinessRuleException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_REQUEST, e.message)

    // 외부 사진 저장소(Supabase Storage) 통신 실패 (502 BAD_GATEWAY)
    @ExceptionHandler(StorageUnavailableException::class)
    fun handleStorageUnavailable(e: StorageUnavailableException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.BAD_GATEWAY, e.message)

    // 조회 대상 데이터가 존재하지 않음 (404 NOT_FOUND)
    @ExceptionHandler(EntityNotFoundException::class)
    fun handleEntityNotFound(e: EntityNotFoundException): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.NOT_FOUND, e.message)

    // Spring 기본 메시지는 영어라 다른 오류처럼 한국어 고정 메시지로 바꾼다
    private fun clientErrorMessage(status: HttpStatus): String =
        when (status) {
            HttpStatus.NOT_FOUND -> "요청한 주소를 찾을 수 없습니다."
            HttpStatus.METHOD_NOT_ALLOWED -> "허용되지 않는 요청 방식입니다."
            HttpStatus.UNSUPPORTED_MEDIA_TYPE -> "지원하지 않는 요청 형식입니다."
            else -> "잘못된 요청입니다."
        }

    // 응답 HTTP 상태와 본문의 status 숫자를 한곳에서 정해, 둘이 어긋나지 않게 한다
    private fun errorResponse(
        status: HttpStatus,
        message: String?,
        errors: List<String?>? = null,
        headers: HttpHeaders = HttpHeaders.EMPTY,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .headers(headers)
            .body(ErrorResponse(status.value(), message, errors = errors))
}
