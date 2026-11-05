package com.glassmark.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/** Widget da tela inicial: links recentes (ou favoritos, se houver). Toque abre o link. */
class LinkWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val uid = currentUid(context)
        val dao = AppDb.get(context).linkDao()
        val favs = prefsOf(context).getBoolean("widget_favorites", false)
        val links = if (favs) dao.favorites(uid, 15).ifEmpty { dao.recent(uid, 15) } else dao.recent(uid, 15)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Color(0xFF0E1530)).cornerRadius(22.dp).padding(12.dp)) {
                Text(if (favs) "Link · Favoritos" else "Link · Recentes", style = TextStyle(color = ColorProvider(Color(0xFF9FC3FF)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                if (links.isEmpty()) Text("Nenhum link salvo", style = TextStyle(color = ColorProvider(Color(0xFFB0B8CC)), fontSize = 12.sp), modifier = GlanceModifier.padding(top = 8.dp))
                LazyColumn {
                    items(links, itemId = { it.id }) { l ->
                        Column(GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
                            .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url))))) {
                            Text(l.title.ifBlank { domainOf(l.url) }, maxLines = 1, style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                            Text(domainOf(l.url), maxLines = 1, style = TextStyle(color = ColorProvider(Color(0xFF8A94A6)), fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }
    companion object {
        fun refresh(ctx: Context) { appScope.launch { runCatching { LinkWidget().updateAll(ctx) } } }
    }
}


class LinkWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LinkWidget() }
