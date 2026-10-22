package com.back.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Kotlin으로 전환한 클래스를 아직 Java인 코드(운영 코드, 기존 Java 테스트)가 그대로 호출할 수 있는지 확인한다.
class JavaKotlinInteropTest {

    @Test
    @DisplayName("Java에서 Kotlin 클래스의 프로퍼티를 getter로 호출할 수 있다")
    void Kotlin_프로퍼티를_getter로_호출한다() {
        KotlinInteropSample sample = new KotlinInteropSample("이름", "설명");

        assertThat(sample.getName()).isEqualTo("이름");
        assertThat(sample.getDescription()).isEqualTo("설명");
    }

    @Test
    @DisplayName("Java에서 @JvmStatic 팩토리를 정적 메서드처럼 호출할 수 있다")
    void JvmStatic_팩토리를_호출한다() {
        KotlinInteropSample sample = KotlinInteropSample.of("이름");

        assertThat(sample.getName()).isEqualTo("이름");
        assertThat(sample.hasDescription()).isFalse();
    }
}
