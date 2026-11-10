package org.fossify.messages.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.interfaces.ItemMoveCallback
import org.fossify.commons.interfaces.ItemTouchHelperContract
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.databinding.ItemFilterRuleBinding
import org.fossify.messages.models.FilterRule
import java.util.Collections

/** The filter rules in the order they run. Tapping a rule edits it; its handle drags it to another place. */
// Most functions are overrides that MyRecyclerViewAdapter and ItemTouchHelperContract require.
@Suppress("TooManyFunctions")
class FilterRulesAdapter(
    activity: SimpleActivity,
    private val rules: ArrayList<FilterRule>,
    private val folderNames: Map<Long, String>,
    recyclerView: MyRecyclerView,
    private val onReordered: (List<FilterRule>) -> Unit,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract {
    private val touchHelper = ItemTouchHelper(ItemMoveCallback(this)).apply { attachToRecyclerView(recyclerView) }

    override fun getActionMenuId() = 0

    override fun prepareActionMode(menu: Menu) = Unit

    override fun actionItemPressed(id: Int) = Unit

    override fun getSelectableItemCount() = 0

    override fun getIsItemSelectable(position: Int) = false

    override fun getItemSelectionKey(position: Int) = rules.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = rules.indexOfFirst { it.id.toInt() == key }

    override fun onActionModeCreated() = Unit

    override fun onActionModeDestroyed() = Unit

    override fun getItemCount() = rules.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemFilterRuleBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = rules[position]
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
            filterRuleDragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                }
                false
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        Collections.swap(rules, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) = Unit

    override fun onRowClear(myViewHolder: ViewHolder?) = onReordered(rules)
}
