package org.fossify.messages.helpers

import android.app.Activity
import android.view.Menu
import android.view.MenuItem
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.FolderActivity

// The folders of the filter rules in the main menu: their group, and their position after Spam.
private const val FOLDERS_MENU_GROUP = 1
private const val FOLDERS_MENU_ORDER = 40

fun MenuItem.isFolderItem() = groupId == FOLDERS_MENU_GROUP

/**
 * Lists the folders of the filter rules in [menu] as "Open <folder>", after Spam. Folders with no rules and no messages
 * are removed first. Each item carries the intent that opens its folder.
 */
fun Activity.updateFolderMenuItems(menu: Menu) {
    ensureBackgroundThread {
        filterRulesDB.deleteUnusedFolders()
        val folders = filterRulesDB.getFolders()
        runOnUiThread {
            menu.removeGroup(FOLDERS_MENU_GROUP)
            folders.forEach {
                val title = getString(R.string.open_folder, it.name)
                menu.add(FOLDERS_MENU_GROUP, it.id.toInt(), FOLDERS_MENU_ORDER, title).intent =
                    FolderActivity.intent(this, it.id, it.name)
            }
        }
    }
}
