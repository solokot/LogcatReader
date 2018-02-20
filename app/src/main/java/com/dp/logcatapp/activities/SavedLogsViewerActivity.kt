package com.dp.logcatapp.activities

import android.os.Bundle
import com.dp.logcatapp.R
import com.dp.logcatapp.fragments.savedlogsviewer.SavedLogsViewerFragment
import com.dp.logcatapp.util.getFileNameFromUri

class SavedLogsViewerActivity : BaseActivityWithToolbar() {

    companion object {
        val TAG = SavedLogsViewerActivity::class.qualifiedName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_logs_viewer)
        setupToolbar()
        enableDisplayHomeAsUp()

        if (intent.data == null) {
            finish()
            return
        }

        toolbar.title = getFileNameFromUri(intent.data)

        if (savedInstanceState == null) {
            val frag = SavedLogsViewerFragment.newInstance(intent.data)
            supportFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, frag, SavedLogsViewerFragment.TAG)
                    .commit()
        }
    }

    override fun getToolbarIdRes(): Int = R.id.toolbar

    override fun getToolbarTitle(): String = getString(R.string.saved_logs)
}