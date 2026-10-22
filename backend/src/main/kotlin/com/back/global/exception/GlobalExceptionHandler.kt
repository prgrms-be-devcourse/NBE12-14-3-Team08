package com.back.global.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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

    // 서버 내부 미처리 예외 방어선 (500 Internal Server Error)
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(): ResponseEntity<ErrorResponse> =
        errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.")

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

    // 응답 HTTP 상태와 본문의 status 숫자를 한곳에서 정해, 둘이 어긋나지 않게 한다
    private fun errorResponse(
        status: HttpStatus,
        message: String?,
        errors: List<String?>? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(status)
            .body(ErrorResponse(status.value(), message, errors = errors))
}
