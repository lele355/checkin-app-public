package cn.anlele.checkin.ui.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MelonEngine 连击取整口径回归（BUG-2）—— App round 口径与网页 Math.round 同规。
 * 历史 bug：App `(lv * comboMult).toInt()` 截断 vs 网页 `Math.round`，30 个（源级×倍率）
 * 组合里 9 个分歧（例：lv4×1.2 网页 +5 / App +4）→ 双端分数漂移。
 * 本测试把全部 9 个分歧格锁死在 App 侧（网页侧由 qa/melon_unit.js 同名 9 断言锁死）。
 * 双列分放防产物互撞污染分数。
 */
class MelonComboRoundingTest {

    /** 在 (x,y) 列投入一对相触同级果并推进物理，返回本次合成的分数增量 */
    private fun mergePair(e: MelonEngine, level: Int, x: Float, y: Float): Int {
        val r = MelonEngine.RADII[level - 1]
        val s0 = e.score
        e.bodies.add(MelonEngine.Body(x, y, 0f, 0f, r, level))
        e.bodies.add(MelonEngine.Body(x + r * 1.2f, y, 0f, 0f, r, level))
        repeat(20) { e.step(1f / 60f) }
        return e.score - s0
    }

    @Test
    fun combo12RoundingDivergentCells() {
        // ×1.2 分歧格：lv3→4、lv4→5、lv8→10、lv9→11（round；旧 toInt 分别少 1）
        val cells = listOf(3 to 4, 4 to 5, 8 to 10, 9 to 11)
        for ((k, want) in cells) {
            val e = MelonEngine(seed = 9)
            mergePair(e, 4, 100f, 200f)                 // 连击 1（×1）
            val delta = mergePair(e, k, 280f, 200f)     // 连击 2 → ×1.2
            assertEquals("lv${k}×1.2 应 +$want（round 口径）", want, delta)
            assertTrue("倍率档 ×1.2", e.comboMult == 1.2f)
        }
    }

    @Test
    fun combo15RoundingDivergentCells() {
        // ×1.5 分歧格：lv1→2、lv3→5、lv5→8、lv7→11、lv9→14（round；旧 toInt 分别少 1）
        val cells = listOf(1 to 2, 3 to 5, 5 to 8, 7 to 11, 9 to 14)
        for ((k, want) in cells) {
            val e = MelonEngine(seed = 9)
            mergePair(e, 4, 100f, 200f)                 // 连击 1（×1）
            mergePair(e, 5, 60f, 140f)                  // 连击 2（×1.2）
            val delta = mergePair(e, k, 280f, 200f)     // 连击 3 → ×1.5
            assertEquals("lv${k}×1.5 应 +$want（round 口径）", want, delta)
            assertTrue("倍率档 ×1.5", e.comboMult == 1.5f)
        }
    }
}
