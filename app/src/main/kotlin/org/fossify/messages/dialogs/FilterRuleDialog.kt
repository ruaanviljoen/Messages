package org.fossify.messages.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beGoneIf
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
import org.fossify.messages.helpers.SPAM_ACTION_DELETE
import org.fossify.messages.helpers.SPAM_ACTION_MOVE
import org.fossify.messages.helpers.addContactPicker
import org.fossify.messages.helpers.filterRulesDB
import org.fossify.messages.models.FilterFolder
import org.fossify.messages.models.FilterRule
import org.fossify.messages.models.SPAM_FOLDER_ID

/**
 * Adds a filter rule, or edits or deletes [rule]. The Spam rule is edited here too: its action is "Move to Spam" or
 * "Delete", and it cannot be deleted. [callback] runs after a change is saved.
 */
class FilterRuleDialog(
    private val activity: BaseSimpleActivity,
    private val rule: FilterRule? = null,
    private val callback: () -> Unit
) {
    private val binding = DialogFilterRuleBinding.inflate(activity.layoutInflater)
    private val isSpam = rule?.isSpam == true
    private var folders = emptyList<FilterFolder>()
    private var folderId = rule?.folderId
    private var deletes = rule?.deletes() == true

    // A folder named in this dialog is created only when the rule is saved.
    private var newFolderName: String? = null

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
            filterRuleApplyToContacts.isChecked = rule?.applyToContacts ?: true
            filterRuleNotify.isChecked = rule?.notify == true
            // Spam never notifies
            filterRuleNotify.beGoneIf(isSpam)
            filterRuleAction.setOnClickListener { chooseAction() }
            filterRuleSenderHint.addContactPicker(activity) { filterRuleSender.setText(it) }
        }
        showAction()

        val titleId = when {
            isSpam -> R.string.spam_filter_rule
            rule == null -> R.string.add_filter_rule
            else -> R.string.edit_filter_rule
        }
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { if (rule != null && !isSpam) setNeutralButton(org.fossify.commons.R.string.delete, null) }
            .apply {
                activity.setupDialogStuff(binding.root, this, titleId) { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(dialog) }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { delete(dialog) }
                }
            }
    }

    private fun folderName(id: Long?) = when (id) {
        null -> null
        SPAM_FOLDER_ID -> activity.getString(R.string.spam)
        else -> folders.firstOrNull { it.id == id }?.name
    }

    private fun showAction() {
        val folder = newFolderName ?: folderName(folderId)
        binding.filterRuleAction.setText(
            when {
                deletes -> activity.getString(org.fossify.commons.R.string.delete)
                folder != null -> activity.getString(R.string.move_to, folder)
                else -> ""
            }
        )
    }

    // The Spam rule moves to Spam or deletes; other rules move to one of the folders or a new one.
    private fun chooseAction() {
        val items = if (isSpam) {
            val spam = activity.getString(R.string.spam)
            arrayListOf(
                RadioItem(SPAM_FOLDER_ID.toInt(), activity.getString(R.string.move_to, spam)),
                RadioItem(DELETE, activity.getString(org.fossify.commons.R.string.delete))
            )
        } else {
            folders.mapTo(ArrayList()) { RadioItem(it.id.toInt(), activity.getString(R.string.move_to, it.name)) }
                .apply { add(RadioItem(NEW_FOLDER, activity.getString(R.string.new_folder))) }
        }
        val checked = if (deletes) DELETE else folderId?.toInt() ?: NO_CHOICE
        RadioGroupDialog(activity, items, checked, R.string.filter_rule_action) {
            when (it) {
                NEW_FOLDER -> nameNewFolder()
                DELETE -> deletes = true
                else -> {
                    folderId = (it as Int).toLong()
                    deletes = false
                    newFolderName = null
                }
            }
            showAction()
        }
    }

    private fun nameNewFolder() {
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
                        if (name.isEmpty()) {
                            activity.toast(org.fossify.commons.R.string.empty_name)
                        } else {
                            dialog.dismiss()
                            chooseNewFolder(name)
                        }
                    }
                }
            }
    }

    // A name that is already a folder chooses that folder.
    private fun chooseNewFolder(name: String) {
        val existing = folders.firstOrNull { it.name.equals(name, ignoreCase = true) }
        folderId = existing?.id
        newFolderName = if (existing == null) name else null
        showAction()
    }

    private fun save(dialog: AlertDialog) {
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val sender = binding.filterRuleSender.value.trim()
        val keywords = binding.filterRuleKeywords.value.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val newFolder = newFolderName
        when {
            folderId == null && newFolder == null -> activity.toast(R.string.filter_rule_needs_folder)
            // the Spam rule may be emptied, which turns Spam off
            !isSpam && sender.isEmpty() && keywords.isEmpty() ->
                activity.toast(R.string.filter_rule_needs_sender_or_keyword)

            else -> {
                // a second tap must not save the rule twice
                okButton.isEnabled = false
                saveRule(dialog, sender, keywords, newFolder)
            }
        }
    }

    private fun saveRule(dialog: AlertDialog, sender: String, keywords: List<String>, newFolder: String?) {
        ensureBackgroundThread {
            val chosenFolder = folderId
                ?: newFolder?.let { activity.filterRulesDB.insertFolder(FilterFolder(name = it)) }
                ?: return@ensureBackgroundThread
            val position = rule?.position
                ?: ((activity.filterRulesDB.getRules().filterNot { it.isSpam }.maxOfOrNull { it.position } ?: -1) + 1)
            activity.filterRulesDB.insertRule(
                FilterRule(
                    id = rule?.id ?: 0,
                    position = position,
                    folderId = chosenFolder,
                    sender = sender,
                    keywords = keywords.joinToString("\n"),
                    notify = !isSpam && binding.filterRuleNotify.isChecked,
                    action = if (deletes) SPAM_ACTION_DELETE else SPAM_ACTION_MOVE,
                    isSpam = isSpam,
                    applyToContacts = binding.filterRuleApplyToContacts.isChecked
                )
            )
            activity.runOnUiThread {
                dialog.dismiss()
                callback()
            }
        }
    }

    // Deleting the last rule for a folder also removes the folder, if it is empty.
    private fun delete(dialog: AlertDialog) {
        val rule = rule ?: return
        ensureBackgroundThread {
            val otherRules = activity.filterRulesDB.countRules(rule.folderId) - 1
            val messages = activity.filterRulesDB.countMessages(rule.folderId)
            val folder = folderName(rule.folderId).orEmpty()
            val question = when {
                otherRules > 0 -> activity.getString(R.string.delete_filter_rule)
                messages == 0 -> activity.getString(R.string.delete_filter_rule_and_folder, folder)
                else -> activity.resources.getQuantityString(
                    R.plurals.delete_filter_rule_keep_folder, messages, folder, messages
                )
            }
            activity.runOnUiThread { confirmDelete(dialog, rule, question) }
        }
    }

    private fun confirmDelete(dialog: AlertDialog, rule: FilterRule, question: String) {
        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                activity.filterRulesDB.deleteRule(rule)
                activity.filterRulesDB.deleteUnusedFolders()
                activity.runOnUiThread {
                    dialog.dismiss()
                    callback()
                }
            }
        }
    }

    companion object {
        private const val NO_CHOICE = -1
        private const val NEW_FOLDER = -2
        private const val DELETE = -3
    }
}
