package com.algohary.basair

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ComparisonScreen(selectedId: String, onSelect: (String) -> Unit) {
    val comparison = Catalog.comparisons.first { it.id == selectedId }
    var school by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionTitle("المقارنة الفقهية", "خمس مسائل · أربعة مذاهب · إحالات إلى الصفحات") }
        item {
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(comparison.title, Modifier.weight(1f), fontSize = 15.sp)
                    Text("⌄")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    Catalog.comparisons.forEach { entry ->
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
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = school == null, onClick = { school = null }, label = { Text("الأربعة") })
                comparison.opinions.forEach { opinion ->
                    FilterChip(selected = school == opinion.school, onClick = { school = opinion.school }, label = { Text(opinion.school) })
                }
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
        item { Notice("الملخصات تحريرية، والإحالات مطابقة لصفحات النسخ المصورة المحددة. الموسوعة مرجع فقهي مقارن؛ راجع حواشيها للمصادر المذهبية الأصلية. لا تعني مطابقة الإحالة اعتماد فتوى لحالة شخصية أو مراجعة لجنة علمية للمشروع.") }
    }
}
