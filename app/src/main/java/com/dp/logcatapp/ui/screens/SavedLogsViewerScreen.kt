package com.dp.logcatapp.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dp.logcat.Log
import com.dp.logcat.LogcatStreamReader
import com.dp.logcatapp.R
import com.dp.logcatapp.ui.common.CopyLogClipboardBottomSheet
import com.dp.logcatapp.ui.common.LogsList
import com.dp.logcatapp.ui.common.LogsListStyle
import com.dp.logcatapp.ui.common.SearchHitKey
import com.dp.logcatapp.ui.common.SearchLogsTopBar
import com.dp.logcatapp.ui.common.SearchResult.SearchHitInfo
import com.dp.logcatapp.ui.common.SearchResult.SearchHitSpan
import com.dp.logcatapp.ui.common.WithTooltip
import com.dp.logcatapp.ui.common.searchLogs
import com.dp.logcatapp.ui.theme.AppTypography
import com.dp.logcatapp.util.closeQuietly
import com.dp.logcatapp.util.findActivity
import com.dp.logcatapp.util.getFileNameFromUri
import com.dp.logcatapp.util.rememberAppInfoByUidMap
import com.dp.logcatapp.util.rememberBooleanSharedPreference
import com.dp.logcatapp.util.showToast
import com.dp.logcatapp.util.toRegexOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val SNAP_SCROLL_HIDE_DELAY_MS = 2000L
private const val COMPACT_VIEW_KEY = "saved_logs_viewer_compact_view_key"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLogsViewerScreen(
  modifier: Modifier,
  uri: Uri,
) {
  val context = LocalContext.current
  var logs by remember { mutableStateOf<LoadLogsState>(LoadLogsState.Loading) }
  LaunchedEffect(uri, context) {
    logs = loadLogs(context = context, uri = uri)

    if (logs is LoadLogsState.FileOpenError) {
      context.showToast(context.getString(R.string.error_opening_source))
      context.findActivity()?.finish()
    } else if (logs is LoadLogsState.LogsParseError) {
      context.showToast(context.getString(R.string.unsupported_source))
      context.findActivity()?.finish()
    }
  }

  val focusManager = LocalFocusManager.current
  val listState = rememberLazyListState()
  var showSearchBar by remember { mutableStateOf(false) }
  var useRegexForSearch by remember { mutableStateOf(false) }
  var searchRegexError by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var currentSearchHitIndex by remember { mutableIntStateOf(-1) }
  var currentSearchHitLogId by remember { mutableIntStateOf(-1) }
  var showHitCount by remember { mutableStateOf(false) }
  val searchHitsMap = remember { mutableStateMapOf<SearchHitKey, SearchHitSpan>() }
  var searchInProgress by remember { mutableStateOf(false) }
  var sortedHitsByLogIdsState by remember { mutableStateOf<List<SearchHitInfo>>(emptyList()) }
  var scrollSnapperVisible by remember { mutableStateOf(false) }
  var compactViewPreference = rememberBooleanSharedPreference(
    key = COMPACT_VIEW_KEY,
    default = false,
  )

  val scrollToTopInteractionSource = remember { MutableInteractionSource() }
  val scrollToBottomInteractionSource = remember { MutableInteractionSource() }

  LaunchedEffect(listState) {
    val scrollToBottomStateFlow = scrollToBottomInteractionSource.interactions.stateIn(
      scope = this,
      started = Eagerly,
      initialValue = null
    )
    val scrollToTopStateFlow = scrollToTopInteractionSource.interactions.stateIn(
      scope = this,
      started = Eagerly,
      initialValue = null
    )
    snapshotFlow {
      listState.firstVisibleItemScrollOffset
    }.drop(1).collectLatest {
      scrollSnapperVisible = true
      scrollToTopStateFlow.combine(
        scrollToBottomStateFlow,
        transform = { a, b ->
          a is PressInteraction.Press || b is PressInteraction.Press
        },
      ).collectLatest { isPressed ->
        if (!isPressed) {
          delay(SNAP_SCROLL_HIDE_DELAY_MS)
          scrollSnapperVisible = false
        }
      }
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      val fileName = remember(uri) { context.getFileNameFromUri(uri) }
      var showDropDownMenu by remember { mutableStateOf(false) }
      AppBar(
        title = fileName,
        subtitle = (logs as? LoadLogsState.Loaded)?.logs?.size?.toString(),
        compactViewEnabled = compactViewPreference.value,
        showDropDownMenu = showDropDownMenu,
        onClickSearch = {
          showSearchBar = true
        },
        onShowDropdownMenu = {
          showDropDownMenu = true
        },
        onDismissDropdownMenu = {
          showDropDownMenu = false
        },
        onClickCompactView = {
          showDropDownMenu = false
          compactViewPreference.value = !compactViewPreference.value
        }
      )
      AnimatedVisibility(
        visible = showSearchBar,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        SearchLogsTopBar(
          searchQuery = searchQuery,
          onQueryChange = { searchQuery = it },
          searchInProgress = searchInProgress,
          showHitCount = showHitCount,
          hitCount = searchHitsMap.size,
          currentHitIndex = currentSearchHitIndex,
          onClose = {
            showSearchBar = false
            searchHitsMap.clear()
            sortedHitsByLogIdsState = emptyList()
            currentSearchHitIndex = -1
            currentSearchHitLogId = -1
            focusManager.clearFocus()
            searchQuery = ""
          },
          onPrevious = {
            focusManager.clearFocus()
            if (currentSearchHitIndex - 1 >= 0) {
              currentSearchHitIndex -= 1
            } else {
              currentSearchHitIndex = searchHitsMap.size - 1
            }
          },
          onNext = {
            focusManager.clearFocus()
            currentSearchHitIndex = (currentSearchHitIndex + 1) % searchHitsMap.size
          },
          regexEnabled = useRegexForSearch,
          regexError = searchRegexError,
          onClickRegex = {
            useRegexForSearch = !useRegexForSearch
            if (!useRegexForSearch) {
              searchRegexError = false
            }
          },
        )
      }
    },
    floatingActionButton = {
      val coroutineScope = rememberCoroutineScope()
      FloatingActionButtons(
        visible = scrollSnapperVisible,
        scrollToBottomInteractionSource = scrollToBottomInteractionSource,
        scrollToTopInteractionSource = scrollToTopInteractionSource,
        onClickScrollToTop = {
          coroutineScope.launch {
            listState.scrollToItem(0)
          }
        },
        onClickScrollToBottom = {
          coroutineScope.launch {
            if (listState.layoutInfo.totalItemsCount > 0) {
              listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
          }
        }
      )
    }
  ) { innerPadding ->
    val logsState = logs
    val appInfoMap = rememberAppInfoByUidMap()
    if (logsState is LoadLogsState.Loaded) {
      if (showSearchBar) {
        val logs = logsState.logs
        LaunchedEffect(logs) {
          snapshotFlow { Pair(searchQuery, useRegexForSearch) }
            .collectLatest { (searchQuery, useRegex) ->
              delay(100L)
              showHitCount = searchQuery.isNotEmpty()
              if (searchQuery.isNotEmpty()) {
                searchInProgress = true
                val searchRegex = if (useRegex) {
                  withContext(Dispatchers.Default) {
                    searchQuery.toRegexOrNull()
                  }.also { searchRegex ->
                    searchRegexError = searchRegex == null
                  }
                } else {
                  null
                }
                val (map, sortedHitsByLogId) = when {
                  useRegex && searchRegex == null -> {
                    Pair(emptyMap(), emptyList())
                  }
                  searchRegex != null -> {
                    searchLogs(
                      logs = logs,
                      appInfoMap = appInfoMap.orEmpty(),
                      searchRegex = searchRegex,
                    )
                  }
                  else -> {
                    searchLogs(
                      logs = logs,
                      appInfoMap = appInfoMap.orEmpty(),
                      searchQuery = searchQuery,
                    )
                  }
                }
                searchHitsMap.clear()
                searchHitsMap.putAll(map)
                sortedHitsByLogIdsState = sortedHitsByLogId
                if (sortedHitsByLogIdsState.isNotEmpty()) {
                  currentSearchHitIndex = 0
                  currentSearchHitLogId = sortedHitsByLogIdsState.first().logId
                } else {
                  currentSearchHitIndex = -1
                  currentSearchHitLogId = -1
                }
              } else {
                searchRegexError = false
                searchHitsMap.clear()
                sortedHitsByLogIdsState = emptyList()
                currentSearchHitIndex = -1
                currentSearchHitLogId = -1
              }
              searchInProgress = false
            }
        }
        if (searchQuery.isNotEmpty()) {
          LaunchedEffect(listState, searchQuery) {
            snapshotFlow { sortedHitsByLogIdsState to currentSearchHitIndex }
              .filter { (_, index) -> index != -1 }
              .distinctUntilChangedBy { (_, index) -> index }
              .collectLatest { (hits, index) ->
                if (index < hits.size) {
                  currentSearchHitLogId = hits[index].logId
                  val scrollIndex = hits[index].index
                  if (scrollIndex != -1 && scrollIndex < listState.layoutInfo.totalItemsCount) {
                    listState.scrollToItem(scrollIndex)
                  }
                }
              }
          }
        }
      }

      var showCopyToClipboardSheet by remember { mutableStateOf<Log?>(null) }

      LogsList(
        modifier = Modifier
          .fillMaxSize()
          .consumeWindowInsets(innerPadding),
        contentPadding = innerPadding,
        state = listState,
        searchHits = searchHitsMap,
        listStyle = if (!showSearchBar && compactViewPreference.value) {
          LogsListStyle.Compact
        } else {
          LogsListStyle.Default
        },
        logs = logsState.logs,
        appInfoMap = appInfoMap.orEmpty(),
        currentSearchHitLogId = currentSearchHitLogId,
        onClick = if (!compactViewPreference.value) {
          { index ->
            showCopyToClipboardSheet = logsState.logs[index]
          }
        } else null,
        onLongClick = if (compactViewPreference.value) {
          { index ->
            showCopyToClipboardSheet = logsState.logs[index]
          }
        } else null
      )

      showCopyToClipboardSheet?.let { log ->
        CopyLogClipboardBottomSheet(
          log = log,
          onDismiss = { showCopyToClipboardSheet = null },
        )
      }
    } else if (logsState is LoadLogsState.Loading) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
          .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator(
          modifier = Modifier.size(48.dp)
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(
  title: String,
  subtitle: String?,
  compactViewEnabled: Boolean,
  showDropDownMenu: Boolean,
  onClickSearch: () -> Unit,
  onShowDropdownMenu: () -> Unit,
  onDismissDropdownMenu: () -> Unit,
  onClickCompactView: () -> Unit,
) {
  val context = LocalContext.current
  TopAppBar(
    navigationIcon = {
      val insetPadding = WindowInsets.displayCutout
        .only(WindowInsetsSides.Left)
        .asPaddingValues()
      WithTooltip(
        modifier = Modifier.padding(insetPadding),
        text = stringResource(R.string.navigate_up),
      ) {
        IconButton(
          onClick = {
            context.findActivity()?.finish()
          },
          colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Default.ArrowBack,
            contentDescription = null,
          )
        }
      }
    },
    title = {
      Column {
        Text(
          text = title,
          overflow = TextOverflow.Ellipsis,
          maxLines = 1,
        )
        if (subtitle != null) {
          Text(
            text = subtitle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = AppTypography.titleSmall,
          )
        }
      }
    },
    actions = {
      Row(
        modifier = Modifier.displayCutoutPadding()
      ) {
        WithTooltip(
          text = stringResource(R.string.search),
        ) {
          IconButton(
            onClick = onClickSearch,
            colors = IconButtonDefaults.iconButtonColors(
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          ) {
            Icon(Icons.Default.Search, contentDescription = null)
          }
        }
        WithTooltip(
          text = stringResource(R.string.more_options),
        ) {
          IconButton(
            onClick = onShowDropdownMenu,
            colors = IconButtonDefaults.iconButtonColors(
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
          ) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
          }
        }
        DropdownMenu(
          expanded = showDropDownMenu,
          onDismissRequest = onDismissDropdownMenu,
        ) {
          DropdownMenuItem(
            leadingIcon = {
              Icon(Icons.Default.ViewCompact, contentDescription = null)
            },
            text = {
              Text(
                text = stringResource(R.string.compact_view),
              )
            },
            trailingIcon = {
              Checkbox(checked = compactViewEnabled, onCheckedChange = null)
            },
            onClick = onClickCompactView,
          )
        }
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
  )
}

@Composable
private fun FloatingActionButtons(
  visible: Boolean,
  scrollToTopInteractionSource: MutableInteractionSource,
  scrollToBottomInteractionSource: MutableInteractionSource,
  onClickScrollToTop: () -> Unit,
  onClickScrollToBottom: () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
  ) {
    val insetsPadding = WindowInsets.systemBars
      .union(WindowInsets.displayCutout)
      .only(WindowInsetsSides.Left + WindowInsetsSides.Right)
      .asPaddingValues()
    Column(
      modifier = Modifier.padding(insetsPadding)
    ) {
      FloatingActionButton(
        modifier = Modifier.size(48.dp),
        onClick = onClickScrollToTop,
        interactionSource = scrollToTopInteractionSource,
      ) {
        Icon(
          imageVector = Icons.Filled.ArrowUpward,
          contentDescription = null
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      FloatingActionButton(
        modifier = Modifier.size(48.dp),
        onClick = onClickScrollToBottom,
        interactionSource = scrollToBottomInteractionSource,
      ) {
        Icon(
          imageVector = Icons.Filled.ArrowDownward,
          contentDescription = null
        )
      }
    }
  }
}

private suspend fun loadLogs(
  context: Context,
  uri: Uri,
) = withContext(Dispatchers.IO) {
  try {
    val stream = context.contentResolver.openInputStream(uri)
    if (stream != null) {
      try {
        val bytes = stream.available()
        val logs = mutableListOf<Log>()
        val reader = LogcatStreamReader(stream)
        for (log in reader) {
          logs += log
        }

        if (logs.isEmpty() && bytes > 0) {
          LoadLogsState.LogsParseError
        } else {
          LoadLogsState.Loaded(logs = logs, bytes = bytes)
        }
      } catch (_: IOException) {
        LoadLogsState.LogsParseError
      } finally {
        stream.closeQuietly()
      }
    } else {
      LoadLogsState.FileOpenError
    }
  } catch (_: Exception) {
    LoadLogsState.FileOpenError
  }
}

sealed interface LoadLogsState {
  data object Loading : LoadLogsState
  data object FileOpenError : LoadLogsState
  data object LogsParseError : LoadLogsState
  data class Loaded(
    val logs: List<Log>,
    val bytes: Int,
  ) : LoadLogsState
}
