package dev.dsh.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dsh.remote.data.GoalInfo
import dev.dsh.remote.data.JobView
import dev.dsh.remote.ui.icons.DshIcon
import dev.dsh.remote.ui.icons.DshIcons
import dev.dsh.remote.ui.theme.DshAmber
import dev.dsh.remote.ui.theme.DshGreen
import dev.dsh.remote.ui.theme.DshRed

/**
 * Right side rail holding the goal + background-jobs cards. It overlays the
 * conversation instead of squeezing it, matching the desktop behavior.
 */
@Composable
fun RightPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val goal by vm.currentGoal.collectAsState()
    val jobs by vm.jobs.collectAsState()
    val todos by vm.currentTodos.collectAsState()
    val goalInfo = goal?.goal
    val hasGoal = goalInfo != null && goalInfo.objective.isNotBlank()

    Column(
        modifier
            .fillMaxHeight()
            .width(300.dp)
            .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
            )
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        if (hasGoal) GoalCard(vm, goalInfo!!)
        if (todos.isNotEmpty()) {
            if (hasGoal) Spacer(Modifier.height(8.dp))
            TodoCard(todos)
        }
        if (jobs.isNotEmpty()) {
            if (hasGoal || todos.isNotEmpty()) Spacer(Modifier.height(8.dp))
            JobsRow(jobs)
        }
        if (!hasGoal && todos.isEmpty() && jobs.isEmpty()) {
            Text(
                "无目标 / 待办 / 后台任务",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TodoCard(todos: List<dev.dsh.remote.data.TodoItem>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DshIcon(DshIcons.Checklist, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                "待办 (${todos.size})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        for (t in todos) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            when (t.status) {
                                "completed" -> DshGreen
                                "in_progress" -> DshAmber
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    t.content,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GoalCard(vm: AppViewModel, goal: GoalInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DshGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DshIcon(DshIcons.Goal, tint = DshGreen, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                "目标 · ${goal.phase}",
                color = DshGreen,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            goal.objective,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
        when (goal.phase) {
            "active" -> {
                TextButton(onClick = { vm.goalPause() }) { Text("暂停", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { vm.goalComplete() }) { Text("完成", style = MaterialTheme.typography.labelSmall) }
            }
            "paused" -> {
                TextButton(onClick = { vm.goalResume() }) { Text("恢复", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun JobsRow(jobs: List<JobView>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DshIcon(DshIcons.Loading, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 14.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                "后台任务",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        for (j in jobs) {
            JobCard(j)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun JobCard(job: JobView) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JobIndicator(job.status)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    job.kind.ifBlank { "任务" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (job.label.isNotBlank()) {
                    Text(
                        job.label,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            jobFooter(job),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun JobIndicator(status: String) {
    val running = status == "running" || status == "stopping"
    val color = when (status) {
        "completed", "killed" -> DshGreen
        "failed" -> DshRed
        else -> MaterialTheme.colorScheme.primary
    }
    if (running) {
        CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            strokeWidth = 2.dp,
            color = color,
        )
    } else {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
    }
}

private fun jobFooter(job: JobView): String {
    val statusText = when (job.status) {
        "completed" -> "已完成"
        "killed" -> "已终止"
        "failed" -> "失败"
        "stopping" -> "停止中"
        else -> "进行中"
    }
    val parts = mutableListOf(statusText)
    if (!job.detail.isNullOrBlank()) parts.add(job.detail)
    val duration = job.finishedAt?.let { fin ->
        if (job.startedAt > 0 && fin > job.startedAt) fin - job.startedAt else null
    }
    if (duration != null) parts.add(formatDuration(duration))
    return parts.joinToString(" · ")
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return if (sec < 60) "${sec}秒" else "${sec / 60}分${sec % 60}秒"
}
