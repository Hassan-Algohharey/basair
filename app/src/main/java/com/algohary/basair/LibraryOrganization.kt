package com.algohary.basair

internal val fiqhSchools = listOf("hanafi", "maliki", "shafii", "hanbali")
internal val schoolNames = mapOf("hanafi" to "الحنفي", "maliki" to "المالكي", "shafii" to "الشافعي", "hanbali" to "الحنبلي")

internal fun Book.matchesOrganization(subject: String?, school: String?, topic: String?): Boolean =
    (subject == null || this.subject == subject) &&
        (school == null || (this.subject == "fiqh" &&
            if (school == "shared") schools.isEmpty() else school in schools)) &&
        (topic == null || topic in topics)

// Curated metadata aliases, not translations of book contents or religious rulings.
internal val subjectAliases = mapOf(
    "fiqh" to "fiqh jurisprudence worship transactions", "aqidah" to "aqidah creed faith",
    "hadith" to "hadith sunnah", "sirah" to "sirah seerah prophetic biography",
    "tafsir" to "tafsir quran interpretation", "salaf" to "salaf biographies",
    "tazkiyah" to "tazkiyah spiritual growth purification of the soul",
    "tajweed" to "tajweed tajwid recitation tuhfat atfal jamzuri jazariyyah jazari"
)
internal fun bookSearchAliases(book: Book): String = subjectAliases[book.subject].orEmpty() + " " +
    book.topics.joinToString(" ") { topicSearchAliases[it].orEmpty() } + " " +
    book.schools.joinToString(" ") { it + " " + schoolNames[it].orEmpty() }

internal fun matchingReferenceVolumes(query: String, topic: String?): List<ReferenceVolume> {
    val terms = normalizeArabic(query).split(Regex("\\s+")).filter { it.isNotBlank() }
    val linked = Catalog.searchComparisons("", "fiqh", topic).flatMap { it.opinions }
        .flatMap { it.references }.map { it.volume }.toSet()
    val queryLinked = Catalog.searchComparisons(query, "fiqh", topic).flatMap { it.opinions }
        .flatMap { it.references }.map { it.volume }.toSet()
    return referenceVolumes.values.filter { volume ->
        val metadata = normalizeArabic("${volume.reference.title} ${volume.volume} Kuwaiti Fiqh Encyclopedia comparative reference")
        (topic == null || volume.volume in linked) &&
            (terms.all { it in metadata } || volume.volume in queryLinked)
    }
}
