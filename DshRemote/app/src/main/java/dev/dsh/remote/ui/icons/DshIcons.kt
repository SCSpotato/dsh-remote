package dev.dsh.remote.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dsh.remote.R

/** Resource IDs for the DSH-native vector icons (converted from the ic_ds_* SVG set). */
object DshIcons {
    val Send = R.drawable.dsh_ic_send_outline16
    val SendSmall = R.drawable.dsh_ic_send_outline14
    val Stop = R.drawable.dsh_ic_stop_fill16
    val ChevronDown = R.drawable.dsh_ic_chevron_down_outline14
    val ChevronUp = R.drawable.dsh_ic_chevron_up_outline14
    val ChevronLeft = R.drawable.dsh_ic_chevron_left_outline14
    val ChevronRight = R.drawable.dsh_ic_chevron_right_outline14
    val TriangleRight = R.drawable.dsh_ic_triangle_right_fill14
    val CloseFill = R.drawable.dsh_ic_close_fill14
    val Close = R.drawable.dsh_ic_close_outline16
    val Copy = R.drawable.dsh_ic_copy_outline16
    val Check = R.drawable.dsh_ic_check_outline16
    val CheckSmall = R.drawable.dsh_ic_check_outline14
    val Branch = R.drawable.dsh_ic_branch_outline16
    val Edit = R.drawable.dsh_ic_edit_outline16
    val Trash = R.drawable.dsh_ic_trash_outline16
    val Plus = R.drawable.dsh_ic_plus_outline16
    val ProjectAdd = R.drawable.dsh_ic_project_add_outline16
    val PanelLeft = R.drawable.dsh_ic_panel_left_outline16
    val NewChat = R.drawable.dsh_ic_new_chat_outline16
    val Settings = R.drawable.dsh_ic_settings_outline16
    val SettingsSmall = R.drawable.dsh_ic_settings_outline14
    val Search = R.drawable.dsh_ic_search_outline16
    val Api = R.drawable.dsh_ic_api_outline14
    val Globe = R.drawable.dsh_ic_globe_outline14
    val Browse = R.drawable.dsh_ic_browse_outline16
    val Code = R.drawable.dsh_ic_code_outline16
    val Sparkle = R.drawable.dsh_ic_sparkle16
    val Folder = R.drawable.dsh_ic_folder_close16
    val FolderOpen = R.drawable.dsh_ic_folder_open_outline16
    val Question = R.drawable.dsh_ic_question_outline14
    val Warning = R.drawable.dsh_ic_warning_outline16
    val Goal = R.drawable.dsh_ic_goal_outline16
    val Checklist = R.drawable.dsh_ic_checklist_outline14
    val Queue = R.drawable.dsh_ic_queue_outline14
    val Think = R.drawable.dsh_ic_think_outline14
    val Paperclip = R.drawable.dsh_ic_paperclip_outline16
    val Loading = R.drawable.dsh_ic_loading_outline16
    val Data = R.drawable.dsh_ic_data_outline16
    val User = R.drawable.dsh_ic_user_outline16
    val Play = R.drawable.dsh_ic_play_outline16
    val Pause = R.drawable.dsh_ic_pause_outline16
    val Light = R.drawable.dsh_ic_light_outline16
    val Dark = R.drawable.dsh_ic_dark_outline16
    val FollowSystem = R.drawable.dsh_ic_followsystem_outline16
    val Fish = R.drawable.dsh_brand_fish
}

@Composable
fun DshIcon(
    @DrawableRes resId: Int,
    tint: Color,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}
