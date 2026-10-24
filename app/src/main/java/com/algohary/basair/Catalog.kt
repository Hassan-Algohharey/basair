package com.algohary.basair

import java.text.Normalizer

data class Subject(val id: String, val title: String, val description: String, val symbol: String)
data class Book(
    val id: String, val title: String, val author: String, val subject: String,
    val description: String, val topics: List<String>, val source: String,
    val asset: String, val pages: Int, val publisher: String, val edition: String,
    val licenseName: String, val licenseUrl: String, val rightsNote: String, val sha256: String
)
data class Reference(
    val title: String, val publisher: String, val edition: String,
    val volume: Int, val pages: String, val pdfPage: Int, val pdfUrl: String
) {
    val label get() = "$title · ج $volume · ص $pages"
    val source get() = "$pdfUrl#page=$pdfPage"
}
data class SchoolOpinion(val school: String, val summary: String, val references: List<Reference>)
data class Comparison(val id: String, val title: String, val topic: String, val scope: String, val opinions: List<SchoolOpinion>)

private fun kuwaitReference(volume: Int, pages: String, pdfPage: Int) = Reference(
    "الموسوعة الفقهية الكويتية", "وزارة الأوقاف والشؤون الإسلامية — الكويت",
    if (volume == 31) "الطبعة الأولى، 1414هـ / 1994م، مطابع دار الصفوة" else "الطبعة الأولى، 1425هـ / 2005م",
    volume, pages, pdfPage,
    "https://d1.islamhouse.com/data/ar/ih_books/chain/ar_encyclopedia_fiqhiyyah_kuwait/ar_${volume}_encyclopedia_fiqhiyyah_kuwait.pdf"
)

