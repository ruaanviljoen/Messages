package org.fossify.messages.helpers

import android.content.res.Resources
import android.view.ActionMode
import android.widget.TextView
import androidx.annotation.PluralsRes

/**
 * Shows in the selection bar how many items are selected and what they are, such as "2 messages selected", where
 * Fossify shows only "2 / 10". Call it from prepareActionMode, which runs after every change to the selection.
 */
fun ActionMode?.showSelectedCount(resources: Resources, @PluralsRes plural: Int, count: Int) {
    (this?.customView as? TextView)?.text = resources.getQuantityString(plural, count, count)
}
