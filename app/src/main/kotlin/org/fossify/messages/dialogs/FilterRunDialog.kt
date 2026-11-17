package org.fossify.messages.dialogs

import android.app.Activity
import org.fossify.commons.databinding.DialogMessageBinding
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
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
                    confirm(run, description)
                } else if (showIfNoChanges) {
                    activity.toast(R.string.spam_filter_no_changes)
                }
            }
        }
    }

    // Deleting is permanent, so a run that deletes can also be applied without deleting.
    private fun confirm(run: FilterRun, description: String) {
        val view = DialogMessageBinding.inflate(activity.layoutInflater).apply { message.text = description }
        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.apply) { _, _ -> apply(run) }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        if (run.toExpire.isNotEmpty()) {
            builder.setNeutralButton(R.string.apply_without_deleting) { _, _ -> apply(run.withoutDeleting()) }
        }
        activity.setupDialogStuff(view.root, builder)
    }

    private fun apply(run: FilterRun) = ensureBackgroundThread { activity.applyFilterRun(run) }

    // Reads the rules and folder names, so it runs off the main thread.
    private fun describe(run: FilterRun): String = with(activity) {
        val folders = getFolderNames()
        val lines = arrayListOf(getString(R.string.filter_run_question), "")
        listOf(
            R.string.filter_run_move to run.toFolders.size,
            R.string.filter_run_inbox to run.toInbox.size,
            R.string.filter_run_expire to run.toExpire.size,
            R.string.filter_run_expire_from_inbox to run.expiringFromInbox,
            R.string.filter_run_archive to run.toArchive.distinctBy { it.threadId }.size,
            R.string.filter_run_unarchive to run.toUnarchive.size
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
