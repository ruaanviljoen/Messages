package org.fossify.messages.activities

import android.content.Intent
import org.fossify.commons.extensions.beGone
import org.fossify.messages.databinding.ActivitySettingsBinding
import org.fossify.messages.dialogs.FilterRunDialog

// The "Filters" section of the settings: the rules, Spam among them, and running the filters.

// Only the latest run of the filters is offered; older ones were worked out with outdated settings.
private var latestFilterRun = 0

private fun SimpleActivity.offerFilterRun(showIfNoChanges: Boolean = false) {
    val runNumber = ++latestFilterRun
    FilterRunDialog(this, showIfNoChanges) { runNumber == latestFilterRun }
}

fun SimpleActivity.setupFilterSettings(binding: ActivitySettingsBinding) = binding.apply {
    settingsFilterRulesHolder.setOnClickListener {
        startActivity(Intent(this@setupFilterSettings, FilterRulesActivity::class.java))
    }

    settingsRunFiltersHolder.setOnClickListener { offerFilterRun(showIfNoChanges = true) }

    // this fork has no recycle bin, see Config.useRecycleBin
    settingsRecycleBinLabel.beGone()
    settingsUseRecycleBinHolder.beGone()
    settingsEmptyRecycleBinHolder.beGone()
    settingsRecycleBinDivider.beGone()
}
