package com.example.emergencyapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private val CHANNEL_ID = "sos_channel_id"
    private val NOTIFICATION_ID = 101
    private val NOTIFICATION_PERMISSION_REQUEST = 1001
    private lateinit var dbHelper: DBHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Firebase Database
        database = FirebaseDatabase.getInstance(
            "https://sos-emergency-app-d0ff3-default-rtdb.asia-southeast1.firebasedatabase.app/"
        ).reference.child("Users")

        // Initialize SQLite DB helper
        dbHelper = DBHelper(this)

        createNotificationChannel()
        requestNotificationPermission() // Ask for permission on start

        val etName = findViewById<EditText>(R.id.editName)
        val etPhone = findViewById<EditText>(R.id.editPhone)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnView = findViewById<Button>(R.id.btnViewContacts)

        // Load last saved contact from SharedPreferences
        val sharedPref = getSharedPreferences("EmergencyAppPrefs", Context.MODE_PRIVATE)
        etName.setText(sharedPref.getString("last_name", ""))
        etPhone.setText(sharedPref.getString("last_phone", ""))

        btnView.setOnClickListener {
            val intent = Intent(this, ViewContactsActivity::class.java)
            startActivity(intent)
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.isNotEmpty() && phone.isNotEmpty()) {
                val userId = database.push().key
                val user = User(name, phone)

                if (userId != null) {
                    database.child(userId).setValue(user)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Data Saved Successfully!", Toast.LENGTH_SHORT).show()

                            // ✅ Save locally in SQLite
                            val inserted = dbHelper.insertUser(name, phone)
                            if (inserted) {
                                Toast.makeText(this, "Saved Locally in SQLite", Toast.LENGTH_SHORT).show()
                            }

                            // ✅ Save last contact in SharedPreferences
                            val editor = sharedPref.edit()
                            editor.putString("last_name", name)
                            editor.putString("last_phone", phone)
                            editor.apply()

                            etName.text.clear()
                            etPhone.text.clear()
                            showNotification(name, phone)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to Save Data", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(this, "Please enter all details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔔 Create Notification Channel
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SOS Alert Channel"
            val descriptionText = "Channel for emergency contact alerts"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 🚨 Show Notification
    private fun showNotification(name: String, phone: String) {
        val intent = Intent(this, ViewContactsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.notify)
            .setContentTitle("Emergency Contact Added")
            .setContentText("$name - $phone has been added")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            with(NotificationManagerCompat.from(this)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    // 📩 Request Notification Permission (for Android 13+)
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    // 🔁 Handle Permission Result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// ✅ SQLite DBHelper class
class DBHelper(context: Context) : SQLiteOpenHelper(context, "UserDB", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS Users(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, phone TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS Users")
        onCreate(db)
    }

    fun insertUser(name: String, phone: String): Boolean {
        val db = writableDatabase
        val cv = ContentValues()
        cv.put("name", name)
        cv.put("phone", phone)
        return db.insert("Users", null, cv) != -1L
    }
}
