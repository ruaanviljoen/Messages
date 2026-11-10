package org.fossify.messages.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.textfield.TextInputLayout
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R

/** Lets the user choose a contact's phone number with the system contact picker. */
fun ComponentActivity.pickContactNumber(callback: (String) -> Unit) {
    var launcher: ActivityResultLauncher<Intent>? = null
    val contract = ActivityResultContracts.StartActivityForResult()
    launcher = activityResultRegistry.register("pick_contact_number", contract) {
        launcher?.unregister()
        val uri = it.data?.data ?: return@register
        ensureBackgroundThread {
            val number = contentResolver.query(uri, arrayOf(Phone.NUMBER), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (!number.isNullOrBlank()) {
                runOnUiThread { callback(number) }
            }
        }
    }

    try {
        launcher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
    } catch (_: ActivityNotFoundException) {
        launcher.unregister()
        toast(org.fossify.commons.R.string.no_app_found)
    }
}

/** Adds a contact icon to the end of this field; choosing a contact passes its number to [onPicked]. */
fun TextInputLayout.addContactPicker(activity: ComponentActivity, onPicked: (String) -> Unit) {
    endIconMode = TextInputLayout.END_ICON_CUSTOM
    setEndIconDrawable(org.fossify.commons.R.drawable.ic_person_vector)
    endIconContentDescription = activity.getString(R.string.choose_contact)
    setEndIconTintList(ColorStateList.valueOf(activity.getProperTextColor()))
    setEndIconOnClickListener { activity.pickContactNumber(onPicked) }
}
