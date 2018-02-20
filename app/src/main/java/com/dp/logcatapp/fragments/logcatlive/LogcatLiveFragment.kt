package com.dp.logcatapp.fragments.logcatlive

import android.Manifest
import android.arch.lifecycle.ViewModelProviders
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.support.v7.preference.PreferenceManager
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.*
import androidx.content.edit
import com.dp.logcat.Log
import com.dp.logcat.Logcat
import com.dp.logcat.LogcatEventListener
import com.dp.logcat.LogcatFilter
import com.dp.logcatapp.R
import com.dp.logcatapp.activities.BaseActivityWithToolbar
import com.dp.logcatapp.activities.SavedLogsActivity
import com.dp.logcatapp.activities.SavedLogsViewerActivity
import com.dp.logcatapp.fragments.base.BaseFragment
import com.dp.logcatapp.fragments.logcatlive.dialogs.FilterDialogFragment
import com.dp.logcatapp.fragments.logcatlive.dialogs.InstructionToGrantPermissionDialogFragment
import com.dp.logcatapp.fragments.shared.dialogs.CopyToClipboardDialogFragment
import com.dp.logcatapp.services.LogcatService
import com.dp.logcatapp.util.*
import com.dp.logger.MyLogger
import kotlinx.android.synthetic.main.app_bar.*
import java.io.File
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*

class LogcatLiveFragment : BaseFragment(), ServiceConnection, LogcatEventListener {
    companion object {
        val TAG = LogcatLiveFragment::class.qualifiedName
        val LOGCAT_DIR = File(Environment.getExternalStoragePublicDirectory(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                    Environment.DIRECTORY_DOCUMENTS
                else
                    "Documents"
        ), "Logcat")

        private const val FILTER_MSG = "msg"
        private const val MY_PERMISSION_REQ_WRITE_EXTERNAL_STORAGE = 1
        private const val LOG_PRIORITY_FILTER = "logPriorityFilter"
        private const val LOG_KEYWORD_FILTER = "logKeywordFilter"

        private val STOP_RECORDING = TAG + "_stop_recording"
        private val KEY_FILTER_PRIORITES = TAG + "_key_filter_priorites"
        private val KEY_FILTER_KEYWORD = TAG + "_key_filter_keyword"

        fun newInstance(stopRecording: Boolean): LogcatLiveFragment {
            val bundle = Bundle()
            bundle.putBoolean(STOP_RECORDING, stopRecording)
            val frag = LogcatLiveFragment()
            frag.arguments = bundle
            return frag
        }
    }

    private lateinit var serviceBinder: ServiceBinder
    private lateinit var recyclerView: RecyclerView
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var viewModel: LogcatLiveViewModel
    private lateinit var adapter: MyRecyclerViewAdapter
    private lateinit var fabUp: FloatingActionButton
    private lateinit var fabDown: FloatingActionButton
    private var logcatService: LogcatService? = null
    private var ignoreScrollEvent = false
    private var searchViewActive = false
    private var lastLogId = -1
    private var pendingLogsToSave: List<Log>? = null
    private var lastSearchRunnable: Runnable? = null
    private var searchTask: SearchTask? = null

    private val hideFabUpRunnable: Runnable = Runnable {
        fabUp.hide()
    }

