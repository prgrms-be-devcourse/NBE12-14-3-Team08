package com.back.domain.habitVerify.service

import com.back.domain.group.repository.GroupRepository
import com.back.domain.groupMember.repository.GroupMemberRepository
import com.back.domain.habitVerify.dto.HabitVerifyPhotoCursor
import com.back.domain.habitVerify.dto.HabitVerifyPhotoPage
import com.back.domain.habitVerify.dto.HabitVerifyPhotoResponse
import com.back.domain.habitVerify.entity.HabitVerifyStatus
import com.back.domain.habitVerify.repository.HabitVerifyRepository
import com.back.global.entity.requiredId
import com.back.global.exception.EntityNotFoundException
import com.back.global.exception.requireAllowed
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 그룹 메인 페이지의 인증 사진 갤러리용 조회. 승인된 인증 중 사진이 있는 것만 최신순으로 돌려준다.
@Service
@Transactional(readOnly = true)
class HabitVerifyPhotoService(
    private val groupRepository: GroupRepository,
    private val groupMemberRepository: GroupMemberRepository,
    private val habitVerifyRepository: HabitVerifyRepository,
) {

    // 확인 순서: 그룹 없음(404) → 그룹 멤버 아님(403) → 요청 값(400)
    fun getPhotos(memberId: Long, groupId: Long, size: Int, cursor: String?, filterMemberId: Long?): HabitVerifyPhotoPage {
        if (!groupRepository.existsById(groupId)) {
            throw EntityNotFoundException("존재하지 않는 그룹입니다.")
        }

        requireAllowed(groupMemberRepository.existsByGroupIdAndMemberId(groupId, memberId)) {
            "같은 그룹의 멤버만 조회할 수 있습니다."
        }

        require(size in 1..MAX_SIZE) { "size는 1 이상 ${MAX_SIZE} 이하여야 합니다." }
        val after = cursor?.let(HabitVerifyPhotoCursor::parse)

        // 한 건 더 읽어서 다음 페이지가 있는지 알아낸다
        val found = habitVerifyRepository.findPhotosByGroupId(
            groupId = groupId,
            status = HabitVerifyStatus.APPROVED,
            memberId = filterMemberId,
            cursorDate = after?.verifyDate,
            cursorId = after?.id,
            pageable = PageRequest.of(0, size + 1),
        )

        val page = found.take(size)
        val nextCursor = if (found.size > size) {
            page.last().let { HabitVerifyPhotoCursor(it.verifyDate, it.requiredId).encode() }
        } else {
            null
        }

        return HabitVerifyPhotoPage(page.map(HabitVerifyPhotoResponse::from), nextCursor)
    }

    companion object {
        const val DEFAULT_SIZE = 60
        const val MAX_SIZE = 120
    }
}
