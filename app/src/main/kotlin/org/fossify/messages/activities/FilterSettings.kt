package org.fossify.messages.activities

import android.content.Intent
import android.text.InputType
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.value
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivitySettingsBinding
import org.fossify.messages.databinding.DialogAddBlockedKeywordBinding
import org.fossify.messages.dialogs.FilterRunDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.SPAM_ACTION_DELETE
import org.fossify.messages.helpers.SPAM_ACTION_MOVE

// The "Filters" section of the settings: Spam's settings, the filter rules and running the filters.

fun SimpleActivity.setupFilterSettings(binding: ActivitySettingsBinding) = binding.apply {
    settingsManageBlockedKeywordsHolder.setOnClickListener {
        startActivity(Intent(this@setupFilterSettings, ManageBlockedKeywordsActivity::class.java))
    }

    settingsSpamAction.text = getSpamActionText()
    settingsSpamActionHolder.setOnClickListener {
        val items = arrayListOf(
            RadioItem(SPAM_ACTION_MOVE, getString(R.string.spam_action_move)),
            RadioItem(SPAM_ACTION_DELETE, getString(R.string.spam_action_delete))
        )
        RadioGroupDialog(this@setupFilterSettings, items, config.spamAction, R.string.spam_action) {
            config.spamAction = it as Int
            settingsSpamAction.text = getSpamActionText()
            FilterRunDialog(this@setupFilterSettings)
        }
    }

    settingsFilterSavedContacts.isChecked = config.filterSavedContacts
    settingsFilterSavedContactsHolder.setOnClickListener {
        settingsFilterSavedContacts.toggle()
        config.filterSavedContacts = settingsFilterSavedContacts.isChecked
        FilterRunDialog(this@setupFilterSettings)
    }

    settingsSpamExemptNumbersHolder.setOnClickListener { editSpamExemptNumbers() }

    settingsFilterRulesHolder.setOnClickListener {
        startActivity(Intent(this@setupFilterSettings, FilterRulesActivity::class.java))
    }

    settingsRunFiltersHolder.setOnClickListener { FilterRunDialog(this@setupFilterSettings, showIfNoChanges = true) }
}

private fun SimpleActivity.getSpamActionText() = getString(
    if (config.spamAction == SPAM_ACTION_DELETE) R.string.spam_action_delete else R.string.spam_action_move
)

// One number or sender name per line.
private fun SimpleActivity.editSpamExemptNumbers() {
    val dialogBinding = DialogAddBlockedKeywordBinding.inflate(layoutInflater).apply {
        addBlockedKeywordHint.hint = getString(R.string.spam_exempt_numbers_hint)
        addBlockedKeywordEdittext.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        addBlockedKeywordEdittext.setText(config.spamExemptNumbers.sorted().joinToString("\n"))
    }
    getAlertDialogBuilder()
        .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
            config.spamExemptNumbers = dialogBinding.addBlockedKeywordEdittext.value
                .split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            FilterRunDialog(this)
        }
        .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        .apply { setupDialogStuff(dialogBinding.root, this, R.string.spam_exempt_numbers) }
}
