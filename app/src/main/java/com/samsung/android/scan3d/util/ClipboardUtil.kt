package com.samsung.android.scan3d.util

import android.content.ClipData
import android.content.Context

object ClipboardUtil {

    fun copyToClipboard(context: Context?, label: String, text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }
}