package dev.dsh.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dsh.remote.data.QuestionAnswer
import dev.dsh.remote.data.QuestionItem
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import dev.dsh.remote.ui.theme.DshAmber

/**
 * Inline decision cards (tool approval, plan review, ask-user question). These
 * render *inside* the conversation list instead of as dialogs, so the messages
 * behind them stay visible and the choices feel part of the chat flow.
 */

@Composable
fun ApprovalCard(vm: AppViewModel, approval: PendingApproval) {
    DecisionCard(title = "需要批准", accent = DshAmber, icon = DshIcons.Warning) {
        Text("工具：${approval.toolName}", fontWeight = FontWeight.SemiBold)
        approval.reason?.takeIf { it.isNotBlank() }?.let { r ->
            Spacer(Modifier.height(6.dp))
            Text(r, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.respondApproval(approval, allow = true) }) { Text("允许一次") }
            OutlinedButton(onClick = { vm.respondApproval(approval, allow = false) }) { Text("拒绝") }
        }
    }
}

@Composable
fun PlanReviewCard(vm: AppViewModel, pending: PendingQuestion, question: QuestionItem) {
    val approveLabel = question.intent?.approve
    val approveOpt = question.options.firstOrNull { it.label == approveLabel }
        ?: question.options.firstOrNull()
    val declineOpt = question.options.firstOrNull { it.label != approveLabel }

    DecisionCard(title = "计划待审", accent = DshAmber, icon = DshIcons.Goal) {
        question.detail?.let { plan ->
            // Cap the plan body so an extra-long plan scrolls inside the card
            // instead of pushing the composer off-screen.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MarkdownText(plan)
            }
        } ?: Text(question.question)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.respondQuestion(pending, listOf(QuestionAnswer(question.id, selected = listOf(approveOpt?.label ?: ""))))
            }) { Text("确认执行") }
            OutlinedButton(onClick = {
                vm.respondQuestion(pending, listOf(QuestionAnswer(question.id, selected = listOf(declineOpt?.label ?: ""))))
            }) { Text("继续规划") }
        }
    }
}

@Composable
fun QuestionCard(vm: AppViewModel, pending: PendingQuestion) {
    val questions = pending.questions
    // Local per-question selection + custom text, collected and sent together.
    val state = remember(questions) {
        mutableStateOf(questions.map { QuestionAnswer(it.id, emptyList(), null) })
    }
    val answers = state.value

    DecisionCard(title = questions.firstOrNull()?.header ?: "问题", accent = MaterialTheme.colorScheme.primary, icon = DshIcons.Question) {
        questions.forEachIndexed { qi, q ->
            if (qi > 0) Spacer(Modifier.height(12.dp))
            q.header?.let { h ->
                Text(h, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(q.question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            q.detail?.let { d ->
                Spacer(Modifier.height(4.dp))
                MarkdownText(d)
            }
            Spacer(Modifier.height(6.dp))
            val cur = answers[qi]
            q.options.forEach { opt ->
                val checked = opt.label in cur.selected
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (q.multiSelect) {
                        Checkbox(checked = checked, onCheckedChange = { on ->
                            val next = if (on) cur.selected + opt.label else cur.selected - opt.label
                            updateAnswer(state, qi) { it.copy(selected = next) }
                        })
                    } else {
                        RadioButton(
                            selected = checked,
                            onClick = {
                                updateAnswer(state, qi) { it.copy(selected = listOf(opt.label), custom = null) }
                            },
                        )
                    }
                    Column {
                        Text(opt.label, style = MaterialTheme.typography.bodySmall)
                        opt.description?.let { d ->
                            Text(d, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = cur.custom ?: "",
                onValueChange = { v ->
                    updateAnswer(state, qi) { it.copy(custom = v.ifBlank { null }, selected = if (v.isNotBlank() && !q.multiSelect) emptyList() else it.selected) }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("或输入自定义答案…") },
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.respondQuestion(pending, answers) }) { Text("提交") }
            OutlinedButton(onClick = { vm.respondQuestion(pending, answers) }) { Text("取消") }
        }
    }
}

@Composable
private fun DecisionCard(
    title: String,
    accent: Color,
    icon: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .height(IntrinsicSize.Min),
    ) {
        // Left accent bar distinguishes decisions from ordinary messages at a glance.
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
        )
        Column(Modifier.weight(1f).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DshIcon(icon, tint = accent, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(title, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun updateAnswer(
    state: MutableState<List<QuestionAnswer>>,
    index: Int,
    transform: (QuestionAnswer) -> QuestionAnswer,
) {
    val list = state.value.toMutableList()
    list[index] = transform(list[index])
    state.value = list
}
