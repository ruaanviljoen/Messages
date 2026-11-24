package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beInvisibleIf
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemFilterRuleBinding
import org.fossify.messages.databinding.ItemFilterRulesHintBinding
import org.fossify.messages.models.FilterRule
import java.util.Collections

/**
 * The filter rules in the order they run. Tapping a rule edits it; its handle drags it to another place. The Spam rule
 * is pinned first: it has no handle, and no rule can be dragged above it. While Spam is the only rule, a hint under it
 * explains rules and offers [onAddRule].
 */
// Most functions are overrides that MyRecyclerViewAdapter and ItemTouchHelperContract require.
@Suppress("TooManyFunctions")
class FilterRulesAdapter(
    activity: SimpleActivity,
    private val rules: ArrayList<FilterRule>,
    private val folderNames: Map<Long, String>,
    recyclerView: MyRecyclerView,
    private val onReordered: (List<FilterRule>) -> Unit,
    private val onAddRule: () -> Unit,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract {
    private val touchHelper = ItemTouchHelper(RuleMoveCallback()).apply { attachToRecyclerView(recyclerView) }
    private var moved = false
    private val showHint = rules.all { it.isSpam }

    /** Stops handling drags, before this adapter is replaced. */
    fun detach() = touchHelper.attachToRecyclerView(null)

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) = Unit

    override fun actionItemPressed(id: Int) = Unit

    override fun getSelectableItemCount() = 0

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = rules.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = rules.indexOfFirst { it.id.toInt() == key }

    override fun onActionModeCreated() = Unit

    override fun onActionModeDestroyed() = Unit

    override fun getItemCount() = rules.size + if (showHint) 1 else 0

    override fun getItemViewType(position: Int) = if (position < rules.size) RULE else HINT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = if (viewType == HINT) {
            ItemFilterRulesHintBinding.inflate(layoutInflater, parent, false).apply {
                filterRulesHint.setTextColor(textColor)
                filterRulesAdd.setTextColor(properPrimaryColor)
                filterRulesAdd.underlineText()
                filterRulesAdd.setOnClickListener { onAddRule() }
            }.root
        } else {
            ItemFilterRuleBinding.inflate(layoutInflater, parent, false).root
        }
        return createViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = rules.getOrNull(position) ?: return
        holder.bindView(rule, allowSingleClick = true, allowLongClick = false) { itemView, _ ->
            setupView(itemView, rule, holder)
        }
        bindViewHolder(holder)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(view: View, rule: FilterRule, holder: ViewHolder) {
        ItemFilterRuleBinding.bind(view).apply {
            root.setupViewBackground(activity)
            filterRuleFolder.text = folderNames[rule.folderId].orEmpty()
            filterRuleFolder.setTextColor(textColor)
            filterRuleDescription.text = rule.describe()
            filterRuleDescription.setTextColor(textColor)
            filterRuleDragHandle.applyColorFilter(textColor)
            filterRuleDragHandle.beInvisibleIf(rule.isSpam)
            filterRuleDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                }
                false
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (rules[fromPosition].isSpam || rules[toPosition].isSpam) {
            return
        }
        Collections.swap(rules, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        moved = true
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) = Unit

    // Saves the new order once a drag ends, if anything moved.
    override fun onRowClear(myViewHolder: ViewHolder?) {
        if (moved) {
            moved = false
            rules.indices.forEach { rules[it] = rules[it].copy(position = it) }
            onReordered(rules.filterNot { it.isSpam })
        }
    }

    // A rule can only be dropped onto another of the user's rules, not onto Spam or the hint.
    private inner class RuleMoveCallback : ItemMoveCallback(this@FilterRulesAdapter) {
        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ) = rules.getOrNull(target.bindingAdapterPosition)?.isSpam == false
    }

    private companion object {
        const val RULE = 0
        const val HINT = 1
    }
}
