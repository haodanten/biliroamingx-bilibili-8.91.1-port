package app.revanced.bilibili.settings

import android.content.SharedPreferences

/**
 * 批量保存的 Editor 作用域。
 *
 * 为什么需要它:
 * - `SharedPreferences.Editor` **不是线程安全的**, 而 `Setting.save` 会同时来自
 *   UI 线程 (设置页) 与 OkHttp / gRPC 回调线程。把 Editor 放在全局字段里会让
 *   两条线程写同一个 Editor, 造成写入丢失与内部状态损坏。
 * - 批量保存期间若 `block` 抛异常, 全局字段会被永久留在"批量中"状态, 之后**所有**
 *   `save` 都会写进一个永远不会 `apply()` 的 Editor, 用户设置静默丢失。
 *
 * 这里的两条保证:
 * 1. 每个线程持有自己的 Editor (ThreadLocal), 跨线程互不干扰。
 * 2. `finally` 保证异常路径也会提交已写入的值并恢复线程状态。
 *
 * 本类只依赖 `SharedPreferences.Editor` 接口, 不触碰 Android 运行时,
 * 因此可以被普通 JVM 单元测试直接覆盖 (见 BatchEditorTest)。
 */
object BatchEditor {

    private val editors = ThreadLocal<SharedPreferences.Editor?>()

    /**
     * 当前线程正在使用的批量 Editor; 没有处于批量保存时为 `null`。
     */
    @JvmStatic
    fun current(): SharedPreferences.Editor? = editors.get()

    /**
     * 在 [editor] 的批量作用域内执行 [block]。
     *
     * - 支持同线程嵌套: 内层结束后恢复外层 Editor。
     * - 无论正常返回还是抛异常, 都会退出批量状态并 `apply()` 已写入的值。
     */
    @JvmStatic
    fun <T> batch(editor: SharedPreferences.Editor, block: () -> T): T {
        val previous = editors.get()
        editors.set(editor)
        try {
            return block()
        } finally {
            editors.set(previous)
            editor.apply()
        }
    }
}
