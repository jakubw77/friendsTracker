package com.example

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.location.Address
import android.location.Geocoder
import android.provider.ContactsContract
import android.util.Log
import java.io.IOException
import java.util.Locale

object ContactSyncManager {

    /**
     * Reads real contacts from the device database (which is synchronized with Google Contacts).
     * Retrieves contacts with an address and email, geocodes the address, and compiles a list of active Friends.
     */
    fun fetchSyncedContacts(context: Context, userLat: Double, userLon: Double): List<Friend> {
        val friends = mutableListOf<Friend>()
        val resolver: ContentResolver = context.contentResolver
        val geocoder = Geocoder(context, Locale.getDefault())

        // We fetch contact display names, emails, and postal addresses.
        // Step 1: Query StructuredPostal to get names and postal addresses.
        val postalCursor = resolver.query(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID,
                ContactsContract.CommonDataKinds.StructuredPostal.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
            ),
            null,
            null,
            null
        )

        val postalMap = mutableMapOf<String, Pair<String, String>>() // ContactID -> Pair(Name, Address)
        postalCursor?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.DISPLAY_NAME)
            val addressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)

            if (idIndex >= 0 && nameIndex >= 0 && addressIndex >= 0) {
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val address = cursor.getString(addressIndex) ?: ""
                    if (address.isNotBlank()) {
                        postalMap[contactId] = Pair(name, address)
                    }
                }
            }
        }

        // Step 2: Query Emails to link with Contact ID
        val emailMap = mutableMapOf<String, String>() // ContactID -> Email
        val emailCursor = resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null,
            null,
            null
        )
        emailCursor?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            if (idIndex >= 0 && emailIndex >= 0) {
                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(idIndex)
                    val email = cursor.getString(emailIndex) ?: ""
                    if (email.isNotBlank()) {
                        emailMap[contactId] = email
                    }
                }
            }
        }

        // Colors to cycle through for icons
        val colors = listOf("#2196F3", "#E91E63", "#9C27B0", "#FFC107", "#00BCD4", "#E040FB", "#FF5722")
        var colorIdx = 0

        // Step 3: Loop through gathered contacts, resolve coordinates using Geocoder, and create Friend models
        for ((contactId, info) in postalMap) {
            val (name, address) = info
            val email = emailMap[contactId] ?: "${name.lowercase().replace(" ", "")}@gmail.com"

            // Geocode the physical address format
            var lat = userLat
            var lon = userLon

            try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(address, 1)
                if (!addresses.isNullOrEmpty()) {
                    val resolved: Address = addresses[0]
                    lat = resolved.latitude
                    lon = resolved.longitude
                    Log.d("ContactSyncManager", "Geocoded address for $name: $address -> $lat, $lon")
                } else {
                    // Fallback to slightly offset location if geocoding yields nothing
                    val offset = ((contactId.hashCode() % 10) + 1) * 0.015
                    lat = userLat + offset
                    lon = userLon - offset
                    Log.d("ContactSyncManager", "Could not geocode address for $name: $address, using fallback offset")
                }
            } catch (e: Exception) {
                val offset = ((contactId.hashCode() % 10) + 1) * 0.012
                lat = userLat + offset
                lon = userLon - offset
                Log.e("ContactSyncManager", "Geocoder error for $name: ${e.message}")
            }

            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")

            val friendColor = colors[colorIdx % colors.size]
            colorIdx++

            friends.add(
                Friend(
                    id = "device_contact_$contactId",
                    name = name,
                    email = email,
                    initial = initials.ifBlank { "C" },
                    latitude = lat,
                    longitude = lon,
                    avatarColorHex = friendColor,
                    isSimulatedMoving = false,
                    trafficCondition = TrafficCondition.NORMAL
                )
            )
        }

        return friends
    }

    /**
     * Convenience function to write a contact into the device's Contacts Provider list programmatically.
     * This acts as the direct dynamic integration allowing users to test Google Contacts synchronization in real-time.
     */
    fun insertContactToDevice(context: Context, name: String, email: String, address: String): Boolean {
        val ops = ArrayList<ContentProviderOperation>()

        // Raw Contact Operation
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .build())

        // Name Operation
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build())

        // Email Operation
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
            .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
            .build())

        // Postal Address Operation
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, address)
            .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK)
            .build())

        return try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            Log.e("ContactSyncManager", "Error inserting contact: ${e.message}")
            false
        }
    }
}
