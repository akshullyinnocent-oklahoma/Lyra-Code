package com.yukisoffd.lyracode.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class ScheduledTaskWorkerTest {
    @Test
    fun recognizesUnknownHostAsRetryable() {
        assertTrue(UnknownHostException("api.deepseek.com").isRetryableNetworkFailure())
    }

    @Test
    fun recognizesWrappedAndroidDnsMessageAsRetryable() {
        val error = IllegalStateException(
            "请求中断",
            RuntimeException("Unable to resolve host \"api.deepseek.com\": No address associated with hostname"),
        )
        assertTrue(error.isRetryableNetworkFailure())
    }

    @Test
    fun doesNotRetryPermanentRequestErrors() {
        assertFalse(IllegalArgumentException("API Key 不能为空").isRetryableNetworkFailure())
    }
}
