package jp.yagi.safecleaner.widget

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import jp.yagi.safecleaner.R
import jp.yagi.safecleaner.model.CleanerMode

/**
 * One-operation widget with no container background. The whole 1x1 cell is
 * clickable, while only the operation icon is rendered.
 */
abstract class OperationWidget(
    private val mode: CleanerMode,
    @DrawableRes private val iconRes: Int,
    @StringRes private val contentDescriptionRes: Int,
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(context) }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val intent = Intent(context, WidgetDispatchActivity::class.java)
            .setAction("jp.yagi.safecleaner.widget.${mode.name}")
            .putExtra(WidgetDispatchActivity.EXTRA_MODE, mode.name)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = context.getString(contentDescriptionRes),
                modifier = GlanceModifier.size(40.dp),
            )
        }
    }
}

class CleanAllWidget : OperationWidget(
    mode = CleanerMode.CLEAN_ALL,
    iconRes = R.drawable.widget_clean_all_icon,
    contentDescriptionRes = R.string.widget_clean_all_name,
)

class ForceStopWidget : OperationWidget(
    mode = CleanerMode.FORCE_STOP_ONLY,
    iconRes = R.drawable.widget_force_stop_icon,
    contentDescriptionRes = R.string.widget_force_stop_name,
)

class ClearCacheWidget : OperationWidget(
    mode = CleanerMode.CLEAR_CACHE_ONLY,
    iconRes = R.drawable.widget_clear_cache_icon,
    contentDescriptionRes = R.string.widget_clear_cache_name,
)

class CleanAllWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CleanAllWidget()
}

class ForceStopWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForceStopWidget()
}

class ClearCacheWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClearCacheWidget()
}
