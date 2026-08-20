package com.nova.assistant.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(val name: String, val number: String)

class CallController(private val context: Context) {

    private fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    /** Looks up a contact by fuzzy name match. Returns null if not found or permission missing. */
    fun findContact(name: String): ContactMatch? {
        if (!hasPermission(android.Manifest.permission.READ_CONTACTS)) return null
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        ) ?: return null

        cursor.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                return ContactMatch(it.getString(nameIdx), it.getString(numberIdx))
            }
        }
        return null
    }

    /** Actually places the call. Only call this AFTER the user has confirmed. */
    fun callNumber(number: String): Boolean {
        if (!hasPermission(android.Manifest.permission.CALL_PHONE)) return false
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    /** Opens the dialer pre-filled, for cases where CALL_PHONE isn't granted — still user-initiated. */
    fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

class MessageController(private val context: Context) {

    /** Opens the default SMS app with recipient + body prefilled — sending itself is the
     * user's final tap unless SEND_SMS is granted and the caller explicitly confirmed. */
    fun composeMessage(number: String, body: String) {
        val uri = Uri.parse("smsto:$number")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun sendMessageDirect(number: String, body: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
        smsManager.sendTextMessage(number, null, body, null, null)
        return true
    }
}
