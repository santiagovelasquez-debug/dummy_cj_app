package com.appbase

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
class StateProvider : ContentProvider() {
    companion object {
        var pin_drive_status: Boolean = false //  Initial state
    }

    override fun onCreate(): Boolean = false

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d("CJ App", "Query receives from StateProvider, path=${uri.path}")
        // path validation
        return if (uri.path == "/state") {
            val cursor = MatrixCursor(arrayOf("allow"))
            cursor.addRow(arrayOf(if (pin_drive_status) 1 else 0))
            Log.d("CJ App", "Returns pin_drive status=${pin_drive_status}")
            cursor
        } else {
            null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
