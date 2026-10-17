package com.markdrop.app.conversion.cloud

import com.markdrop.app.conversion.markdown.HeaderFooterCleaner
import org.junit.Assert.*
import org.junit.Test

class CloudConfigAndPolicyTest {

    @Test
    fun `empty or missing key resolution fails fast without dispatching request`() {
        val resultNull = CloudConfig.resolveKey(provider = CloudProvider.ANTHROPIC, key = null)
        assertTrue(resultNull.isFailure)
        assertTrue(resultNull.exceptionOrNull() is CloudConfigError)
        assertTrue(resultNull.exceptionOrNull()!!.message!!.contains("missing"))

        val resultBlank = CloudConfig.resolveKey(provider = CloudProvider.ANTHROPIC, key = "   ")
        assertTrue(resultBlank.isFailure)
        assertTrue(resultBlank.exceptionOrNull() is CloudConfigError)
    }

    @Test
    fun `fail fast aborts on Auth BadRequest and ModelNotFound`() {
        val policy = AiErrorPolicy()

        assertTrue(policy.isFailFast(VisionException.Auth("Unauthorized 401")))
        assertTrue(policy.isFailFast(VisionException.BadRequest("Bad Request 400")))
        assertTrue(policy.isFailFast(VisionException.ModelNotFound("Model 404")))

        assertFalse(policy.isFailFast(VisionException.RateLimited(1000L, "Rate limit 429")))
        assertFalse(policy.isFailFast(VisionException.Overloaded("Overloaded 503")))
        assertFalse(policy.isFailFast(VisionException.Network("Timeout")))
    }

    @Test
    fun `circuit breaker trips after 3 consecutive identical error classes`() {
        val policy = AiErrorPolicy()

        assertFalse(policy.recordErrorAndCheckTripped(VisionException.RateLimited(1000L, "1")))
        assertFalse(policy.recordErrorAndCheckTripped(VisionException.RateLimited(1000L, "2")))
        assertTrue(policy.recordErrorAndCheckTripped(VisionException.RateLimited(1000L, "3")))
    }

    @Test
    fun `circuit breaker resets on page success`() {
        val policy = AiErrorPolicy()

        assertFalse(policy.recordErrorAndCheckTripped(VisionException.RateLimited(1000L, "1")))
        assertFalse(policy.recordErrorAndCheckTripped(VisionException.RateLimited(1000L, "2")))
        policy.recordSuccess()
        assertFalse(policy.recordErrorAndCheckTripped(VisionException.RateLimited(1000L, "3")))
    }

    @Test
    fun `fallback OFF marks page FAILED and never produces FALLBACK`() {
        val policy = AiErrorPolicy(fallbackToOcrOnError = false)
        val decision = policy.handlePageError(pageIndex = 0, error = VisionException.RateLimited(1000L, "Limit"))

        assertEquals(PageErrorAction.MARK_FAILED, decision.action)
        assertEquals("FAILED", decision.pageStatus)
    }

    @Test
    fun `fallback ON yields FALLBACK status and non-strippable comment`() {
        val policy = AiErrorPolicy(fallbackToOcrOnError = true)
        val decision = policy.handlePageError(pageIndex = 0, error = VisionException.RateLimited(1000L, "Limit"))

        assertEquals(PageErrorAction.FALLBACK_TO_OCR, decision.action)
        assertEquals("FALLBACK", decision.pageStatus)
        assertEquals("<!-- page 1: AI failed, OCR fallback (RateLimited) -->", decision.markerComment)

        // Verify HeaderFooterCleaner never strips this comment
        val samplePage = "${decision.markerComment}\n\n# Heading\nBody text\nLine 3\nLine 4\nLine 5\nLine 6\nLine 7\nLine 8\nLine 9\nLine 10"
        val pages = listOf(samplePage, samplePage, samplePage)
        val cleaned = HeaderFooterCleaner.cleanPages(pages)
        assertTrue(cleaned.all { it.contains("AI failed, OCR fallback") })
    }
}
