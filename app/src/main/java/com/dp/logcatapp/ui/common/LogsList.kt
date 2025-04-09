package com.dp.logcatapp.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly
import com.dp.logcat.Log
import com.dp.logcat.LogPriority
import com.dp.logcatapp.R
import com.dp.logcatapp.ui.common.SearchHitKey.LogComponent
import com.dp.logcatapp.ui.common.SearchResult.SearchHitInfo
import com.dp.logcatapp.ui.common.SearchResult.SearchHitSpan
import com.dp.logcatapp.ui.theme.LogPriorityColors
import com.dp.logcatapp.ui.theme.LogcatReaderTheme
import com.dp.logcatapp.ui.theme.RobotoMonoFontFamily
import com.dp.logcatapp.ui.theme.currentSearchHitColor
import com.dp.logcatapp.ui.theme.logListItemSecondaryColor
import com.dp.logcatapp.util.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val DEFAULT_ENABLED_LIST_ITEMS = ToggleableLogItem.entries.toSet()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogsList(
  modifier: Modifier,
  state: LazyListState,
  contentPadding: PaddingValues,
  logs: List<Log>,
  searchHits: Map<SearchHitKey, SearchHitSpan>,
  appInfoMap: Map<String, AppInfo> = emptyMap(),
  currentSearchHitLogId: Int,
  listStyle: LogsListStyle = LogsListStyle.Default,
  enabledLogItems: Set<ToggleableLogItem> = DEFAULT_ENABLED_LIST_ITEMS,
  // onClick is only available for LogsListStyle.Default.
  onClick: ((Int) -> Unit)? = null,
  onLongClick: ((Int) -> Unit)? = null,
) {
  val textSelectionColors = LocalTextSelectionColors.current
  val currentSearchHitColor = currentSearchHitColor()
  LazyColumn(
    modifier = modifier,
    state = state,
    contentPadding = contentPadding,
  ) {
    itemsIndexed(
      items = logs,
      key = { index, _ -> logs[index].id }
    ) { index, item ->
      if (index > 0) {
        HorizontalDivider()
      }

      fun maybeHighlightSearchHit(target: String, searchHitKey: SearchHitKey): AnnotatedString {
        val hit = if (searchHits.isNotEmpty()) searchHits[searchHitKey] else null
        return if (hit != null) {
          buildAnnotatedString {
            append(target)
            val color = if (item.id == currentSearchHitLogId) {
              currentSearchHitColor
            } else {
              textSelectionColors.backgroundColor
            }
            addStyle(
              SpanStyle(
                background = color
              ),
              start = hit.start,
              end = hit.end,
            )
          }
        } else {
          AnnotatedString(target)
        }
      }

      if (listStyle == LogsListStyle.Compact) {
        var expanded by remember { mutableStateOf(false) }
        LogItemCompact(
          modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
              onLongClick = { onLongClick?.invoke(index) },
              onClick = { expanded = !expanded },
            )
            .wrapContentHeight(),
          priority = item.priority,
          tag = if (expanded || ToggleableLogItem.Tag in enabledLogItems) {
            maybeHighlightSearchHit(
              target = item.tag,
              searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.TAG),
            )
          } else null,
          message = maybeHighlightSearchHit(
            target = item.msg,
            searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.MSG),
          ),
          packageName = if (ToggleableLogItem.PackageName in enabledLogItems) {
            item.uid?.let { uid ->
              val packageName = if (uid.isDigitsOnly()) {
                appInfoMap[item.uid]?.packageName
              } else {
                uid
              }
              packageName?.let {
                maybeHighlightSearchHit(
                  target = it,
                  searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.PKG),
                )
              }
            }
          } else null,
          date = item.date,
          time = item.time,
          pid = item.pid,
          tid = item.tid,
          priorityColor = when (item.priority) {
            LogPriority.ASSERT -> LogPriorityColors.priorityAssert
            LogPriority.DEBUG -> LogPriorityColors.priorityDebug
            LogPriority.ERROR -> LogPriorityColors.priorityError
            LogPriority.FATAL -> LogPriorityColors.priorityFatal
            LogPriority.INFO -> LogPriorityColors.priorityInfo
            LogPriority.VERBOSE -> LogPriorityColors.priorityVerbose
            LogPriority.WARNING -> LogPriorityColors.priorityWarning
            else -> LogPriorityColors.prioritySilent
          },
          expanded = expanded,
        )
      } else {
        LogItem(
          modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
              onLongClick = { onLongClick?.invoke(index) },
              onClick = { onClick?.invoke(index) },
            )
            .wrapContentHeight(),
          priority = item.priority,
          tag = if (ToggleableLogItem.Tag in enabledLogItems) {
            maybeHighlightSearchHit(
              target = item.tag,
              searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.TAG),
            )
          } else null,
          message = maybeHighlightSearchHit(
            target = item.msg,
            searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.MSG),
          ),
          packageName = if (ToggleableLogItem.PackageName in enabledLogItems) {
            item.uid?.let { uid ->
              val packageName = if (uid.isDigitsOnly()) {
                appInfoMap[item.uid]?.packageName
              } else {
                uid
              }
              packageName?.let {
                maybeHighlightSearchHit(
                  target = it,
                  searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.PKG),
                )
              }
            }
          } else null,
          date = item.date.takeIf { ToggleableLogItem.Date in enabledLogItems },
          time = item.time.takeIf { ToggleableLogItem.Time in enabledLogItems },
          pid = item.pid.takeIf { ToggleableLogItem.Pid in enabledLogItems },
          tid = item.tid.takeIf { ToggleableLogItem.Tid in enabledLogItems },
          priorityColor = when (item.priority) {
            LogPriority.ASSERT -> LogPriorityColors.priorityAssert
            LogPriority.DEBUG -> LogPriorityColors.priorityDebug
            LogPriority.ERROR -> LogPriorityColors.priorityError
            LogPriority.FATAL -> LogPriorityColors.priorityFatal
            LogPriority.INFO -> LogPriorityColors.priorityInfo
            LogPriority.VERBOSE -> LogPriorityColors.priorityVerbose
            LogPriority.WARNING -> LogPriorityColors.priorityWarning
            else -> LogPriorityColors.prioritySilent
          },
        )
      }
    }
  }
}

