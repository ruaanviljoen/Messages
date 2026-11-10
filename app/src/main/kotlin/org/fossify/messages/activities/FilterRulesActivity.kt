package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.adapters.FilterRulesAdapter
import org.fossify.messages.databinding.ActivityManageBlockedKeywordsBinding
import org.fossify.messages.dialogs.FilterRuleDialog
import org.fossify.messages.dialogs.FilterRunDialog
import org.fossify.messages.helpers.filterRulesDB
import org.fossify.messages.helpers.getFolderNames
import org.fossify.messages.models.FilterRule

/** The filter rules, which run after Spam in the order shown. It reuses the layout of the keyword list. */
class FilterRulesActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityManageBlockedKeywordsBinding::inflate)

    // Only the latest run of the filters is offered; older ones were worked out with outdated rules.
    private var latestFilterRun = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar()
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.manageBlockedKeywordsList))
        setupMaterialScrollListener(
            scrollingView = binding.manageBlockedKeywordsList,
            topAppBar = binding.blockKeywordsAppbar
        )
        updateTextColors(binding.manageBlockedKeywordsWrapper)

        binding.manageBlockedKeywordsPlaceholder.text = getString(R.string.no_filter_rules)
        binding.manageBlockedKeywordsPlaceholder2.apply {
            text = getString(R.string.add_filter_rule)
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener { editRule() }
        }
        loadRules()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.blockKeywordsAppbar, NavigationIcon.Arrow)
    }

    private fun setupToolbar() {
        binding.blockKeywordsToolbar.apply {
            title = getString(R.string.filter_rules)
            menu.clear()
            inflateMenu(R.menu.menu_filter_rules)
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.add_filter_rule -> editRule()
                    R.id.run_filters -> offerFilterRun(showIfNoChanges = true)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
    }

    private fun loadRules() {
        ensureBackgroundThread {
            // the Spam rule is pinned and edited from the settings
            val rules = ArrayList(filterRulesDB.getRules().filterNot { it.isSpam })
            val folderNames = getFolderNames()
            runOnUiThread {
                (binding.manageBlockedKeywordsList.adapter as? FilterRulesAdapter)?.detach()
                binding.manageBlockedKeywordsList.adapter = FilterRulesAdapter(
                    activity = this,
                    rules = rules,
                    folderNames = folderNames,
                    recyclerView = binding.manageBlockedKeywordsList,
                    onReordered = { saveOrder(it) }
                ) { editRule(it as FilterRule) }
                binding.manageBlockedKeywordsPlaceholder.beVisibleIf(rules.isEmpty())
                binding.manageBlockedKeywordsPlaceholder2.beVisibleIf(rules.isEmpty())
            }
        }
    }

    private fun editRule(rule: FilterRule? = null) {
        FilterRuleDialog(this, rule) {
            loadRules()
            offerFilterRun()
        }
    }

    // The order decides which rule wins, so a new order may move messages.
    private fun saveOrder(rules: List<FilterRule>) {
        ensureBackgroundThread {
            filterRulesDB.updateRules(rules)
            runOnUiThread { offerFilterRun() }
        }
    }

    private fun offerFilterRun(showIfNoChanges: Boolean = false) {
        val runNumber = ++latestFilterRun
        FilterRunDialog(this, showIfNoChanges) { runNumber == latestFilterRun }
    }
}
