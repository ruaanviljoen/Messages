package org.fossify.messages.dialogs

import android.app.Activity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.helpers.FilterRun
import org.fossify.messages.helpers.applyFilterRun
import org.fossify.messages.helpers.filterRulesDB
import org.fossify.messages.helpers.getFolderNames
import org.fossify.messages.helpers.planFilterRun

/**
 * Works out what Spam and the filter rules would change in the messages already on the phone, shows the counts and
 * applies them if the user confirms. [isCurrent] lets the caller drop a run that settings changed since started.
 */
class FilterRunDialog(
    private val activity: Activity,
    private val showIfNoChanges: Boolean = false,
    private val isCurrent: () -> Boolean = { true }
) {
    init {
        if (showIfNoChanges) {
            activity.toast(R.string.checking_messages)
        }

        ensureBackgroundThread {
            val run = activity.planFilterRun()
            val description = describe(run)
            activity.runOnUiThread {
                if (!isCurrent() || activity.isFinishing) {
                    return@runOnUiThread
                } else if (run.hasChanges) {
                    ConfirmationDialog(
                        activity = activity,
                        message = description,
                        positive = R.string.apply,
                        negative = org.fossify.commons.R.string.cancel
                    ) {
                        ensureBackgroundThread { activity.applyFilterRun(run) }
                    }
                } else if (showIfNoChanges) {
                    activity.toast(R.string.spam_filter_no_changes)
                }
            }
        }
    }

    // Reads the rules and folder names, so it runs off the main thread.
    private fun describe(run: FilterRun): String = with(activity) {
        val folders = getFolderNames()
        val keywordLines = run.keywordCounts.entries.sortedByDescending { it.value }
            .joinToString("\n") { getString(R.string.spam_keyword_count, it.key, it.value) }
        val ruleLines = filterRulesDB.getRules().filter { it.id in run.ruleCounts }.joinToString("\n") {
            getString(R.string.filter_rule_count, folders[it.folderId].orEmpty(), it.describe(), run.ruleCounts[it.id])
        }

        return buildString {
            append(getString(R.string.filter_run_summary, run.toFolders.size, run.toInbox.size, run.toDelete.size))
            if (keywordLines.isNotEmpty()) {
                append("\n\n").append(getString(R.string.spam_keyword_counts)).append("\n").append(keywordLines)
            }
            if (ruleLines.isNotEmpty()) {
                append("\n\n").append(getString(R.string.filter_rule_counts)).append("\n").append(ruleLines)
            }
        }
    }
}
