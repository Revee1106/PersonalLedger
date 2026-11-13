package com.eric.personalledger.domain

object CategoryTags {
    fun defaults(type: EntryType): List<String> = when (type) {
        EntryType.EXPENSE, EntryType.INCOME, EntryType.TRANSFER -> catalog
    }

    val catalog: List<String> = listOf("餐饮", "交通", "购物")

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

    fun normalizeCommon(tags: List<String>): List<String> {
        val normalized = if (tags.isEmpty()) emptyList() else normalize(tags)
        require(normalized.size <= 3) { "At most three common tags are allowed." }
        return normalized
    }

    /** Extras are ordered by the most recent selection, without changing pinned preferences. */
    fun displayOrder(selected: List<String>, common: List<String>): List<String> {
        val extras = selected.distinct().filterNot { it in common }.asReversed()
        return (extras + common).distinct()
    }
}
