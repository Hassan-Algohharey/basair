package com.algohary.basair

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComparisonScreen(selectedId: String, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var school by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val results = remember(query, topic, school) { Catalog.searchComparisons(query, topic = topic, school = school) }
    val comparison = selectComparison(results, selectedId)
    LaunchedEffect(comparison?.id) { comparison?.let { if (it.id != selectedId) onSelect(it.id) } }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionTitle("المقارنة الفقهية", "${Catalog.comparisons.size} مسألة · أربعة مذاهب · إحالات إلى الصفحات") }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it; expanded = false },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("ابحث في المقارنات") }, placeholder = { Text("مسألة، قول، جزء أو صفحة") },
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("مسح") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), shape = RoundedCornerShape(14.dp))
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("الباب", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = topic == null, onClick = { topic = null; focus.clearFocus() }, label = { Text("كل الأبواب") })
                    Catalog.comparisonTopics.forEach { name ->
                        FilterChip(selected = topic == name, onClick = { topic = name; focus.clearFocus() }, label = { Text(name) })
                    }
                }
                Text("المذهب · البحث يشمل الأقوال المعروضة", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = school == null, onClick = { school = null; focus.clearFocus() }, label = { Text("الأربعة") })
                    Catalog.comparisonSchools.forEach { name ->
                        FilterChip(selected = school == name, onClick = { school = name; focus.clearFocus() }, label = { Text(name) })
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("النتائج: ${results.size} من ${Catalog.comparisons.size} مسألة", Modifier.weight(1f), color = Muted, fontSize = 13.sp)
                if (query.isNotBlank() || topic != null || school != null) TextButton(onClick = {
                    query = ""; topic = null; school = null; expanded = false; focus.clearFocus()
                }) { Text("إعادة الضبط") }
            }
        }
        if (comparison == null) item {
            Notice("لا توجد مسائل مطابقة. جرّب كلمة أقصر أو غيّر الباب أو المذهب، أو اضغط إعادة الضبط.")
        }
        if (comparison != null) {
            item {
                Box {
                    OutlinedButton(onClick = { expanded = true; focus.clearFocus() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text(comparison.title, Modifier.weight(1f), fontSize = 15.sp)
                        Text("⌄")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        results.forEach { entry ->
                            DropdownMenuItem(text = { Text(entry.title) }, onClick = { onSelect(entry.id); expanded = false })
                        }
                    }
                }
            }
            item {
                Surface(color = Green, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(comparison.topic, fontSize = 12.sp, color = Color(0xFFE8D5A7))
                        Text(comparison.title, fontSize = 25.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(comparison.scope, fontSize = 13.sp, lineHeight = 23.sp, color = Color(0xFFD6E6DB))
                    }
                }
            }
            item {
                val index = results.indexOf(comparison)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = index > 0, onClick = { onSelect(results[index - 1].id) }) { Text("المسألة السابقة") }
                    Text("${index + 1} / ${results.size}", color = Muted, fontSize = 12.sp)
                    TextButton(enabled = index < results.lastIndex, onClick = { onSelect(results[index + 1].id) }) { Text("المسألة التالية") }
                }
            }
            items(comparison.opinions.filter { school == null || it.school == school }, key = { comparison.id + it.school }) { opinion ->
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(24.dp).background(Gold, CircleShape))
                            Spacer(Modifier.width(9.dp)); Text("المذهب ${opinion.school}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Text(opinion.summary, fontSize = 16.sp, lineHeight = 28.sp)
                        HorizontalDivider(color = Line)
                        opinion.references.forEach { reference ->
                            Text(reference.label, fontSize = 13.sp, color = Green, lineHeight = 23.sp)
                            Text("${reference.publisher}\n${reference.edition}", fontSize = 11.sp, lineHeight = 20.sp, color = Muted)
                            TextButton(onClick = { openSource(context, reference.source) }) { Text("فتح المصدر المصوّر · صفحة ${reference.pdfPage}", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        item { Notice("الملخصات تحريرية، والإحالات مطابقة لصفحات النسخ المصورة المحددة. الموسوعة مرجع فقهي مقارن؛ راجع حواشيها للمصادر المذهبية الأصلية. لا تعني مطابقة الإحالة اعتماد فتوى لحالة شخصية أو مراجعة لجنة علمية للمشروع.") }
    }
}
