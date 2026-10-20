package com.mtv.launcher.f1

import com.mtv.launcher.poly.PolymarketRepository
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run: ./gradlew :launcher:testReleaseUnitTest -Plive */
class LiveDataTest {
    @Test
    fun f1AndPolymarket() {
        assumeTrue(System.getProperty("live") == "true")
        val now = System.currentTimeMillis()
        val f1 = F1Repository.fetch(now)
        val next = f1.next(now)!!
        println("F1 next: R${next.round} ${next.shortName} ${next.circuit}, sessions=${next.sessions.map { it.label }}")
        println("F1 leader: ${f1.drivers.firstOrNull()} | teams: ${f1.constructors.take(2).map { it.name }} | last: ${f1.lastRaceName} ${f1.lastPodium}")
        assertTrue(f1.races.size > 10 && f1.drivers.isNotEmpty())

        val poly = PolymarketRepository.fetch(listOf("Tesla", "SpaceX", "F1"))
        poly.take(8).forEach { e -> println("POLY [${e.topic ?: "trending"}] ${e.title.take(60)} -> ${e.outcomes.take(2).joinToString { "${it.label} ${(it.probability * 100).toInt()}%" }}") }
        assertTrue(poly.size >= 5)
    }
}
