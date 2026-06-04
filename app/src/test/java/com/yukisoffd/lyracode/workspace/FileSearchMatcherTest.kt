package com.yukisoffd.lyracode.workspace

import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchMatcherTest {
    @Test
    fun matchesExactFileName() {
        val matcher = FileSearchMatcher("test.py")

        assertTrue(matcher.matches("test.py", "scripts/test.py"))
    }

    @Test
    fun matchesNameWithoutSeparators() {
        val matcher = FileSearchMatcher("MainActivity")

        assertTrue(matcher.matches("MainActivity.kt", "app/src/main/java/com/example/MainActivity.kt"))
    }

    @Test
    fun matchesPathTermsInAnySegment() {
        val matcher = FileSearchMatcher("src main py")

        assertTrue(matcher.matches("app.py", "project/src/main/app.py"))
    }

    @Test
    fun matchesWeakFuzzyName() {
        val matcher = FileSearchMatcher("mnact")

        assertTrue(matcher.matches("MainActivity.kt", "app/src/main/java/MainActivity.kt"))
    }
}
