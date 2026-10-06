package app.revanced.bilibili.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * [BatchEditor] 的单元测试。
 *
 * 只依赖 `SharedPreferences.Editor` 接口 (测试里自己实现), 不触碰 Android 运行时,
 * 因此可以在普通 JVM 单元测试里跑, 不需要 Robolectric。
 *
 * 覆盖两个真实缺陷:
 * - 批量期间抛异常后线程状态必须被清理 (否则后续所有 save 写进死 Editor, 设置静默丢失)。
 * - 批量 Editor 必须按线程隔离 (Editor 不是线程安全的)。
 */
class BatchEditorTest {

    private class RecordingEditor : SharedPreferences.Editor {
        val ops = LinkedHashMap<String, Any?>()
        var applyCount = 0
        var commitCount = 0

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            ops[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            ops[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            ops[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            ops[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            ops[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            ops[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            ops.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            ops.clear()
            return this
        }

        override fun commit(): Boolean {
            commitCount++
            return true
        }

        override fun apply() {
            applyCount++
        }
    }

    @Test
    fun `no batch in progress means no active editor`() {
        assertNull(BatchEditor.current())
    }

    @Test
    fun `writes during batch are routed to the batch editor and applied once`() {
        val editor = RecordingEditor()

        BatchEditor.batch<Unit>(editor) {
            BatchEditor.current()!!.putBoolean("a", true)
            BatchEditor.current()!!.putInt("b", 42)
        }

        assertEquals(true, editor.ops["a"])
        assertEquals(42, editor.ops["b"])
        assertEquals(1, editor.applyCount)
        assertNull(BatchEditor.current())
    }

    /**
     * 回归测试: 原实现 (全局 `_batchEditor` 字段 + 没有 try/finally) 在 block 抛异常后
     * 会把状态永久留在"批量中", 导致之后每一次设置保存都写进一个永不 apply 的 Editor。
     */
    @Test
    fun `thread state is released even when block throws`() {
        val editor = RecordingEditor()
        val boom = IllegalStateException("boom")

        try {
            BatchEditor.batch<Unit>(editor) {
                BatchEditor.current()!!.putBoolean("saved", true)
                throw boom
            }
            fail("block 抛出的异常必须继续向上传播")
        } catch (e: IllegalStateException) {
            assertSame(boom, e)
        }

        assertNull(
            "异常后必须退出批量状态, 否则后续所有 save 都会写进死 Editor",
            BatchEditor.current()
        )
        assertTrue("异常前已写入的值应当被提交", editor.ops["saved"] == true)
        assertEquals(1, editor.applyCount)
    }

    /**
     * 异常之后, 非批量路径必须重新可用 (即 `current()` 为 null, 调用方走独立 editor + apply)。
     */
    @Test
    fun `after a failed batch plain saves still take the non-batch path`() {
        val batchEditor = RecordingEditor()

        runCatching {
            BatchEditor.batch<Unit>(batchEditor) { throw RuntimeException("boom") }
        }

        assertNull(BatchEditor.current())

        val plainEditor = RecordingEditor()
        val current = BatchEditor.current()
        if (current != null) current.putBoolean("late", true) else plainEditor.putBoolean("late", true)

        assertEquals(true, plainEditor.ops["late"])
        assertNull(batchEditor.ops["late"])
    }

    @Test
    fun `nested batch restores the outer editor`() {
        val outer = RecordingEditor()
        val inner = RecordingEditor()

        BatchEditor.batch<Unit>(outer) {
            assertSame(outer, BatchEditor.current())

            BatchEditor.batch<Unit>(inner) {
                assertSame(inner, BatchEditor.current())
            }

            assertSame("内层结束后必须恢复外层 Editor", outer, BatchEditor.current())
        }

        assertNull(BatchEditor.current())
        assertEquals(1, inner.applyCount)
        assertEquals(1, outer.applyCount)
    }

    /**
     * 回归测试: Editor 不能跨线程共享。
     *
     * Setting.save 会同时来自 UI 线程与 OkHttp / gRPC 回调线程, 原实现把 Editor
     * 放在全局字段里, 两条线程会写同一个 Editor。
     */
    @Test
    fun `batch editor is isolated per thread`() {
        val mainEditor = RecordingEditor()
        val workerSees = AtomicReference<SharedPreferences.Editor?>(null)
        val workerChecked = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)

        BatchEditor.batch<Unit>(mainEditor) {
            val worker = Thread {
                workerSees.set(BatchEditor.current())
                workerChecked.countDown()
                releaseWorker.await(10, TimeUnit.SECONDS)
            }
            worker.start()

            assertTrue(workerChecked.await(10, TimeUnit.SECONDS))
            assertNull(
                "另一条线程不得看到本线程的批量 Editor",
                workerSees.get()
            )

            releaseWorker.countDown()
            worker.join(10_000)
        }

        assertNull(BatchEditor.current())
        assertEquals(1, mainEditor.applyCount)
    }
}
