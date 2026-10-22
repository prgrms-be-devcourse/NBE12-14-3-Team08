package com.back.support

// Java 테스트에서 Kotlin 클래스를 호출할 수 있는지 확인하기 위한 샘플이다. (JavaKotlinInteropTest)
class KotlinInteropSample(val name: String, val description: String? = null) {

    fun hasDescription(): Boolean = description != null

    companion object {
        // Java에서 KotlinInteropSample.of(...)로 호출할 수 있게 한다. (엔티티 정적 팩토리 전환 시 같은 방식 사용)
        @JvmStatic
        fun of(name: String): KotlinInteropSample = KotlinInteropSample(name)
    }
}