@Composable
private fun LogItem(
  modifier: Modifier,
  priority: String,
  tag: AnnotatedString?,
  message: AnnotatedString,
  packageName: AnnotatedString?,
  date: String?,
  time: String?,
  pid: String?,
  tid: String?,
  priorityColor: Color,
) {
  val textColor = logListItemSecondaryColor()
  Row(
    modifier = modifier
      .height(IntrinsicSize.Max),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .background(priorityColor)
        .padding(5.dp),
    ) {
      Text(
        modifier = Modifier.align(Alignment.Center),
        text = priority,
        style = TextStyle.Default.copy(
          fontSize = 12.sp,
          fontFamily = RobotoMonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          textAlign = TextAlign.Center,
        ),
      )
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(all = 5.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      if (tag != null) {
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = tag,
          style = TextStyle.Default.copy(
            fontSize = 13.sp,
            fontFamily = RobotoMonoFontFamily,
            fontWeight = FontWeight.Medium,
          )
        )
      }
      Text(
        modifier = Modifier.fillMaxWidth(),
        text = message,
        style = TextStyle.Default.copy(
          fontSize = 12.sp,
          fontFamily = RobotoMonoFontFamily,
        )
      )
      if (packageName != null) {
        Text(
          text = packageName,
          style = TextStyle.Default.copy(
            fontSize = 12.sp,
            fontFamily = RobotoMonoFontFamily,
            fontWeight = FontWeight.Bold,
            color = textColor,
          )
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Absolute.spacedBy(10.dp),
      ) {
        if (date != null) {
          Text(
            text = date,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
        }
        if (time != null) {
          Text(
            text = time,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
        }
        if (pid != null) {
          Text(
            text = pid,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
        }
        if (tid != null) {
          Text(
            text = tid,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
        }
      }
    }
  }
}

@Composable
private fun LogItemCompact(
  modifier: Modifier,
  priority: String,
  tag: AnnotatedString?,
  message: AnnotatedString,
  packageName: AnnotatedString?,
  date: String,
  time: String,
  pid: String,
  tid: String,
  priorityColor: Color,
  expanded: Boolean,
) {
  Row(
    modifier = modifier
      .height(IntrinsicSize.Max),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .background(priorityColor)
        .padding(4.dp),
    ) {
      Text(
        modifier = Modifier.align(Alignment.Center),
        text = priority,
        style = TextStyle.Default.copy(
          fontSize = 12.sp,
          fontFamily = RobotoMonoFontFamily,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          textAlign = TextAlign.Center,
        ),
      )
    }
    if (expanded) {
      val textColor = logListItemSecondaryColor()
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(all = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        if (tag != null) {
          Text(
            modifier = Modifier.fillMaxWidth(),
            text = tag,
            style = TextStyle.Default.copy(
              fontSize = 13.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Medium,
            )
          )
        }
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = message,
          style = TextStyle.Default.copy(
            fontSize = 12.sp,
            fontFamily = RobotoMonoFontFamily,
          )
        )
        if (packageName != null) {
          Text(
            text = packageName,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Absolute.spacedBy(10.dp),
        ) {
          Text(
            text = date,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
          Text(
            text = time,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
          Text(
            text = pid,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
          Text(
            text = tid,
            style = TextStyle.Default.copy(
              fontSize = 12.sp,
              fontFamily = RobotoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
          )
        }
      }
    } else {
      Spacer(modifier = Modifier.width(4.dp))
      if (tag != null) {
        Text(
          modifier = Modifier.weight(0.2f),
          text = tag,
          overflow = TextOverflow.Ellipsis,
          maxLines = 1,
          style = TextStyle.Default.copy(
            fontSize = 12.sp,
            fontFamily = RobotoMonoFontFamily,
            fontWeight = FontWeight.Medium,
          )
        )
        Spacer(modifier = Modifier.width(4.dp))
      }
      Text(
        modifier = Modifier.weight(0.8f),
        text = message,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = TextStyle.Default.copy(
          fontSize = 12.sp,
          fontFamily = RobotoMonoFontFamily,
        )
      )
    }
  }
}

suspend fun searchLogs(
  logs: List<Log>,
  appInfoMap: Map<String, AppInfo>,
  searchQuery: String,
) = withContext(Dispatchers.Default) {
  val map = mutableMapOf<SearchHitKey, SearchHitSpan>()
  val hits = mutableListOf<SearchHitInfo>()
  logs.forEachIndexed { index, log ->
    val msgIndex = log.msg.indexOf(string = searchQuery, ignoreCase = true)
    val tagIndex = log.tag.indexOf(string = searchQuery, ignoreCase = true)
    val uid = log.uid
    val packageNameIndex = if (uid != null) {
      val packageName = if (uid.isDigitsOnly()) {
        appInfoMap[log.uid]?.packageName
      } else {
        uid
      }
      packageName?.indexOf(string = searchQuery, ignoreCase = true) ?: -1
    } else {
      -1
    }
    if (msgIndex != -1) {
      map[SearchHitKey(logId = log.id, component = LogComponent.MSG)] =
        SearchHitSpan(start = msgIndex, end = msgIndex + searchQuery.length)
      hits += SearchHitInfo(logId = log.id, index = index)
    }
    if (tagIndex != -1) {
      map[SearchHitKey(logId = log.id, component = LogComponent.TAG)] =
        SearchHitSpan(start = tagIndex, end = tagIndex + searchQuery.length)
      hits += SearchHitInfo(logId = log.id, index = index)
    }
    if (packageNameIndex != -1) {
      map[SearchHitKey(logId = log.id, component = LogComponent.PKG)] =
        SearchHitSpan(start = packageNameIndex, end = packageNameIndex + searchQuery.length)
      hits += SearchHitInfo(logId = log.id, index = index)
    }
  }
  Pair(
    first = map,
    second = hits.sortedBy {
      // sort by log id
      it.logId
    },
  )
}

data class SearchResult(
  val map: Map<SearchHitKey, SearchHitSpan>,
  val hitsSortedById: List<SearchHitInfo>
) {
  data class SearchHitSpan(
    val start: Int,
    val end: Int,
  )

  data class SearchHitInfo(
    val logId: Int,
    val index: Int,
  )
}

data class SearchHitKey(
  val logId: Int,
  val component: LogComponent,
) {
  enum class LogComponent {
    MSG,
    TAG,
    PKG,
  }
}

enum class LogsListStyle {
  Default,
  Compact,
}

enum class ToggleableLogItem(@StringRes val labelRes: Int) {
  Tag(R.string.tag),
  Date(R.string.date),
  Time(R.string.time),
  Pid(R.string.process_id),
  Tid(R.string.thread_id),
  PackageName(R.string.package_name),
}

@Preview(showBackground = true)
@Composable
private fun LogItemPreview() {
  LogcatReaderTheme {
    LogItem(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      priority = "D",
      tag = AnnotatedString("Tag"),
      message = AnnotatedString("This is a log"),
      date = "01-12",
      time = "21:10:46.123",
      pid = "1600",
      tid = "123123",
      packageName = AnnotatedString("com.dp.logcatapp"),
      priorityColor = LogPriorityColors.priorityDebug,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun LogItemCompactPreview() {
  LogcatReaderTheme {
    LogItemCompact(
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(),
      priority = "D",
      tag = AnnotatedString("FooBarFooBarFooBar"),
      message = AnnotatedString("This is a long log this is a long log this is a long log"),
      date = "01-12",
      time = "21:10:46.123",
      pid = "1600",
      tid = "123123",
      priorityColor = LogPriorityColors.priorityDebug,
      packageName = AnnotatedString("com.dp.logcatapp"),
      expanded = false,
    )
  }
}