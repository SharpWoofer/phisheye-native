package com.caihongqi.phisheye.dialogs

import android.app.Activity
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.R

fun ConfirmationDialog(
    activity: Activity,
    message: String = "",
    messageId: Int = R.string.proceed_with_deletion,
    positive: Int = R.string.yes,
    negative: Int = R.string.no,
    callback: () -> Unit
) {
    val builder = MaterialAlertDialogBuilder(activity)
    
    val msg = if (message.isEmpty()) activity.getString(messageId) else message
    builder.setMessage(msg)
    
    builder.setPositiveButton(positive) { _, _ -> callback() }
    
    if (negative != 0) {
        builder.setNegativeButton(negative, null)
    }

    builder.show()
}

fun PermissionRequiredDialog(
    activity: Activity,
    textId: Int,
    positiveActionCallback: () -> Unit
) {
    MaterialAlertDialogBuilder(activity)
        .setMessage(textId)
        .setPositiveButton(R.string.ok) { _, _ -> positiveActionCallback() }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

fun ExportSettingsDialog(
    activity: Activity,
    defaultFilename: String,
    hidePath: Boolean,
    callback: (path: String, filename: String) -> Unit
) {
    val builder = MaterialAlertDialogBuilder(activity)
    builder.setTitle(R.string.export_settings)
    
    val input = EditText(activity)
    input.setText(defaultFilename)
    val container = FrameLayout(activity)
    val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    params.leftMargin = activity.resources.getDimensionPixelSize(R.dimen.activity_margin)
    params.rightMargin = activity.resources.getDimensionPixelSize(R.dimen.activity_margin)
    input.layoutParams = params
    container.addView(input)
    
    builder.setView(container)
    builder.setPositiveButton(R.string.ok) { _, _ ->
        callback("", input.text.toString())
    }
    builder.setNegativeButton(R.string.cancel, null)
    builder.show()
}

fun FilePickerDialog(
    activity: Activity,
    currPath: String,
    pickFile: Boolean,
    showFAB: Boolean,
    callback: (path: String) -> Unit
) {
    val builder = MaterialAlertDialogBuilder(activity)
    builder.setTitle(if (pickFile) R.string.select_file else R.string.select_folder)
    
    val input = EditText(activity)
    input.setText(currPath)
    val container = FrameLayout(activity)
    val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    params.leftMargin = activity.resources.getDimensionPixelSize(R.dimen.activity_margin)
    params.rightMargin = activity.resources.getDimensionPixelSize(R.dimen.activity_margin)
    input.layoutParams = params
    container.addView(input)
    
    builder.setView(container)
    builder.setPositiveButton(R.string.ok) { _, _ ->
        callback(input.text.toString())
    }
    builder.setNegativeButton(R.string.cancel, null)
    builder.show()
}

// Stub for RadioGroupDialog if needed
fun RadioGroupDialog(
    activity: Activity,
    items: ArrayList<org.fossify.commons.models.RadioItem>,
    titleId: Int = 0,
    callback: (Any) -> Unit
) {
    val builder = MaterialAlertDialogBuilder(activity)
    if (titleId != 0) {
        builder.setTitle(titleId)
    }
    
    val itemTitles = items.map { it.title }.toTypedArray()
    builder.setItems(itemTitles) { _, which ->
        callback(items[which].id)
    }
    builder.show()
}

// Stub for FeatureLockedDialog
fun FeatureLockedDialog(activity: Activity, callback: () -> Unit) {
    MaterialAlertDialogBuilder(activity)
        .setMessage(R.string.features_locked)
        .setPositiveButton(R.string.ok) { _, _ -> callback() }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

// Stub for WritePermissionDialog
fun WritePermissionDialog(activity: Activity, mode: Any, callback: () -> Unit) {
    // Just trigger the callback or show generic confirmation
    MaterialAlertDialogBuilder(activity)
        .setMessage(R.string.confirm_storage_access_text)
        .setPositiveButton(R.string.ok) { _, _ -> callback() }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

// Stub for FileConflictDialog
fun FileConflictDialog(
    activity: Activity,
    fileDirItem: Any,
    showApplyToAllCheckbox: Boolean,
    callback: (resolution: Int, applyForAll: Boolean) -> Unit
) {
    // Always skip or overwrite?
    // Let's just ask overwrite
    MaterialAlertDialogBuilder(activity)
        .setMessage("Overwrite file?")
        .setPositiveButton(R.string.yes) { _, _ -> callback(1, false) } // 1 = Overwrite? Need to check values
        .setNegativeButton(R.string.no) { _, _ -> callback(0, false) } // 0 = Skip
        .show()
}
