package com.kingzcheung.xime.ui.settings

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.kingzcheung.xime.platform.AndroidPlatform

/**
 * CyIME 在系统里启用了没有、是不是当前输入法。引导第一步、设置首页和预览键盘都按这里读。
 *
 * 只看 InputMethodManager.enabledInputMethodList 不够：红米 Turbo5（Android 17）上已启用、已设为默认、能打字，
 * 引导第一步仍说没启用，进不了设置（#508）。所以几样证据有一样就算：启用列表、ENABLED_INPUT_METHODS 设置、
 * 已经是当前输入法（只有启用了才能选），以及系统已经把输入法服务开起来了（系统只开启用的输入法）。
 */
internal fun isCyimeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val listed = runCatching { imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true }
        .getOrDefault(false)
    val setting = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
    }.getOrNull()
    return imeEnabledFrom(context.packageName, listed, setting, isCyimeSelected(context),
        com.kingzcheung.xime.service.ImeServiceLiveness.alive)
}

/** [setting] 是 ENABLED_INPUT_METHODS 的原文：「包名/服务;子类型:包名/服务…」。 */
internal fun imeEnabledFrom(packageName: String, listed: Boolean, setting: String?, selected: Boolean, serviceAlive: Boolean): Boolean =
    listed || selected || serviceAlive ||
        setting.orEmpty().split(':').any { it.substringBefore(';').substringBefore('/') == packageName }

/**
 * 当前输入法是不是 CyIME：读 DEFAULT_INPUT_METHOD；读不到（空）时 Android 14 起再问 InputMethodManager 当前输入法。
 * 读得到就以它为准，不会因为别的证据把另一个输入法当成 CyIME。
 */
internal fun isCyimeSelected(context: Context): Boolean {
    val setting = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    }.getOrNull().orEmpty()
    val current = if (setting.isBlank() && AndroidPlatform.currentInputMethod) runCatching {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.currentInputMethodInfo?.packageName
    }.getOrNull() else null
    return imeSelectedFrom(context.packageName, setting, current)
}

internal fun imeSelectedFrom(packageName: String, setting: String, currentPackage: String?): Boolean =
    if (setting.isNotBlank()) setting.substringBefore('/') == packageName  // 「包名/服务」；本地单测里 ComponentName 是空桩
    else currentPackage == packageName
