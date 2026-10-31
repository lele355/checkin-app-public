package cn.anlele.checkin.ui.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FishEngine JVM 压力/规则测试 —— 「3 分钟不闪退」的脚本化验证 + F11/F12 规则断言。
 * 真机闪退根因：鱼群迭代中 spawnFish（List.add）触发 ConcurrentModificationException，
 * 首条鱼出屏（约 5 秒）即炸。本测试模拟 3 分钟帧循环 + 批量出屏，任何 CME 直接失败。
 */
class FishEngineStressTest {

    @Test
    fun threeMinutesOfFramesNeverThrows() {
        val e = FishEngine()
        repeat(10800) { i ->                     // 3 分钟 × 60fps
            e.setTarget((i * 7 % 360).toFloat(), (i * 11 % 480).toFloat())
            if (i % 72 == 0) e.dash()
            e.step(1f / 60f)
        }
        assertTrue("鱼群应持续存在", e.fishes.isNotEmpty())
    }

    @Test
    fun batchFishExitRespawnsWithoutCrash() {
        // 把全部鱼推到出屏位：一步内批量出屏 + 对侧补鱼（旧代码在第一处就抛 CME）
        val e = FishEngine()
        e.fishes.forEach { it.x = 10000f }
        e.step(1f / 60f)
        assertTrue("出屏即补鱼", e.fishes.isNotEmpty())
        // 再走 30 秒，覆盖补鱼后的常态游动
        repeat(1800) { e.step(1f / 60f) }
        assertTrue(e.fishes.isNotEmpty())
    }

    @Test
    fun eatingFishRespawnsWithoutCrash() {
        // 玩家长大后连续吃鱼：吃鱼路径的 spawnFish 同样不能在迭代内
        val e = FishEngine()
        e.setTarget(180f, 240f)
        // 喂 200 帧让鱼群自然聚拢被吃
        repeat(200 * 60) { i ->
            e.setTarget((180 + (i % 40) - 20).toFloat(), 240f)
            e.step(1f / 60f)
        }
        assertTrue(e.fishes.isNotEmpty())
    }

    @Test
    fun enemyContactCostsLifeAndGrantsInvuln() {
        // F12：天敌（水母 kind=3）碰触扣命 + 无敌帧，不可吃
        val e = FishEngine()
        e.fishes.clear()
        e.pearls.clear()
        e.fishes.add(FishEngine.Fish(x = 180f, y = 240f, baseY = 240f, phase = 0f, level = 0, dir = 1, speed = 0f, kind = 3))
        e.setTarget(180f, 240f)
        e.step(1f / 60f)
        assertEquals("天敌碰触扣一命", 2, e.lives)
        assertTrue("受击后应有无敌帧", e.invuln > 0f)
        assertEquals("天敌不可吃（不给分）", 0, e.score)
        assertEquals("天敌碰触应触发受伤音效事件", 1, e.evHurt)
    }

    @Test
    fun enemyNeverEdibleEvenWhenSmaller() {
        // F12：鲨鱼（kind=4）即使体型不大于玩家也危险、不可吃
        val e = FishEngine()
        e.fishes.clear()
        e.pearls.clear()
        e.fishes.add(FishEngine.Fish(x = 180f, y = 240f, baseY = 240f, phase = 0f, level = 0, dir = 1, speed = 0f, kind = 4))
        e.setTarget(180f, 240f)
        e.step(1f / 60f)
        assertEquals(0, e.score)
        assertEquals("碰触天敌扣命", 2, e.lives)
    }

    @Test
    fun sameLevelPreyIsEdible() {
        // F8：普通鱼同级可吃，计分走 VALUES 档位（P3-3：0 档 = 50）
        val e = FishEngine()
        e.fishes.clear()
        e.pearls.clear()
        e.fishes.add(FishEngine.Fish(x = 180f, y = 240f, baseY = 240f, phase = 0f, level = 0, dir = 1, speed = 0f, kind = 0))
        e.setTarget(180f, 240f)
        e.step(1f / 60f)
        assertEquals("同级可吃 +1 档位分", 50, e.score)
        assertEquals("吃鱼应触发吃音效事件", 1, e.evEat)
    }

    @Test
    fun stageFormulaMatchesTutorialSpec() {
        // F11：每 1000 分或 120 秒 +1 关，封顶第 6 关（教程与引擎同规）
        val e = FishEngine()
        assertEquals(1, e.stageOf(0, 0f))
        assertEquals(2, e.stageOf(1000, 0f))
        assertEquals(2, e.stageOf(0, 120f))
        assertEquals(3, e.stageOf(1000, 120f))
        assertEquals(6, e.stageOf(99999, 99999f))
    }
}
