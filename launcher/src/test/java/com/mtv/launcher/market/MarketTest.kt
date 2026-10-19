package com.mtv.launcher.market

import com.mtv.launcher.Brief
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MarketTest {

    private val open = 1_792_500_000_000L // inside the fake session below
    private fun quote(symbol: String, price: Double, prev: Double, fx: Boolean = false, now: Long = open) = Quote(
        symbol = symbol, name = Quote.displayName(symbol, null), price = price, previousClose = prev,
        currency = if (symbol == "USDKES=X") "KES" else "USD", points = listOf(prev, price), isFx = fx,
        regularStart = open - 3_600_000, regularEnd = open + 3_600_000,
        preStart = open - 7_200_000, postEnd = open + 7_200_000, fetchedAt = now,
    )

    @Test
    fun alertsOnlyWhenTheMoveEscalates() {
        val engine = AlertEngine(AlertEngine.State(lastSession = "OPEN"))
        assertEquals(0, engine.evaluate(listOf(quote("TSLA", 101.5, 100.0)), 2.0, 0.5, open).size)
        val first = engine.evaluate(listOf(quote("TSLA", 102.5, 100.0)), 2.0, 0.5, open)
        assertEquals("Tesla up 2.5% today", first.single().title)
        assertEquals(0, engine.evaluate(listOf(quote("TSLA", 103.0, 100.0)), 2.0, 0.5, open).size)
        assertEquals("Tesla up 4.1% today", engine.evaluate(listOf(quote("TSLA", 104.1, 100.0)), 2.0, 0.5, open).single().title)
        // Falling back below 4% and rising again doesn't repeat the same alert.
        assertEquals(0, engine.evaluate(listOf(quote("TSLA", 104.2, 100.0)), 2.0, 0.5, open).size)
        val down = engine.evaluate(listOf(quote("TSLA", 97.9, 100.0)), 2.0, 0.5, open).single()
        assertEquals("Tesla down 2.1% today", down.title)
        assertEquals(false, down.positive)
    }

    @Test
    fun weakerShillingIsBadNews() {
        val engine = AlertEngine(AlertEngine.State(lastSession = "OPEN"))
        val a = engine.evaluate(listOf(quote("USDKES=X", 130.5, 129.6, fx = true)), 2.0, 0.5, open).single()
        assertEquals("USD/KES up 0.7%", a.title)
        assertEquals(false, a.positive)
    }

    @Test
    fun announcesOpenAndClose() {
        val state = AlertEngine.State(lastSession = "PRE")
        val engine = AlertEngine(state)
        val opened = engine.evaluate(listOf(quote("TSLA", 100.5, 100.0)), 2.0, 0.5, open)
        assertEquals("US market is open", opened.single().title)
        val after = open + 3_700_000
        val closed = engine.evaluate(listOf(quote("TSLA", 100.5, 100.0, now = after)), 2.0, 0.5, after)
        assertEquals("US market closed", closed.single().title)
        assertTrue(closed.single().detail.contains("Tesla $100.50 +0.5%"))
    }

    @Test
    fun stateSurvivesJson() {
        val s = AlertEngine.State("2026-10-20", mutableMapOf("TSLA" to 2), mutableMapOf(), "OPEN")
        val back = AlertEngine.State.fromJson(s.toJson())
        assertEquals(2, back.maxUp["TSLA"])
        assertEquals("OPEN", back.lastSession)
    }

    @Test
    fun briefReadsLikeAPerson() {
        val quotes = listOf(quote("TSLA", 368.06, 354.72), quote("SPCX", 147.96, 146.03), quote("USDKES=X", 129.82, 128.73, fx = true))
        val brief = Brief.build(quotes, open)
        assertEquals(
            "Tesla is up 3.8% and SpaceX up 1.3% today. The shilling is at 129.82 to the dollar. US market closes in 1h 0m.",
            brief,
        )
        assertEquals("Good evening, Maina", Brief.greeting("Maina", 21))
        assertEquals("Good morning", Brief.greeting("", 8))
    }

    /** Run: ./gradlew :launcher:testReleaseUnitTest -Plive */
    @Test
    fun liveWatchlist() {
        assumeTrue(System.getProperty("live") == "true")
        val now = System.currentTimeMillis()
        val quotes = MarketRepository.fetch(listOf("TSLA", "SPCX", "USDKES=X"), now)
        quotes.forEach { println("LIVE ${it.symbol} ${it.name} ${AlertEngine.formatPrice(it)} ${"%.2f".format(it.changePct)}% points=${it.points.size} session=${it.session(now)}") }
        println("BRIEF " + Brief.build(quotes, now))
        assertEquals(3, quotes.size)
        assertTrue(quotes.all { it.points.size >= 2 })
    }
}
