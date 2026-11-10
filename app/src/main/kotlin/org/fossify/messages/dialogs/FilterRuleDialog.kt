package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogAddBlockedKeywordBinding
import org.fossify.messages.databinding.DialogFilterRuleBinding
import org.fossify.messages.helpers.filterRulesDB
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule

/** Adds a filter rule, or edits or deletes [rule]. [callback] runs after a change is saved. */
class FilterRuleDialog(
    private val activity: BaseSimpleActivity,
    private val rule: FilterRule? = null,
    private val callback: () -> Unit
) {
    private val binding = DialogFilterRuleBinding.inflate(activity.layoutInflater)
    private var folders = emptyList<FilterFolder>()
    private var folderId = rule?.folderId

    init {
        ensureBackgroundThread {
            folders = activity.filterRulesDB.getFolders()
            activity.runOnUiThread { show() }
        }
    }

    private fun show() {
        binding.apply {
            filterRuleSender.setText(rule?.sender)
            filterRuleKeywords.setText(rule?.keywords)
            filterRuleNotify.isChecked = rule?.notify == true
            filterRuleFolder.setOnClickListener { chooseFolder() }
        }
        showFolder()

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { if (rule != null) setNeutralButton(org.fossify.commons.R.string.delete, null) }
            .apply {
                val titleId = if (rule == null) R.string.add_filter_rule else R.string.filter_rules
                activity.setupDialogStuff(binding.root, this, titleId) { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(dialog) }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { delete(dialog) }
                }
            }
    }

    private fun showFolder() {
        binding.filterRuleFolder.setText(folders.firstOrNull { it.id == folderId }?.name.orEmpty())
    }

    private fun chooseFolder() {
        val items = folders.mapTo(ArrayList()) { RadioItem(it.id.toInt(), it.name) }
        items += RadioItem(NEW_FOLDER, activity.getString(R.string.new_folder))
        RadioGroupDialog(activity, items, folderId?.toInt() ?: 0, R.string.filter_rule_folder) {
            if (it == NEW_FOLDER) {
                createFolder()
            } else {
                folderId = (it as Int).toLong()
                showFolder()
            }
        }
    }

    private fun createFolder() {
        val nameBinding = DialogAddBlockedKeywordBinding.inflate(activity.layoutInflater)
        nameBinding.addBlockedKeywordHint.hint = activity.getString(R.string.folder_name)
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(nameBinding.root, this, R.string.new_folder) { dialog ->
                    dialog.showKeyboard(nameBinding.addBlockedKeywordEdittext)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameBinding.addBlockedKeywordEdittext.value.trim()
                        if (name.isNotEmpty()) {
                            dialog.dismiss()
                            addFolder(name)
                        }
                    }
                }
            }
    }

    private fun addFolder(name: String) {
        ensureBackgroundThread {
            val id = activity.filterRulesDB.insertFolder(FilterFolder(name = name))
            folders = activity.filterRulesDB.getFolders()
            activity.runOnUiThread {
                folderId = id
                showFolder()
            }
        }
    }

    private fun save(dialog: AlertDialog) {
        val sender = binding.filterRuleSender.value.trim()
        val keywords = binding.filterRuleKeywords.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val chosenFolder = folderId
        when {
            chosenFolder == null -> activity.toast(R.string.filter_rule_needs_folder)
            sender.isEmpty() && keywords.isEmpty() -> activity.toast(R.string.filter_rule_needs_sender_or_keyword)
            else -> ensureBackgroundThread {
                val position = rule?.position ?: activity.filterRulesDB.getRules().size
                activity.filterRulesDB.insertRule(
                    FilterRule(
                        id = rule?.id ?: 0,
                        position = position,
                        folderId = chosenFolder,
                        sender = sender,
                        keywords = keywords.joinToString("\n"),
                        notify = binding.filterRuleNotify.isChecked
                    )
                )
                activity.runOnUiThread {
                    dialog.dismiss()
                    callback()
                }
            }
        }
    }

    private fun delete(dialog: AlertDialog) {
        val rule = rule ?: return
        ConfirmationDialog(activity) {
            ensureBackgroundThread {
                activity.filterRulesDB.deleteRule(rule)
                activity.runOnUiThread {
                    dialog.dismiss()
                    callback()
                }
            }
        }
    }

    companion object {
        private const val NEW_FOLDER = -1
    }
}