    private val hideFabDownRunnable: Runnable = Runnable {
        fabDown.hide()
    }

    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        var lastDy = 0

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy > 0 && lastDy <= 0) {
                hideFabUp()
                showFabDown()
            } else if (dy < 0 && lastDy >= 0) {
                showFabUp()
                hideFabDown()
            }
            lastDy = dy
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    viewModel.autoScroll = false
                    if (lastDy > 0) {
                        hideFabUp()
                        showFabDown()
                    } else if (lastDy < 0) {
                        showFabUp()
                        hideFabDown()
                    }
                }
                else -> {
                    var firstPos = -1
                    if (searchViewActive && !viewModel.autoScroll &&
                            newState == RecyclerView.SCROLL_STATE_IDLE) {
                        firstPos = linearLayoutManager.findFirstCompletelyVisibleItemPosition()
                        if (firstPos != RecyclerView.NO_POSITION) {
                            val log = adapter[firstPos]
                            lastLogId = log.id
                        }
                    }

                    val pos = linearLayoutManager.findLastCompletelyVisibleItemPosition()
                    if (pos == RecyclerView.NO_POSITION) {
                        viewModel.autoScroll = false
                        return
                    }

                    if (ignoreScrollEvent) {
                        if (pos == adapter.itemCount) {
                            ignoreScrollEvent = false
                        }
                        return
                    }

                    if (pos == 0) {
                        hideFabUp()
                    }

                    if (firstPos == RecyclerView.NO_POSITION) {
                        firstPos = linearLayoutManager.findFirstCompletelyVisibleItemPosition()
                    }

                    viewModel.scrollPosition = firstPos
                    viewModel.autoScroll = pos == adapter.itemCount - 1

                    if (viewModel.autoScroll) {
                        hideFabUp()
                        hideFabDown()
                    }
                }
            }
        }
    }

    private fun showFabUp() {
        handler.removeCallbacks(hideFabUpRunnable)
        fabUp.show()
        handler.postDelayed(hideFabUpRunnable, 2000)
    }

    private fun hideFabUp() {
        handler.removeCallbacks(hideFabUpRunnable)
        fabUp.hide()
    }

    private fun showFabDown() {
        handler.removeCallbacks(hideFabDownRunnable)
        fabDown.show()
        handler.postDelayed(hideFabDownRunnable, 2000)
    }

    private fun hideFabDown() {
        handler.removeCallbacks(hideFabDownRunnable)
        fabDown.hide()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProviders.of(this)
                .get(LogcatLiveViewModel::class.java)
        adapter = MyRecyclerViewAdapter(activity!!)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        serviceBinder = ServiceBinder(LogcatService::class.java, this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflateLayout(R.layout.fragment_logcat_live)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        linearLayoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.itemAnimator = null
        recyclerView.addItemDecoration(DividerItemDecoration(activity,
                linearLayoutManager.orientation))
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(onScrollListener)

        fabDown = view.findViewById(R.id.fabDown)
        fabDown.setOnClickListener {
            logcatService?.logcat?.pause()
            hideFabDown()
            ignoreScrollEvent = true
            viewModel.autoScroll = true
            linearLayoutManager.scrollToPosition(adapter.itemCount - 1)
            resumeLogcat()
        }

        fabUp = view.findViewById(R.id.fabUp)
        fabUp.setOnClickListener {
            logcatService?.logcat?.pause()
            hideFabUp()
            viewModel.autoScroll = false
            linearLayoutManager.scrollToPositionWithOffset(0, 0)
            resumeLogcat()
        }

        hideFabUp()
        hideFabDown()

        adapter.setOnClickListener { v ->
            val pos = linearLayoutManager.getPosition(v)
            if (pos >= 0) {
                viewModel.autoScroll = false
                val log = adapter[pos]
                CopyToClipboardDialogFragment.newInstance(log)
                        .show(fragmentManager, CopyToClipboardDialogFragment.TAG)
            }
        }

        if (!checkReadLogsPermission() && !viewModel.showedGrantPermissionInstruction) {
            viewModel.showedGrantPermissionInstruction = true
            InstructionToGrantPermissionDialogFragment().show(fragmentManager,
                    InstructionToGrantPermissionDialogFragment.TAG)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.logcat_live, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        var reachedBlank = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                searchViewActive = true
                removeLastSearchRunnableCallback()

                if (newText.isBlank()) {
                    reachedBlank = true
                    onSearchViewClose()
                } else {
                    reachedBlank = false
                    val logcat = logcatService?.logcat ?: return true

                    lastSearchRunnable = Runnable {
                        onSearchAction(logcat, newText)
                    }

                    handler.postDelayed(lastSearchRunnable, 300)
                }
                return true
            }

            override fun onQueryTextSubmit(query: String) = false
        })

        val playPauseItem = menu.findItem(R.id.action_play_pause)
        val recordToggleItem = menu.findItem(R.id.action_record_toggle)

        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            playPauseItem.isVisible = !hasFocus
            recordToggleItem.isVisible = !hasFocus
        }

        searchView.setOnCloseListener {
            removeLastSearchRunnableCallback()
            searchViewActive = false
            if (!reachedBlank) {
                onSearchViewClose()
            }
            playPauseItem.isVisible = true
            recordToggleItem.isVisible = true
            false
        }
    }

    private fun onSearchAction(logcat: Logcat, newText: String) {
        MyLogger.logDebug(LogcatLiveFragment::class, "onSearchAction: $newText")
        searchTask?.cancel(true)
        searchTask = SearchTask(this, logcat, newText)
        searchTask!!.execute()
    }

    private fun onSearchViewClose() {
        val logcat = logcatService?.logcat ?: return
        logcat.pause()
        logcat.removeFilter(FILTER_MSG)

        adapter.clear()
        addAllLogs(logcat.getLogsFiltered())
        if (lastLogId == -1) {
            scrollRecyclerView()
        } else {
            viewModel.autoScroll = linearLayoutManager.findLastCompletelyVisibleItemPosition() ==
                    adapter.itemCount - 1
            if (!viewModel.autoScroll) {
                viewModel.scrollPosition = lastLogId
                linearLayoutManager.scrollToPositionWithOffset(lastLogId, 0)
            }
            lastLogId = -1
        }

        resumeLogcat()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // do nothing

        val playPauseItem = menu.findItem(R.id.action_play_pause)
        val recordToggleItem = menu.findItem(R.id.action_record_toggle)

        if (logcatService?.paused == true) {
            playPauseItem.icon = ContextCompat.getDrawable(activity!!,
                    R.drawable.ic_play_arrow_white_24dp)
            playPauseItem.title = getString(R.string.resume)
        } else {
            playPauseItem.icon = ContextCompat.getDrawable(activity!!,
                    R.drawable.ic_pause_white_24dp)
            playPauseItem.title = getString(R.string.pause)

            if (logcatService?.recording == true) {
                recordToggleItem.icon = ContextCompat.getDrawable(activity!!,
                        R.drawable.ic_stop_white_24dp)
                recordToggleItem.title = getString(R.string.stop_recording)
            } else {
                recordToggleItem.icon = ContextCompat.getDrawable(activity!!,
                        R.drawable.ic_fiber_manual_record_white_24dp)
                recordToggleItem.title = getString(R.string.start_recording)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                true
            }
            R.id.action_play_pause -> {
                val logcat = logcatService?.logcat
                if (logcat != null) {
                    val newPausedState = !logcatService!!.paused
                    if (newPausedState) {
                        logcat.pause()
                    } else {
                        logcat.resume()
                    }
                    logcatService!!.paused = newPausedState
                    activity?.invalidateOptionsMenu()
                }
                true
            }
            R.id.action_record_toggle -> {
                val recording = !logcatService!!.recording
                logcatService?.updateNotification(recording)
                val logcat = logcatService?.logcat
                if (logcat != null) {
                    if (recording) {
                        Snackbar.make(view!!, getString(R.string.started_recording),
                                Snackbar.LENGTH_SHORT)
                                .show()
                        logcat.startRecording()
                    } else {
                        val logs = logcat.stopRecording()

                        trySaveToFile(logs)
                    }
                    logcatService!!.recording = recording
                    activity?.invalidateOptionsMenu()
                }
                true
            }
            R.id.filter_action -> {
                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
                val keyword = sharedPreferences.getString(KEY_FILTER_KEYWORD, "")
                val logPriorites = sharedPreferences.getStringSet(KEY_FILTER_PRIORITES, setOf())
                val frag = FilterDialogFragment.newInstance(keyword, logPriorites)
                frag.setTargetFragment(this, 0)
                frag.show(fragmentManager, FilterDialogFragment.TAG)
                true
            }
            R.id.action_save -> {
                trySaveToFile()
                true
            }
            R.id.action_view_saved_logs -> {
                startActivity(Intent(activity, SavedLogsActivity::class.java))
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    fun setFilterAndSave(keyword: String, logPriorities: Set<String>) {
        val logcat = logcatService?.logcat
        if (logcat != null) {
            logcat.pause()

            if (logPriorities.isNotEmpty()) {
                logcat.addFilter(LOG_PRIORITY_FILTER, LogPriorityFilter(logPriorities))
            } else {
                logcat.removeFilter(LOG_PRIORITY_FILTER)
            }

            if (keyword.isNotEmpty()) {
                logcat.addFilter(LOG_KEYWORD_FILTER, LogKeywordFilter(keyword))
            } else {
                logcat.removeFilter(LOG_KEYWORD_FILTER)
            }

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            sharedPreferences.edit {
                putString(KEY_FILTER_KEYWORD, keyword)
                putStringSet(KEY_FILTER_PRIORITES, logPriorities)
            }

            adapter.clear()
            adapter.addItems(logcat.getLogsFiltered())
            updateToolbarSubtitle(adapter.itemCount)
            scrollRecyclerView()
            resumeLogcat()
        }
    }

    fun tryStopRecording() {
        viewModel.stopRecording = true
        if (logcatService != null) {
            stopRecording()
        }
    }

    private fun stopRecording() {
        if (logcatService?.recording == true) {
            logcatService?.updateNotification(false)

            val logcat = logcatService?.logcat
            if (logcat != null) {
                val logs = logcat.stopRecording()
                trySaveToFile(logs)
            }

            logcatService!!.recording = false
            activity?.invalidateOptionsMenu()
        }
        viewModel.stopRecording = false
    }

    private fun actuallySaveToFile(logs: List<Log>, fileName: String): Boolean {
        if (logs.isEmpty()) {
            MyLogger.logDebug(LogcatLiveFragment::class, "Nothing to save")
            showSnackbar(view, getString(R.string.nothing_to_save))
            return true
        }

        if (isExternalStorageWritable()) {
            if (LOGCAT_DIR.exists() || LOGCAT_DIR.mkdirs()) {
                return Logcat.writeToFile(logs, File(LOGCAT_DIR, fileName))
            }
        } else {
            MyLogger.logDebug(LogcatLiveFragment::class, "External storage is not writable")
        }

        return false
    }

    private fun saveToFile(logs: List<Log>) {
        val timeStamp = SimpleDateFormat("MM-dd-yyyy_HH-mm-ss", Locale.getDefault())
                .format(Date())
        val fileName = "logcat_$timeStamp.txt"
        if (actuallySaveToFile(logs, fileName)) {
            newSnakcbar(view, getString(R.string.saved_as_filename).format(fileName), Snackbar.LENGTH_LONG)
                    ?.setAction(getString(R.string.view_log), {
                        if (!viewSavedLog(fileName)) {
                            showSnackbar(view, getString(R.string.could_not_open_log_file))
                        }
                    })?.show()
        } else {
            showSnackbar(view, getString(R.string.failed_to_save_logs))
        }
    }

    private fun viewSavedLog(fileName: String): Boolean {
        val path = File(LOGCAT_DIR, fileName)
        val uri = FileProvider.getUriForFile(context!!,
                context!!.applicationContext.packageName + ".provider", path)

        val intent = Intent(context, SavedLogsViewerActivity::class.java)
        intent.setDataAndType(uri, "text/plain")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
        return true
    }

    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun trySaveToFile(logs: List<Log>) {
        if (checkWriteExternalStoragePermission()) {
            saveToFile(logs)
        } else {
            pendingLogsToSave = logs
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MY_PERMISSION_REQ_WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun trySaveToFile() {
        val logcat = logcatService?.logcat
        if (logcat != null) {
            trySaveToFile(logcat.getLogsFiltered())
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSION_REQ_WRITE_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    saveToFile(pendingLogsToSave ?: emptyList())
                }
            }
        }
    }

    private fun checkReadLogsPermission() =
            ContextCompat.checkSelfPermission(activity!!,
                    Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    private fun checkWriteExternalStoragePermission() =
            ContextCompat.checkSelfPermission(activity!!,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onStart() {
        super.onStart()
        serviceBinder.bind(activity!!)
    }

    override fun onStop() {
        super.onStop()
        serviceBinder.unbind(activity!!)
    }

    private fun removeLastSearchRunnableCallback() {
        if (lastSearchRunnable != null) {
            handler.removeCallbacks(lastSearchRunnable)
            lastSearchRunnable = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeLastSearchRunnableCallback()
        searchTask?.cancel(true)

        recyclerView.removeOnScrollListener(onScrollListener)
        logcatService?.logcat?.setEventListener(null)
        logcatService?.logcat?.unbind(activity as AppCompatActivity)
        serviceBinder.close()
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        MyLogger.logDebug(LogcatLiveFragment::class, "onServiceConnected")
        logcatService = (service as LogcatService.LocalBinder).getLogcatService()
        val logcat = logcatService!!.logcat
        logcat.pause()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val keyword = sharedPreferences.getString(KEY_FILTER_KEYWORD, null)
        val logPriorites = sharedPreferences.getStringSet(KEY_FILTER_PRIORITES, null)

        if (keyword == null) {
            logcat.removeFilter(LOG_KEYWORD_FILTER)
        } else {
            logcat.addFilter(LOG_KEYWORD_FILTER, LogKeywordFilter(keyword))
        }

        if (logPriorites == null) {
            logcat.removeFilter(LOG_PRIORITY_FILTER)
        } else {
            logcat.addFilter(LOG_PRIORITY_FILTER, LogPriorityFilter(logPriorites))
        }

        if (adapter.itemCount == 0) {
            MyLogger.logDebug(LogcatLiveFragment::class, "Added all logs")
            addAllLogs(logcat.getLogsFiltered())
        } else if (logcatService!!.restartedLogcat) {
            MyLogger.logDebug(LogcatLiveFragment::class, "Logcat restarted")
            logcatService!!.restartedLogcat = false
            adapter.clear()
        }

        scrollRecyclerView()

        logcat.setEventListener(this)
        resumeLogcat()

        if (!logcat.isBound) {
            logcat.bind(activity as AppCompatActivity)
        }

        if (viewModel.stopRecording || arguments?.getBoolean(STOP_RECORDING) == true) {
            arguments?.putBoolean(STOP_RECORDING, false)
            stopRecording()
        }
    }

    override fun onLogEvent(log: Log) {
        adapter.addItem(log)
        updateUIOnLogEvent(adapter.itemCount)
    }

    override fun onLogEvents(logs: List<Log>) {
        adapter.addItems(logs)
        updateUIOnLogEvent(adapter.itemCount)
    }

    private fun addAllLogs(logs: List<Log>) {
        adapter.addItems(logs)
        updateToolbarSubtitle(adapter.itemCount)
    }

    private fun scrollRecyclerView() {
        if (viewModel.autoScroll) {
            linearLayoutManager.scrollToPosition(adapter.itemCount - 1)
        } else {
            linearLayoutManager.scrollToPositionWithOffset(viewModel.scrollPosition, 0)
        }
    }

    private fun updateToolbarSubtitle(count: Int) {
        if (count > 1) {
            (activity as BaseActivityWithToolbar).toolbar.subtitle = "$count"
        } else {
            (activity as BaseActivityWithToolbar).toolbar.subtitle = null
        }
    }

    private fun resumeLogcat() {
        if (logcatService != null && !logcatService!!.paused) {
            logcatService?.logcat?.resume()
        }
    }

    private fun updateUIOnLogEvent(count: Int) {
        updateToolbarSubtitle(count)
        if (viewModel.autoScroll) {
            linearLayoutManager.scrollToPosition(count - 1)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        MyLogger.logDebug(LogcatLiveFragment::class, "onServiceDisconnected")
        logcatService = null
    }

    private class SearchTask(fragment: LogcatLiveFragment,
                             val logcat: Logcat, val searchText: String) :
            AsyncTask<String, Void, List<Log>>() {

        private var fragRef: WeakReference<LogcatLiveFragment> = WeakReference(fragment)

        override fun onPreExecute() {
            logcat.pause()
            logcat.addFilter(FILTER_MSG, object : LogcatFilter {
                override fun filter(log: Log): Boolean {
                    return log.tag.containsIgnoreCase(searchText) ||
                            log.msg.containsIgnoreCase(searchText)
                }
            })
        }

        override fun doInBackground(vararg params: String?): List<Log> =
                logcat.getLogsFiltered()

        override fun onCancelled(result: List<Log>?) {
            fragRef.get()?.resumeLogcat()
        }

        override fun onPostExecute(result: List<Log>?) {
            val frag = fragRef.get() ?: return
            if (result != null) {
                frag.adapter.clear()
                frag.adapter.addItems(result)
                frag.viewModel.autoScroll = false
                frag.linearLayoutManager.scrollToPositionWithOffset(0, 0)
            }
        }
    }

    private class LogPriorityFilter(val priorities: Set<String>) : LogcatFilter {
        override fun filter(log: Log): Boolean {
            return priorities.isEmpty() || priorities.contains(log.priority)
        }
    }

    private class LogKeywordFilter(val keyword: String) : LogcatFilter {
        override fun filter(log: Log): Boolean {
            return keyword.isEmpty() || log.tag.containsIgnoreCase(keyword) ||
                    log.msg.containsIgnoreCase(keyword)
        }
    }
}