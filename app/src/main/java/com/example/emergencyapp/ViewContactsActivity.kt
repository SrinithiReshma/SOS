package com.example.emergencyapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.*

class ViewContactsActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var contactListView: ListView
    private lateinit var contactList: ArrayList<User>
    private lateinit var undoButton: Button

    private var lastDeletedUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_contacts)

        contactListView = findViewById(R.id.listViewContacts)
        undoButton = findViewById(R.id.buttonUndoDelete)

        contactList = ArrayList()
        database = FirebaseDatabase.getInstance("https://sos-emergency-app-d0ff3-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .reference.child("Users")

        // Create Notification Channel (for Android O and above)
        createNotificationChannel()

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contactList.clear()
                for (data in snapshot.children) {
                    val user = data.getValue(User::class.java)
                    user?.id = data.key
                    user?.let { contactList.add(it) }
                }

                val adapter = object : ArrayAdapter<User>(
                    this@ViewContactsActivity,
                    R.layout.list_item_contact,
                    R.id.textContact,
                    contactList
                ) {
                    override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                        val view = super.getView(position, convertView, parent)
                        val textView = view.findViewById<TextView>(R.id.textContact)
                        val user = contactList[position]
                        textView.text = "${user.name} - ${user.phone}"
                        return view
                    }
                }

                contactListView.adapter = adapter

                // 🗑️ Delete on long click
                contactListView.setOnItemLongClickListener { _, _, position, _ ->
                    val user = contactList[position]
                    AlertDialog.Builder(this@ViewContactsActivity)
                        .setTitle("Delete Contact")
                        .setMessage("Do you want to delete ${user.name}?")
                        .setPositiveButton("Yes") { _, _ ->
                            lastDeletedUser = user // Store the last deleted user
                            database.child(user.id!!).removeValue()
                            Toast.makeText(this@ViewContactsActivity, "Deleted", Toast.LENGTH_SHORT).show()

                            // Show Undo button
                            undoButton.visibility = android.view.View.VISIBLE

                            // Show a notification about deletion
                            showNotification("Contact Deleted", "${user.name} has been deleted.")
                        }
                        .setNegativeButton("No", null)
                        .show()
                    true
                }

                // ✏️ Update on single click
                contactListView.setOnItemClickListener { _, _, position, _ ->
                    val user = contactList[position]
                    val inflater = LayoutInflater.from(this@ViewContactsActivity)
                    val view = inflater.inflate(R.layout.dialog_update_contact, null)

                    val nameEdit = view.findViewById<EditText>(R.id.editUpdateName)
                    val phoneEdit = view.findViewById<EditText>(R.id.editUpdatePhone)

                    nameEdit.setText(user.name)
                    phoneEdit.setText(user.phone)

                    AlertDialog.Builder(this@ViewContactsActivity)
                        .setTitle("Update Contact")
                        .setView(view)
                        .setPositiveButton("Update") { _, _ ->
                            val updatedName = nameEdit.text.toString()
                            val updatedPhone = phoneEdit.text.toString()

                            if (updatedName.isNotEmpty() && updatedPhone.isNotEmpty()) {
                                val updatedUser = User(updatedName, updatedPhone, user.id)
                                database.child(user.id!!).setValue(updatedUser)
                                Toast.makeText(this@ViewContactsActivity, "Updated", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@ViewContactsActivity, "Fields can't be empty", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ViewContactsActivity, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
        })

        // 🎬 Undo button click listener
        undoButton.setOnClickListener {
            lastDeletedUser?.let { user ->
                // Restore the deleted contact
                database.child(user.id!!).setValue(user).addOnSuccessListener {
                    Toast.makeText(this@ViewContactsActivity, "Contact restored", Toast.LENGTH_SHORT).show()
                    undoButton.visibility = android.view.View.GONE // Hide undo button after restoration
                    lastDeletedUser = null

                    // Show a notification about restoring the contact
                    showNotification("Contact Restored", "${user.name} has been restored.")
                }.addOnFailureListener {
                    Toast.makeText(this@ViewContactsActivity, "Failed to restore contact", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Default Channel"
            val descriptionText = "Channel for notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("default_channel", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, "default_channel")
            .setSmallIcon(R.drawable.notify)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = NotificationManagerCompat.from(this)

        // Send the notification
        notificationManager.notify(0, builder.build())
    }
}
