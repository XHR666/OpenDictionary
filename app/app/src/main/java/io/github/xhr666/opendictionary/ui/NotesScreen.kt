package io.github.xhr666.opendictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xhr666.opendictionary.data.UserDb

/** 我的笔记：词条笔记与作文保存内容统一查看/删除。 */
@Composable
fun NotesScreen(onNavigate: (Route) -> Unit) {
    val ctx = LocalContext.current
    val db = remember { UserDb.get(ctx) }
    var notes by remember { mutableStateOf(db.allNotes()) }
    PageScaffold("我的笔记", { onNavigate(Route.Home) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("共 ${notes.size} 条", style = OD.Cap.copy(color = OD.Muted))
            Spacer(Modifier.weight(1f))
            Text("词条笔记与作文会出现在这里", style = OD.Micro)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth()) {
            if (notes.isEmpty()) {
                Text("还没有笔记。在词条页写笔记，或在写作页保存作文。", style = OD.Cap)
            }
            notes.forEach { n ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(13.dp)).background(Color.White).border(1.dp, OD.Hairline, RoundedCornerShape(13.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.word, style = OD.H3.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                        Pressable(onClick = { db.deleteNote(n.id); notes = db.allNotes() }) { OIcon(Ic.Trash, 14.dp, OD.Muted) }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(n.content, style = OD.Cap.copy(fontSize = 11.5.sp, lineHeight = 17.sp))
                }
            }
            Spacer(Modifier.height(90.dp))
        }
    }
}
