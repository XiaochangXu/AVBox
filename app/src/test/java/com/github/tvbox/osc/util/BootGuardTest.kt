package com.github.tvbox.osc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootGuardTest {

    companion object {
        private const val LOAD = 5_000L

        private fun frame(className: String, method: String): StackTraceElement =
            StackTraceElement(className, method, "$className.java", 1)

        private fun crashWith(vararg frames: StackTraceElement): RuntimeException =
            RuntimeException("crash").apply {
                stackTrace = Array(frames.size) { frames[it] }
            }
    }

    @Test
    fun startupCrash_disablesAfterSingleAttempt() {
        assertTrue(BootGuard.shouldDisable("/x/spider.jar", 1, LOAD + 28, true))
    }

    @Test
    fun startupCrash_atThresholdCounts() {
        assertTrue(BootGuard.crashedDuringStartup(LOAD + 10_000, LOAD))
        assertTrue(BootGuard.shouldDisable("/x/spider.jar", 1, LOAD + 10_000, true))
    }

    @Test
    fun slowCrash_oneMillisecondPastThreshold_doesNotDisable() {
        assertFalse(BootGuard.crashedDuringStartup(LOAD + 10_001, LOAD))
        assertFalse(BootGuard.shouldDisable("/x/spider.jar", 1, LOAD + 10_001, false))
    }

    @Test
    fun slowCrash_singleAttemptNeverDisables() {
        assertFalse(BootGuard.shouldDisable("/x/spider.jar", 1, LOAD + 60_000, false))
        assertFalse(BootGuard.shouldDisable("/x/spider.jar", 2, LOAD + 60_000, false))
    }

    @Test
    fun repeatedLoads_disableAtFallbackThreshold() {
        assertTrue(BootGuard.shouldDisable("/x/spider.jar", 3, LOAD + 60_000, false))
    }

    @Test
    fun noCrashNeverDisables() {
        assertFalse(BootGuard.shouldDisable("/x/spider.jar", 9, 0, true))
        assertFalse(BootGuard.shouldDisable("/x/spider.jar", 9, -1, true))
    }

    @Test
    fun crashWithoutLoadingJarNeverDisables() {
        assertFalse(BootGuard.shouldDisable("", 4, LOAD + 1, true))
        assertFalse(BootGuard.shouldDisable(null, 4, LOAD + 1, true))
    }

    @Test
    fun crashBeforeLoadStartIsNotStartupCrash() {
        assertFalse(BootGuard.crashedDuringStartup(LOAD - 1, LOAD))
        assertFalse(BootGuard.shouldDisable("/x/spider.jar", 1, LOAD - 1, false))
    }

    @Test
    fun missingLoadStartFallsBackToCount() {
        assertFalse(BootGuard.crashedDuringStartup(LOAD + 1, 0))
        assertFalse(BootGuard.shouldDisable("/x/spider.jar", 1, LOAD + 1, false))
        assertFalse("阈值以下是 2", BootGuard.shouldDisable("/x/spider.jar", 2, LOAD + 1, false))
        assertTrue(BootGuard.shouldDisable("/x/spider.jar", 3, LOAD + 1, false))
    }

    @Test
    fun midSessionSwitchCrash_countsAsLoadStageCrash() {
        val firstLoadOfProcess = LOAD
        val switchLoad = firstLoadOfProcess + 30 * 60_000L
        val crash = switchLoad + 28
        assertTrue(BootGuard.crashedDuringStartup(crash, switchLoad))
        assertTrue(BootGuard.shouldDisable("/x/spider.jar", 1, crash, true))
        assertFalse(
            "拿进程第一次装载当起点(旧写法)会判成 false —— 这正是要修掉的",
            BootGuard.crashedDuringStartup(crash, firstLoadOfProcess),
        )
    }

    @Test
    fun addDisabledSource_dedups() {
        var list = BootGuard.addDisabledSource(ArrayList(), "http://a/1")
        list = BootGuard.addDisabledSource(list, "http://a/1")
        assertEquals(1, list.size)
        assertEquals("http://a/1", list[0])
    }

    @Test
    fun addDisabledSource_ignoresBlank() {
        assertTrue(BootGuard.addDisabledSource(ArrayList(), "").isEmpty())
        assertTrue(BootGuard.addDisabledSource(ArrayList(), null).isEmpty())
    }

    @Test
    fun removeDisabledSource_removesMatch() {
        var list = BootGuard.removeDisabledSource(arrayListOf("http://a/1", "http://b/2"), "http://a/1")
        assertEquals(1, list.size)
        assertEquals("http://b/2", list[0])
    }

    @Test
    fun removeDisabledSource_missingIsNoop() {
        val list = arrayListOf("http://a/1")
        assertEquals(1, BootGuard.removeDisabledSource(list, "http://z/9").size)
        assertEquals(1, BootGuard.removeDisabledSource(list, "").size)
    }

    @Test
    fun blacklist_toleratesNullList() {
        val noList: ArrayList<String>? = null
        assertEquals(1, BootGuard.addDisabledSource(noList, "http://a/1").size)
        assertTrue(BootGuard.removeDisabledSource(noList, "http://a/1").isEmpty())
    }

    @Test
    fun uiCrashIsNotSourceRelated() {
        val crash = crashWith(
            frame("java.util.ArrayList\$Itr", "checkForComodification"),
            frame("com.github.tvbox.osc.ui.page.SettingsPageKt", "SettingsPage\$lambda\$6\$0\$1\$3"),
            frame("androidx.compose.runtime.internal.ComposableLambdaImpl", "invoke"),
            frame("com.github.tvbox.osc.ui.components.SettingsGroupKt", "SettingsCard"),
            frame("androidx.compose.runtime.Recomposer", "performRecompose"),
            frame("androidx.compose.ui.platform.AndroidUiFrameClock\$withFrameNanos\$2\$callback\$1", "doFrame"),
            frame("android.view.Choreographer\$CallbackRecord", "run"),
            frame("android.os.Handler", "dispatchMessage"),
            frame("android.os.Looper", "loop"),
            frame("android.app.ActivityThread", "main"),
            frame("java.lang.reflect.Method", "invoke"),
            frame("com.android.internal.os.RuntimeInit\$MethodAndArgsCaller", "run"),
            frame("com.android.internal.os.ZygoteInit", "main"),
        )
        assertFalse(BootGuard.looksSourceRelated(crash))
    }

    @Test
    fun frameworkCrashTailIsNotSourceRelated() {
        val crash = crashWith(
            frame("androidx.compose.runtime.Recomposer", "run"),
            frame("com.android.internal.os.RuntimeInit", "main"),
            frame("com.android.internal.os.ZygoteInit", "main"),
        )
        assertFalse(BootGuard.looksSourceRelated(crash))
    }

    @Test
    fun uiStackWithSpiderFrameIsStillSourceRelated() {
        val crash = crashWith(
            frame("androidx.compose.runtime.Recomposer", "run"),
            frame("com.github.tvbox.osc.ui.page.SettingsPageKt", "SettingsPage"),
            frame("com.github.catvod.spider.GoProxy", "<clinit>"),
            frame("com.android.internal.os.RuntimeInit", "main"),
        )
        assertTrue(BootGuard.looksSourceRelated(crash))
    }

    @Test
    fun spiderCrashIsSourceRelated() {
        val crash = crashWith(
            frame("com.github.catvod.spider.GoProxy", "<clinit>"),
            frame("com.github.catvod.crawler.JarLoader", "invokeInit"),
            frame("java.lang.Thread", "run"),
        )
        assertTrue(BootGuard.looksSourceRelated(crash))
    }

    @Test
    fun causeChainIsScanned() {
        val inner = crashWith(frame("com.github.catvod.spider.DouDou", "homeContent"))
        val outer = RuntimeException(
            "wrapped",
            inner,
        ).apply {
            stackTrace = arrayOf(frame("com.github.tvbox.osc.ui.page.HomeViewModel", "loadHome"))
        }
        assertTrue(BootGuard.looksSourceRelated(outer))
    }

    @Test
    fun suppressedChainIsScanned() {
        val crash = crashWith(frame("androidx.compose.runtime.ComposerImpl", "applyChanges"))
        val suppressed = crashWith(frame("com.github.catvod.spider.GoProxy", "init"))
        crash.addSuppressed(suppressed)
        assertTrue(BootGuard.looksSourceRelated(crash))
    }

    @Test
    fun unknownCrashCountsAsSourceRelated() {
        val empty = crashWith()
        assertTrue(BootGuard.looksSourceRelated(empty))
        assertTrue(BootGuard.looksSourceRelated(null))
    }
}
