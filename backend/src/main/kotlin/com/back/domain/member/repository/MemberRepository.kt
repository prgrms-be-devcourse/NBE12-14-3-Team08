package com.back.domain.member.repository

import com.back.domain.member.entity.Member
import com.back.domain.member.entity.MemberStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemberRepository : JpaRepository<Member, Long> {

    fun existsByUsername(username: String): Boolean

    // global의 Java 코드(TestDataInit)가 Optional로 사용하고 있어 그대로 둔다.
    fun findByUsername(username: String): Optional<Member>

    fun existsByIdAndStatus(id: Long, status: MemberStatus): Boolean
}
