package org.fossify.messages.dialogs

import android.app.Activity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.helpers.FilterMatches
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
        val lines = arrayListOf(getString(R.string.filter_run_question), "")
        listOf(
            R.string.filter_run_move to run.toFolders.size,
            R.string.filter_run_inbox to run.toInbox.size,
            R.string.filter_run_delete to run.toDelete.size
        ).filter { it.second > 0 }.forEach { lines += getString(it.first, it.second) }

        // the rules in the order they run, Spam first, each named after its folder
        val filters = filterRulesDB.getRules().mapNotNull { rule ->
            run.ruleMatches[rule.id]?.let { folders[rule.folderId].orEmpty() to it }
        }
        val matching = filters.filter { it.second.total > 0 }
        if (matching.isNotEmpty()) {
            lines += ""
            lines += getString(R.string.filter_run_matches)
            matching.forEach { (name, matches) -> lines += describe(name, matches) }
        }
        return lines.joinToString("\n")
    }

    // A filter's line with its total, then a line for each of its keywords, most matches first.
    private fun describe(name: String, matches: FilterMatches): List<String> = with(activity) {
        listOf(getString(R.string.filter_run_filter, name, matches.total)) +
            matches.keywordCounts.entries.sortedByDescending { it.value }
                .map { getString(R.string.filter_run_keyword, it.key, it.value) }
    }
}
