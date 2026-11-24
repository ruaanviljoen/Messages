package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.extensions.beGone
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
import org.fossify.messages.helpers.getSpamRule
import org.fossify.messages.helpers.markFiltersChanged
import org.fossify.messages.models.FilterRule

/**
 * The filter rules in the order they run: Spam pinned first, then the user's own. It reuses the layout of the keyword
 * list.
 */
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

        // the list always holds the Spam rule, so the reused layout's empty-list hint is replaced by one in the list
        binding.manageBlockedKeywordsPlaceholder.beGone()
        binding.manageBlockedKeywordsPlaceholder2.beGone()
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
            // getSpamRule creates the Spam rule if it does not exist yet; getRules lists it first
            getSpamRule()
            val rules = ArrayList(filterRulesDB.getRules())
            val folderNames = getFolderNames()
            runOnUiThread {
                (binding.manageBlockedKeywordsList.adapter as? FilterRulesAdapter)?.detach()
                binding.manageBlockedKeywordsList.adapter = FilterRulesAdapter(
                    activity = this,
                    rules = rules,
                    folderNames = folderNames,
                    recyclerView = binding.manageBlockedKeywordsList,
                    onReordered = { saveOrder(it) },
                    onAddRule = { editRule() }
                ) { editRule(it as FilterRule) }
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
            markFiltersChanged()
            runOnUiThread { offerFilterRun() }
        }
    }

    private fun offerFilterRun(showIfNoChanges: Boolean = false) {
        val runNumber = ++latestFilterRun
        FilterRunDialog(this, showIfNoChanges) { runNumber == latestFilterRun }
    }
}
