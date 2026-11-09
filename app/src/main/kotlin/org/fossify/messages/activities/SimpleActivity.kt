package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.messages.R

open class SimpleActivity : BaseSimpleActivity() {
    // Fossify Commons' BaseSimpleActivity.onCreate warns now and then that the app is a "fake version" when the
    // package name doesn't start with "org.fossify.". This fork has its own package name on purpose, so only that
    // check is given the upstream name. The approach comes from Messages+ (https://github.com/arikw/messages).
    private var isInBaseOnCreate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isInBaseOnCreate = true
        try {
            super.onCreate(savedInstanceState)
        } finally {
            isInBaseOnCreate = false
        }
    }

    override fun getPackageName(): String {
        val packageName = super.getPackageName()
        if (!isInBaseOnCreate) {
            return packageName
        }

        val caller = Thread.currentThread().stackTrace.dropWhile { it.methodName != "getPackageName" }
            .firstOrNull { it.methodName != "getPackageName" }
        val isCommonsCheck = caller?.className == BaseSimpleActivity::class.java.name && caller.methodName == "onCreate"
        return if (isCommonsCheck) UPSTREAM_PACKAGE_NAME else packageName
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher_red,
        R.mipmap.ic_launcher_pink,
        R.mipmap.ic_launcher_purple,
        R.mipmap.ic_launcher_deep_purple,
        R.mipmap.ic_launcher_indigo,
        R.mipmap.ic_launcher_blue,
        R.mipmap.ic_launcher_light_blue,
        R.mipmap.ic_launcher_cyan,
        R.mipmap.ic_launcher_teal,
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_light_green,
        R.mipmap.ic_launcher_lime,
        R.mipmap.ic_launcher_yellow,
        R.mipmap.ic_launcher_amber,
        R.mipmap.ic_launcher_orange,
        R.mipmap.ic_launcher_deep_orange,
        R.mipmap.ic_launcher_brown,
        R.mipmap.ic_launcher_blue_grey,
        R.mipmap.ic_launcher_grey_black
    )

    override fun getAppLauncherName() = getString(R.string.app_name)

    override fun getRepositoryName() = "Messages"

    companion object {
        private const val UPSTREAM_PACKAGE_NAME = "org.fossify.messages"
    }
}
