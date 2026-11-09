package org.fossify.messages.activities

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getTempFile
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ExportResult
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityManageBlockedKeywordsBinding
import org.fossify.messages.databinding.DialogAddBlockedKeywordBinding
import org.fossify.messages.dialogs.AddBlockedKeywordDialog
import org.fossify.messages.dialogs.ExportBlockedKeywordsDialog
import org.fossify.messages.dialogs.ManageBlockedKeywordsAdapter
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.toArrayList
import org.fossify.messages.helpers.BlockedKeywordsExporter
import org.fossify.messages.helpers.BlockedKeywordsImporter
import org.fossify.messages.helpers.SPAM_ACTION_DELETE
import org.fossify.messages.helpers.SPAM_ACTION_MOVE
import org.fossify.messages.helpers.SpamFilterRun
import org.fossify.messages.helpers.applySpamFilterRun
import org.fossify.messages.helpers.planSpamFilterRun
import java.io.FileOutputStream
import java.io.OutputStream

class ManageBlockedKeywordsActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageBlockedKeywordsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateBlockedKeywords()
        setupOptionsMenu()
        binding.blockKeywordsToolbar.title = getString(R.string.spam_filter)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.manageBlockedKeywordsList))
        setupMaterialScrollListener(
            scrollingView = binding.manageBlockedKeywordsList,
            topAppBar = binding.blockKeywordsAppbar
        )
        updateTextColors(binding.manageBlockedKeywordsWrapper)

        binding.manageBlockedKeywordsPlaceholder2.apply {
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener {
                addOrEditBlockedKeyword()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.blockKeywordsAppbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        binding.blockKeywordsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_blocked_keyword -> {
                    addOrEditBlockedKeyword()
                    true
                }

                R.id.run_spam_filter -> {
                    runSpamFilter(showIfNoChanges = true)
                    true
                }

                R.id.spam_action -> {
                    changeSpamAction()
                    true
                }

                R.id.filter_saved_contacts -> {
                    config.filterSavedContacts = !config.filterSavedContacts
                    menuItem.isChecked = config.filterSavedContacts
                    runSpamFilter()
                    true
                }

                R.id.spam_exempt_numbers -> {
                    editSpamExemptNumbers()
                    true
                }

                R.id.export_blocked_keywords -> {
                    tryExportBlockedNumbers()
                    true
                }

                R.id.import_blocked_keywords -> {
                    tryImportBlockedKeywords()
                    true
                }

                else -> false
            }
        }
        binding.blockKeywordsToolbar.menu.findItem(R.id.filter_saved_contacts).isChecked = config.filterSavedContacts
    }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            try {
                val outputStream = uri?.let { contentResolver.openOutputStream(it) }
                if (outputStream != null) {
                    exportBlockedKeywordsTo(outputStream)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            try {
                if (uri != null) {
                    tryImportBlockedKeywordsFromFile(uri)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

    private fun tryImportBlockedKeywords() {
        val mimeType = "text/plain"
        try {
            getContent.launch(mimeType)
        } catch (_: ActivityNotFoundException) {
            toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun tryImportBlockedKeywordsFromFile(uri: Uri) {
        when (uri.scheme) {
            "file" -> importBlockedKeywords(uri.path!!)
            "content" -> {
                val tempFile = getTempFile("blocked", "blocked_keywords.txt")
                if (tempFile == null) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)
                    importBlockedKeywords(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> toast(org.fossify.commons.R.string.invalid_file_format)
        }
    }

    private fun importBlockedKeywords(path: String) {
        ensureBackgroundThread {
            val result = BlockedKeywordsImporter(this).importBlockedKeywords(path)
            toast(
                when (result) {
                    BlockedKeywordsImporter.ImportResult.IMPORT_OK -> org.fossify.commons.R.string.importing_successful
                    BlockedKeywordsImporter.ImportResult.IMPORT_FAIL -> org.fossify.commons.R.string.no_items_found
                }
            )
            updateBlockedKeywords()
            runSpamFilter()
        }
    }

    private fun exportBlockedKeywordsTo(outputStream: OutputStream?) {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords.toArrayList()
            if (blockedKeywords.isEmpty()) {
                toast(org.fossify.commons.R.string.no_entries_for_exporting)
            } else {
                BlockedKeywordsExporter.exportBlockedKeywords(blockedKeywords, outputStream) {
                    toast(
                        when (it) {
                            ExportResult.EXPORT_OK -> org.fossify.commons.R.string.exporting_successful
                            else -> org.fossify.commons.R.string.exporting_failed
                        }
                    )
                }
            }
        }
    }

    private fun tryExportBlockedNumbers() {
        ExportBlockedKeywordsDialog(
            activity = this,
            path = config.lastBlockedKeywordExportPath,
            hidePath = true
        ) { file ->
            try {
                createDocument.launch(file.name)
            } catch (_: ActivityNotFoundException) {
                toast(
                    org.fossify.commons.R.string.system_service_disabled,
                    Toast.LENGTH_LONG
                )
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    override fun refreshItems() {
        updateBlockedKeywords()
        runSpamFilter()
    }

    private fun updateBlockedKeywords() {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords.sorted().toArrayList()
            runOnUiThread {
                ManageBlockedKeywordsAdapter(
                    activity = this,
                    blockedKeywords = blockedKeywords,
                    listener = this,
                    recyclerView = binding.manageBlockedKeywordsList
                ) {
                    addOrEditBlockedKeyword(it as String)
                }.apply {
                    binding.manageBlockedKeywordsList.adapter = this
                }

                binding.manageBlockedKeywordsPlaceholder.beVisibleIf(blockedKeywords.isEmpty())
                binding.manageBlockedKeywordsPlaceholder2.beVisibleIf(blockedKeywords.isEmpty())
            }
        }
    }

    private fun addOrEditBlockedKeyword(keyword: String? = null) {
        AddBlockedKeywordDialog(this, keyword) {
            updateBlockedKeywords()
            runSpamFilter()
        }
    }

    private fun changeSpamAction() {
        val items = arrayListOf(
            RadioItem(SPAM_ACTION_MOVE, getString(R.string.spam_action_move)),
            RadioItem(SPAM_ACTION_DELETE, getString(R.string.spam_action_delete))
        )
        RadioGroupDialog(this, items, config.spamAction, R.string.spam_action) {
            config.spamAction = it as Int
            runSpamFilter()
        }
    }

    private fun editSpamExemptNumbers() {
        val dialogBinding = DialogAddBlockedKeywordBinding.inflate(layoutInflater).apply {
            addBlockedKeywordHint.hint = getString(R.string.spam_exempt_numbers_hint)
            addBlockedKeywordEdittext.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            addBlockedKeywordEdittext.setText(config.spamExemptNumbers.sorted().joinToString("\n"))
        }
        getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                config.spamExemptNumbers = dialogBinding.addBlockedKeywordEdittext.value
                    .split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                runSpamFilter()
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { setupDialogStuff(dialogBinding.root, this, R.string.spam_exempt_numbers) }
    }

    // Works out what the spam filter would change in the messages already on the phone and asks before changing it.
    private fun runSpamFilter(showIfNoChanges: Boolean = false) {
        if (showIfNoChanges) {
            toast(R.string.checking_messages)
        }

        ensureBackgroundThread {
            val run = planSpamFilterRun()
            runOnUiThread {
                if (run.hasChanges) {
                    ConfirmationDialog(
                        activity = this,
                        message = describe(run),
                        positive = R.string.apply,
                        negative = org.fossify.commons.R.string.cancel
                    ) {
                        ensureBackgroundThread { applySpamFilterRun(run) }
                    }
                } else if (showIfNoChanges) {
                    toast(R.string.spam_filter_no_changes)
                }
            }
        }
    }

    private fun describe(run: SpamFilterRun): String {
        val keywordLines = run.keywordCounts.entries.sortedByDescending { it.value }
            .joinToString("\n") { getString(R.string.spam_keyword_count, it.key, it.value) }
        return getString(R.string.spam_filter_run_summary, run.toSpam.size, run.toInbox.size, run.toDelete.size) +
            if (keywordLines.isEmpty()) "" else "\n\n" + getString(R.string.spam_keyword_counts) + "\n" + keywordLines
    }
}
