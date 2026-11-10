package org.fossify.messages.helpers

import android.content.res.ColorStateList
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.messages.R
import org.fossify.messages.models.Conversation

private const val CHECKED_ALPHA = 0.3f
private const val STROKE_ALPHA = 0.5f

/**
 * The "Unread" and "Read" chips under the search bar. At most one is checked; with none, every conversation shows.
 * [onChanged] runs when the choice changes.
 */
class ReadFilterChips(private val group: ChipGroup, onChanged: () -> Unit) {
    init {
        group.setOnCheckedStateChangeListener { _, _ -> onChanged() }
        updateColors()
    }

    fun apply(conversations: List<Conversation>) = when (group.checkedChipId) {
        R.id.read_filter_unread -> conversations.filter { !it.read }
        R.id.read_filter_read -> conversations.filter { it.read }
        else -> conversations
    }

    // Fossify's colours are chosen by the user, so the chips take them instead of the theme's.
    fun updateColors() {
        val context = group.context
        val textColor = context.getProperTextColor()
        val checked = intArrayOf(android.R.attr.state_checked)
        val background = ColorStateList(
            arrayOf(checked, intArrayOf()),
            intArrayOf(context.getProperPrimaryColor().adjustAlpha(CHECKED_ALPHA), android.graphics.Color.TRANSPARENT)
        )
        for (index in 0 until group.childCount) {
            (group.getChildAt(index) as Chip).apply {
                setTextColor(textColor)
                chipBackgroundColor = background
                chipStrokeColor = ColorStateList.valueOf(textColor.adjustAlpha(STROKE_ALPHA))
                checkedIconTint = ColorStateList.valueOf(textColor)
            }
        }
    }
}
