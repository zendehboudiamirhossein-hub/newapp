package ir.omid.vpnman.util

fun Number.fa(): String = toString().toPersianDigits()

fun String.toPersianDigits(): String {
    val en = "0123456789"
    val fa = "۰۱۲۳۴۵۶۷۸۹"
    return buildString(length) {
        for (c in this@toPersianDigits) {
            val i = en.indexOf(c)
            append(if (i >= 0) fa[i] else c)
        }
    }
}
