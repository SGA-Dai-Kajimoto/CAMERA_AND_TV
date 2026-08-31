package com.sony.dtv.camera_tv.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 実画面ガイドの進行判定。
 *
 * 「キーを押したか」ではなく「結果が起きたか」で進むこと、
 * その手順のキーが効かない状態では進まないことを固定する。
 */
class TutorialCoachTest {

    private val onPhoto = CoachSignal(screen = CoachScreen.Photo, photoIndex = 3, judgedCount = 5)
    private val inCull = onPhoto.copy(screen = CoachScreen.Cull, isCulling = true)

    private fun progressAt(step: CoachStep, signal: CoachSignal) = CoachProgress(step, signal)

    // ---------------------------------------------------------------- //
    // 各手順の達成条件
    // ---------------------------------------------------------------- //

    @Test
    fun `写真を送るは表示中の写真が変わったら達成`() {
        val progress = progressAt(CoachStep.SendPhoto, onPhoto)

        assertEquals(CoachStep.SendPhoto, progress.advance(onPhoto).step)
        assertEquals(CoachStep.StartCulling, progress.advance(onPhoto.copy(photoIndex = 4)).step)
        // 戻る向きに送っても達成
        assertEquals(CoachStep.StartCulling, progress.advance(onPhoto.copy(photoIndex = 2)).step)
    }

    @Test
    fun `選別を始めるは選別中になったら達成`() {
        val progress = progressAt(CoachStep.StartCulling, onPhoto)

        assertEquals(CoachStep.StartCulling, progress.advance(onPhoto).step)
        assertEquals(CoachStep.Zoom, progress.advance(inCull).step)
    }

    @Test
    fun `判定は判定済みが増えたら達成`() {
        val progress = progressAt(CoachStep.Judge, inCull)

        assertEquals(CoachStep.Judge, progress.advance(inCull).step)
        assertEquals(CoachStep.Done, progress.advance(inCull.copy(judgedCount = 6)).step)
    }

    @Test
    fun `未判定に戻して判定済みが減っても判定は達成しない`() {
        // 案内中に選別結果で戻すと減りうる。減少を達成と誤認しない
        val progress = progressAt(CoachStep.Judge, inCull)

        assertEquals(CoachStep.Judge, progress.advance(inCull.copy(judgedCount = 4)).step)
    }

    // ---------------------------------------------------------------- //
    // 拡大の扱い
    // ---------------------------------------------------------------- //

    @Test
    fun `拡大は解除するまで達成しない`() {
        val progress = progressAt(CoachStep.Zoom, inCull)

        // 拡大しただけでは進まない。拡大中の上下キーは見る位置の移動に使われるため
        val zoomed = progress.advance(inCull.copy(isZoomed = true))
        assertEquals(CoachStep.Zoom, zoomed.step)

        // 解除して初めて次の手順へ
        assertEquals(CoachStep.Judge, zoomed.advance(inCull).step)
    }

    @Test
    fun `拡大せずに解除状態が続いても達成しない`() {
        val progress = progressAt(CoachStep.Zoom, inCull)

        assertEquals(CoachStep.Zoom, progress.advance(inCull).advance(inCull).step)
    }

    @Test
    fun `拡大中は判定の手順を案内しない`() {
        assertFalse(CoachStep.Judge.isActiveOn(inCull.copy(isZoomed = true)))
        assertTrue(CoachStep.Judge.isActiveOn(inCull))
    }

    @Test
    fun `拡大中の判定には拡大の解除を促す`() {
        val zoomed = inCull.copy(isZoomed = true)

        assertEquals(
            com.sony.dtv.camera_tv.R.string.coach_exit_zoom,
            CoachStep.Judge.strandedMessage(zoomed),
        )
        assertNull(CoachStep.Judge.strandedMessage(inCull))
    }

    // ---------------------------------------------------------------- //
    // 画面の限定
    // ---------------------------------------------------------------- //

    @Test
    fun `案内できない画面では達成にしない`() {
        // メニューを開いたままだと左右キーはボタン移動に使われ、写真は送られない
        val inMenu = onPhoto.copy(screen = CoachScreen.Other)
        val progress = progressAt(CoachStep.SendPhoto, inMenu)

        assertEquals(CoachStep.SendPhoto, progress.advance(inMenu.copy(photoIndex = 9)).step)
    }

    @Test
    fun `手順ごとに成立する画面が決まっている`() {
        assertTrue(CoachStep.SendPhoto.isActiveOn(onPhoto))
        assertFalse(CoachStep.SendPhoto.isActiveOn(onPhoto.copy(screen = CoachScreen.Other)))

        // 選別を始める手順はメニューを経由するのでどの画面でも成立する
        assertTrue(CoachStep.StartCulling.isActiveOn(onPhoto.copy(screen = CoachScreen.Other)))

        assertTrue(CoachStep.Zoom.isActiveOn(inCull))
        assertFalse(CoachStep.Zoom.isActiveOn(onPhoto))
    }

    @Test
    fun `画面を限定する手順には戻り方の文言がある`() {
        CoachStep.entries.forEach { step ->
            if (step.requiredScreen != null) assertTrue(step.strandedRes != null)
        }
    }

    // ---------------------------------------------------------------- //
    // 手順の並び
    // ---------------------------------------------------------------- //

    @Test
    fun `締めくくりは達成条件を持たない`() {
        val progress = progressAt(CoachStep.Done, onPhoto)

        assertEquals(CoachStep.Done, progress.advance(CoachSignal()).step)
        assertNull(CoachStep.Done.next())
    }

    @Test
    fun `手順は選別作業の流れどおりに並ぶ`() {
        assertEquals(
            listOf(
                CoachStep.SendPhoto,
                CoachStep.StartCulling,
                CoachStep.Zoom,
                CoachStep.Judge,
                CoachStep.Done,
            ),
            CoachStep.entries.toList(),
        )
        // 進捗表示は締めくくりを数えない
        assertEquals(4, CoachStep.TOTAL)
    }

    @Test
    fun `最初から最後まで通してたどれる`() {
        var progress = CoachProgress.initial(onPhoto)

        progress = progress.advance(onPhoto.copy(photoIndex = 4))
        assertEquals(CoachStep.StartCulling, progress.step)

        progress = progress.advance(inCull)
        assertEquals(CoachStep.Zoom, progress.step)

        progress = progress.advance(inCull.copy(isZoomed = true))
        progress = progress.advance(inCull)
        assertEquals(CoachStep.Judge, progress.step)

        progress = progress.advance(inCull.copy(judgedCount = 6))
        assertEquals(CoachStep.Done, progress.step)
    }
}
