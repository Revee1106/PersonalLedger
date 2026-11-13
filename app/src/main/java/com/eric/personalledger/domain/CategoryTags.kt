package com.eric.personalledger.domain

object CategoryTags {
    fun defaults(type: EntryType): List<String> = when (type) {
        EntryType.EXPENSE -> listOf("餐饮", "交通", "购物")
        EntryType.INCOME -> listOf("工资", "奖金", "理财")
        EntryType.TRANSFER -> listOf("转账", "还款", "储蓄")
    }

    val catalog: List<String> = (EntryType.entries.flatMap(::defaults) +
        listOf("居家", "娱乐", "医疗", "教育", "其他")).distinct()

    fun normalize(tags: List<String>): List<String> {
        require(tags.isNotEmpty()) { "Select at least one category tag." }
        return tags.map { tag ->
            tag.trim().also {
                require(it.isNotEmpty() && it.length <= 40 && it.none(Char::isISOControl)) {
                    "Tag names must contain 1 to 40 characters without control characters."
                }
            }
        }.distinct()
    }

    /** Frequency counts entries, not money; recency resolves ties. */
    fun common(entries: Iterable<LedgerEntry>, type: EntryType): List<String> {
        val usage = mutableMapOf<String, Pair<Int, Long>>()
        entries.filter { it.type == type }.forEach { entry ->
            entry.categoryTags.forEach { tag ->
                val previous = usage[tag]
                usage[tag] = (previous?.first ?: 0) + 1 to
                    maxOf(previous?.second ?: Long.MIN_VALUE, entry.occurredAtEpochMillis)
            }
        }
        return (usage.keys.sortedWith(compareByDescending<String> { usage.getValue(it).first }
            .thenByDescending { usage.getValue(it).second }.thenBy { it }) + defaults(type)).distinct().take(3)
    }
}