object Catalog {
    val subjects = listOf(
        Subject("fiqh", "الفقه", "العبادات والمعاملات", "scales"),
        Subject("aqidah", "العقيدة", "الإيمان وأصول الاعتقاد", "star"),
        Subject("hadith", "الحديث", "السنة وعلوم الحديث", "book"),
        Subject("sirah", "السيرة", "السيرة النبوية والشمائل", "sun"),
        Subject("tafsir", "التفسير", "معاني القرآن وعلومه", "open"),
        Subject("salaf", "تراجم السلف", "سير الأئمة والعلماء", "book"),
        Subject("tazkiyah", "التزكية", "أعمال القلوب وتهذيب النفس", "star")
    )
    val books = libraryBooks
    val subjectFilters = mapOf(
        "salaf" to listOf("الأئمة الأربعة", "الصحابة", "التابعين"),
        "tazkiyah" to listOf("الإخلاص", "التوكل", "الذكر")
    )
    val comparisons = listOf(
        Comparison("ghusl-intention", "النية في الغسل من الجنابة", "الطهارة / الغسل",
            "الحكم العام للنية في غسل الجنابة. التلخيص من مرجع فقهي مقارن، وليس نقلًا مباشرًا عن الأئمة الأربعة.", listOf(
                SchoolOpinion("الحنفي", "النية في الغسل سنة وليست فرضًا.", listOf(kuwaitReference(31, "207", 207))),
                SchoolOpinion("المالكي", "النية لازمة في الغسل؛ عدّتها الموسوعة من فرائضه.", listOf(kuwaitReference(31, "207", 207))),
                SchoolOpinion("الشافعي", "النية لازمة في الغسل؛ عدّتها الموسوعة من فرائضه.", listOf(kuwaitReference(31, "207", 207))),
                SchoolOpinion("الحنبلي", "لا يصح الغسل دون نية، وفق عرض الموسوعة لفرائض الغسل.", listOf(kuwaitReference(31, "207", 207)))
            )),
        Comparison("wudu-order", "الترتيب في الوضوء", "الطهارة / الوضوء",
            "ترتيب الأعضاء المفروضة، لا ترتيب السنن. أُبقيت الإشارة إلى الأقوال الأخرى داخل المذهب كما يذكرها المرجع.", listOf(
                SchoolOpinion("الحنفي", "الترتيب سنة، وليس ركنًا ولا واجبًا.", listOf(kuwaitReference(43, "355–356", 356))),
                SchoolOpinion("المالكي", "الترتيب سنة في المشهور، ويوجد قول في المذهب بأنه ركن.", listOf(kuwaitReference(43, "355–356", 356))),
                SchoolOpinion("الشافعي", "الترتيب ركن في الوضوء، مع ذكر الموسوعة أقوالًا لبعض الشافعية بخلافه.", listOf(kuwaitReference(43, "355–356", 356))),
                SchoolOpinion("الحنبلي", "الترتيب ركن في المذهب، وتذكر الموسوعة رواية عن أحمد بسنيته.", listOf(kuwaitReference(43, "355–356", 356)))
            )),
        Comparison("wudu-continuity", "الموالاة في الوضوء", "الطهارة / الوضوء",
            "عرض الحكم العام لتتابع غسل الأعضاء؛ تفاصيل الأعذار وتقدير الجفاف تُراجع في باب الموالاة، ولا تستوعبها هذه البطاقة.", listOf(
                SchoolOpinion("الحنفي", "الموالاة سنة في الوضوء.", listOf(kuwaitReference(43, "355", 355))),
                SchoolOpinion("المالكي", "واجبة في المشهور؛ وتذكر الموسوعة أيضًا قولًا بسنيتها.", listOf(kuwaitReference(43, "355", 355))),
                SchoolOpinion("الشافعي", "سنة في القول الصحيح الجديد، وواجبة في القديم.", listOf(kuwaitReference(43, "355", 355))),
                SchoolOpinion("الحنبلي", "واجبة في المذهب، مع رواية بالسنية يذكرها المرجع.", listOf(kuwaitReference(43, "355", 355)))
            )),
        Comparison("ghusl-rubbing", "الدلك في الغسل", "الطهارة / الغسل",
            "دلك الأعضاء مع وصول الماء إلى البشرة. يعرض المرجع خلافًا داخل بعض المذاهب وتفصيل العجز عن الدلك.", listOf(
                SchoolOpinion("الحنفي", "دلك الأعضاء في الغسل سنة، وليس فرضًا.", listOf(kuwaitReference(31, "211–212", 211))),
                SchoolOpinion("المالكي", "الدلك فريضة في المشهور، ويعرض المرجع قولًا آخر وتفصيل العجز عنه.", listOf(kuwaitReference(31, "211–212", 211))),
                SchoolOpinion("الشافعي", "الدلك سنة، مع مخالفة المزني الذي عدّه من الفرائض.", listOf(kuwaitReference(31, "211–212", 211))),
                SchoolOpinion("الحنبلي", "دلك الأعضاء في الغسل سنة، وليس فرضًا.", listOf(kuwaitReference(31, "211–212", 211)))
            )),
        Comparison("ghusl-mouth-nose", "المضمضة والاستنشاق في الغسل", "الطهارة / الغسل",
            "المقارنة خاصة بالغسل، ولا يجوز تعميم نتيجتها تلقائيًا على الوضوء.", listOf(
                SchoolOpinion("الحنفي", "المضمضة والاستنشاق واجبان في الغسل.", listOf(kuwaitReference(31, "208–209", 208))),
                SchoolOpinion("المالكي", "ليسا واجبين في الغسل، ويعدّ غسلهما من سننه.", listOf(kuwaitReference(31, "208–209", 209))),
                SchoolOpinion("الشافعي", "ليسا واجبين في الغسل، ويعدّ غسلهما من سننه.", listOf(kuwaitReference(31, "208–209", 209))),
                SchoolOpinion("الحنبلي", "المضمضة والاستنشاق واجبان في الغسل.", listOf(kuwaitReference(31, "208–209", 208)))
            ))
    )

    private val bookSearchText = books.associate { book -> book.id to normalizeArabic(
        listOf(book.title, book.author, book.description, book.publisher,
            subjects.first { it.id == book.subject }.title, book.topics.joinToString(" ")).joinToString(" ")) }

    fun search(query: String, subject: String? = null): List<Book> {
        val terms = normalizeArabic(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        return books.filter { book -> (subject == null || book.subject == subject) && terms.all { it in bookSearchText.getValue(book.id) } }
    }

    fun searchComparisons(query: String, subject: String? = null): List<Comparison> {
        if (subject != null && subject != "fiqh") return emptyList()
        val terms = normalizeArabic(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        return comparisons.filter { comparison ->
            val text = normalizeArabic(comparison.title + " " + comparison.topic + " " + comparison.scope + " " +
                comparison.opinions.joinToString(" ") { opinion -> opinion.school + " " + opinion.summary + " " + opinion.references.joinToString { it.label } })
            terms.all { it in text }
        }
    }
}

fun normalizeArabic(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace("ـ", "")
    .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').lowercase()

fun parsePageNumber(input: String, pageCount: Int): Int? {
    val ascii = input.trim().map { char ->
        val digit = Character.digit(char, 10)
        if (digit >= 0) ('0'.code + digit).toChar() else char
    }.joinToString("")
    return ascii.toIntOrNull()?.takeIf { it in 1..pageCount }
}
