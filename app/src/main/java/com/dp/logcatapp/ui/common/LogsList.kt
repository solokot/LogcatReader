package com.dp.logcatapp.ui.common

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
import com.dp.logcat.Log
import com.dp.logcat.LogPriority
import com.dp.logcatapp.ui.common.SearchHitKey.LogComponent
import com.dp.logcatapp.ui.theme.LogPriorityColors
import com.dp.logcatapp.ui.theme.LogcatReaderTheme
import com.dp.logcatapp.ui.theme.RobotoMonoFontFamily
import com.dp.logcatapp.ui.theme.currentSearchHitColor
import com.dp.logcatapp.ui.theme.logListItemSecondaryColor

enum class LogsListStyle {
  Default,
  Compact,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogsList(
  modifier: Modifier,
  state: LazyListState,
  contentPadding: PaddingValues,
  logs: List<Log>,
  searchHits: Map<SearchHitKey, Pair<Int, Int>>,
  currentSearchHitLogId: Int,
  listStyle: LogsListStyle = LogsListStyle.Default,
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
              start = hit.first,
              end = hit.second
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
          tag = maybeHighlightSearchHit(
            target = item.tag,
            searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.TAG),
          ),
          message = maybeHighlightSearchHit(
            target = item.msg,
            searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.MSG),
          ),
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
          tag = maybeHighlightSearchHit(
            target = item.tag,
            searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.TAG),
          ),
          message = maybeHighlightSearchHit(
            target = item.msg,
            searchHitKey = SearchHitKey(logId = item.id, component = LogComponent.MSG),
          ),
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
        )
      }
    }
  }
}

@Composable
private fun LogItem(
  modifier: Modifier,
  priority: String,
  tag: AnnotatedString,
  message: AnnotatedString,
  date: String,
  time: String,
  pid: String,
  tid: String,
  priorityColor: Color,
) {
  Row(
    modifier = modifier
      .height(IntrinsicSize.Max),
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
      Text(
        modifier = Modifier.fillMaxWidth(),
        text = tag,
        style = TextStyle.Default.copy(
          fontSize = 13.sp,
          fontFamily = RobotoMonoFontFamily,
          fontWeight = FontWeight.Medium,
        )
      )
      Text(
        modifier = Modifier.fillMaxWidth(),
        text = message,
        style = TextStyle.Default.copy(
          fontSize = 12.sp,
          fontFamily = RobotoMonoFontFamily,
        )
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Absolute.spacedBy(10.dp),
      ) {
        val textColor = logListItemSecondaryColor()
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
  }
}

@Composable
private fun LogItemCompact(
  modifier: Modifier,
  priority: String,
  tag: AnnotatedString,
  message: AnnotatedString,
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
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(all = 5.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = tag,
          style = TextStyle.Default.copy(
            fontSize = 13.sp,
            fontFamily = RobotoMonoFontFamily,
            fontWeight = FontWeight.Medium,
          )
        )
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = message,
          style = TextStyle.Default.copy(
            fontSize = 12.sp,
            fontFamily = RobotoMonoFontFamily,
          )
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Absolute.spacedBy(10.dp),
        ) {
          val textColor = logListItemSecondaryColor()
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

data class SearchHitKey(
  val logId: Int,
  val component: LogComponent,
) {
  enum class LogComponent {
    MSG,
    TAG,
  }
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
      expanded = false,
    )
  }
}