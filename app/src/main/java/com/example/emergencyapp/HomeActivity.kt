package com.example.emergencyapp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var locationReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val tol=findViewById<Toolbar>(R.id.topAppBar)
        setSupportActionBar(tol)
        val btnSOS = findViewById<Button>(R.id.btnSOS)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check permissions at runtime
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        }

        // Get location on start
        getLocation()

        btnSOS.setOnClickListener {
            showDangerDialog()
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_view_contacts -> {
                // Launch ViewContactsActivity
                startActivity(Intent(this, ViewContactsActivity::class.java))
                true
            }
            R.id.menu_add_contact -> {
                // Launch AddContactActivity
                startActivity(Intent(this, MainActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    // Requesting location updates
    private fun getLocation() {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 10000
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                currentLocation = result.lastLocation
                locationReady = true
                fusedLocationClient.removeLocationUpdates(this)
            }
        }, Looper.getMainLooper())
    }

    // Show dialog when user clicks on SOS button
    private fun showDangerDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Emergency Alert")
        builder.setMessage("Are you in danger?")
        builder.setCancelable(true)

        builder.setPositiveButton("Yes") { dialog, _ ->
            sendEmergencyMessages()
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        builder.create().show()
    }

    // Send emergency message with location
    private fun sendSMS(phoneNumber: String, message: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Toast.makeText(this, "SMS sent to $phoneNumber", Toast.LENGTH_SHORT).show()
            Log.d("SMS", "SMS sent to $phoneNumber: $message")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "SMS failed for $phoneNumber: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("SMS", "Error sending SMS to $phoneNumber: ${e.message}")
        }
    }


    private fun sendEmergencyMessages() {
        if (currentLocation == null) {
            Toast.makeText(this, "Location is unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        val lat = currentLocation!!.latitude
        val lng = currentLocation!!.longitude

        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        val address = if (addresses != null && addresses.isNotEmpty()) {
            addresses[0].getAddressLine(0)
        } else {
            "Address not available"
        }

        val locationMessage = "\n📍 Location: https://maps.google.com/?q=$lat,$lng\n🏠 Address: $address"
        val finalMessage = "⚠️ Emergency! I may be in danger. Please help $locationMessage"

        // Firebase Database call to get user phone numbers
        val database = FirebaseDatabase.getInstance("https://sos-emergency-app-d0ff3-default-rtdb.asia-southeast1.firebasedatabase.app/").reference.child("Users")

        database.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                for (data in snapshot.children) {
                    val user = data.getValue(User::class.java)
                    user?.let {
                        val phone = it.phone
                        if (!phone.isNullOrEmpty()) {
                            Log.d("Firebase", "Sending SMS to: $phone")
                            sendSMS(phone, finalMessage)

                            // Send via WhatsApp
                            sendWhatsAppMessage(phone, finalMessage)
                        } else {
                            Log.d("Firebase", "No phone number found for user")
                        }
                    }
                }
            } else {
                Toast.makeText(this@HomeActivity, "No users found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this@HomeActivity, "Failed to fetch contacts", Toast.LENGTH_SHORT).show()
            Log.e("Firebase", "Failed to fetch contacts: ${it.message}")
        }
    }

    // Method to open WhatsApp with pre-filled message
    private fun sendWhatsAppMessage(phone: String, message: String) {
        try {
            val uri = "https://api.whatsapp.com/send?phone=$phone&text=$message"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(uri)
            intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://com.whatsapp"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp is not installed on your device", Toast.LENGTH_SHORT).show()
            Log.e("WhatsApp", "Error sending WhatsApp message: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
