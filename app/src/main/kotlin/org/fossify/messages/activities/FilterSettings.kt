package org.fossify.messages.activities

import android.content.Intent
import android.text.InputType
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivitySettingsBinding
import org.fossify.messages.databinding.DialogAddBlockedKeywordBinding
import org.fossify.messages.dialogs.FilterRuleDialog
import org.fossify.messages.dialogs.FilterRunDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.addContactPicker
import org.fossify.messages.helpers.getSpamRule

// The "Filters" section of the settings: the Spam rule, its exempt numbers, the other rules and running the filters.

// Only the latest run of the filters is offered; older ones were worked out with outdated settings.
private var latestFilterRun = 0

private fun SimpleActivity.offerFilterRun(showIfNoChanges: Boolean = false) {
    val runNumber = ++latestFilterRun
    FilterRunDialog(this, showIfNoChanges) { runNumber == latestFilterRun }
}

fun SimpleActivity.setupFilterSettings(binding: ActivitySettingsBinding) = binding.apply {
    settingsSpamRuleHolder.setOnClickListener {
        ensureBackgroundThread {
            val spamRule = getSpamRule()
            runOnUiThread { FilterRuleDialog(this@setupFilterSettings, spamRule) { offerFilterRun() } }
        }
    }

    settingsSpamExemptNumbersHolder.setOnClickListener { editSpamExemptNumbers() }

    settingsFilterRulesHolder.setOnClickListener {
        startActivity(Intent(this@setupFilterSettings, FilterRulesActivity::class.java))
    }

    settingsRunFiltersHolder.setOnClickListener { offerFilterRun(showIfNoChanges = true) }
}

// One number or sender name per line. They apply to the Spam rule only.
private fun SimpleActivity.editSpamExemptNumbers() {
    val dialogBinding = DialogAddBlockedKeywordBinding.inflate(layoutInflater).apply {
        addBlockedKeywordHint.hint = getString(R.string.spam_exempt_numbers_hint)
        addBlockedKeywordEdittext.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        addBlockedKeywordEdittext.setText(config.spamExemptNumbers.sorted().joinToString("\n"))
        // a chosen contact's number is added as a new line
        addBlockedKeywordHint.addContactPicker(this@editSpamExemptNumbers) { number ->
            val text = addBlockedKeywordEdittext.value.trimEnd()
            addBlockedKeywordEdittext.setText(if (text.isEmpty()) number else "$text\n$number")
        }
    }
    getAlertDialogBuilder()
        .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
            config.spamExemptNumbers = dialogBinding.addBlockedKeywordEdittext.value
                .split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            offerFilterRun()
        }
        .setNegativeButton(org.fossify.commons.R.string.cancel, null)
        .apply { setupDialogStuff(dialogBinding.root, this, R.string.spam_exempt_numbers) }
}
