package com.example.contacts

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

data class DeviceContactInfo(
    val id: String,
    val displayName: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val photoUri: String? = null
)

object ContactsHelper {

    /**
     * Resolves contact details from a Contact URI returned by ActivityResultContracts.PickContact()
     */
    fun resolveContact(context: Context, contactUri: Uri): DeviceContactInfo? {
        return try {
            val contentResolver = context.contentResolver
            var contactId: String? = null
            var displayName = "Contact"
            var photoUri: String? = null
            var hasPhone = 0

            contentResolver.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    val phoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    if (idIdx >= 0) contactId = cursor.getString(idIdx)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: "Contact"
                    if (photoIdx >= 0) photoUri = cursor.getString(photoIdx)
                    if (phoneIdx >= 0) hasPhone = cursor.getInt(phoneIdx)
                }
            }

            var phoneNumber: String? = null
            if (contactId != null && hasPhone > 0) {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIdx >= 0) phoneNumber = phoneCursor.getString(numIdx)
                    }
                }
            }

            var email: String? = null
            if (contactId != null) {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                    "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { emailCursor ->
                    if (emailCursor.moveToFirst()) {
                        val emailIdx = emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                        if (emailIdx >= 0) email = emailCursor.getString(emailIdx)
                    }
                }
            }

            DeviceContactInfo(
                id = contactId ?: System.currentTimeMillis().toString(),
                displayName = displayName,
                phoneNumber = phoneNumber,
                email = email,
                photoUri = photoUri
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun dialContact(context: Context, phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phoneNumber.trim()}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot dial: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun messageContact(context: Context, phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${phoneNumber.trim()}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot message: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun emailContact(context: Context, email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${email.trim()}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot email: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
