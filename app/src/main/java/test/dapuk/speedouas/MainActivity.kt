package test.dapuk.speedouas

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var speedTextView: TextView
    private lateinit var distanceTextView: TextView
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val tripDataList = mutableListOf<Map<String, Any>>()
    private var lastLocation: Location? = null
    private var totalDistance = 0.0
    private var isTransitioning = false
    private val handler = Handler(Looper.getMainLooper())
    private val transitionDelay = 150L  // Delay default untuk fragment lainnya
    private val mapsTransitionDelay = 300L  // Delay khusus untuk MapsFragment
    private val buttonClickDelay = 1000L  // Delay untuk mengaktifkan tombol kembali setelah transisi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activity_main_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        firestore = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        speedTextView = findViewById(R.id.speedTextView)
        distanceTextView = findViewById(R.id.distanceTextView)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocation(location)
                    val speed = location.speed
                    val speedInKmh = speed * 3.6
                    speedTextView.text = "Kecepatan: %.2f km/jam".format(speedInKmh)

                    val tripData = mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "speed" to speedInKmh,
                        "timestamp" to System.currentTimeMillis()
                    )

                    tripDataList.add(tripData)
                }
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        findViewById<View>(R.id.startButton).setOnClickListener { startTrip() }
        findViewById<View>(R.id.stopButton).setOnClickListener { stopTrip() }

        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            if (isTransitioning) {
                return@setOnNavigationItemSelectedListener false
            }

            isTransitioning = true
            setButtonsClickable(false)  // Nonaktifkan tombol

            val delay = when (item.itemId) {
                R.id.nav_maps -> mapsTransitionDelay
                else -> transitionDelay
            }

            handler.postDelayed({
                loadFragmentWhenReady(item.itemId)
                handler.postDelayed({
                    setButtonsClickable(true)  // Aktifkan tombol kembali setelah delay
                    isTransitioning = false
                }, buttonClickDelay)
            }, delay)

            true
        }

        // Muat SpeedometerFragment secara default pada aplikasi dimulai
        if (savedInstanceState == null) {
            loadFragment(SpeedometerFragment())
        }
    }

    private fun setButtonsClickable(clickable: Boolean) {
        findViewById<View>(R.id.startButton).isClickable = clickable
        findViewById<View>(R.id.stopButton).isClickable = clickable
    }

    private fun loadFragmentWhenReady(menuId: Int) {
        when (menuId) {
            R.id.nav_main -> loadFragment(SpeedometerFragment())
            R.id.nav_trip_detail -> loadFragment(TripDetailFragment())
            R.id.nav_maps -> loadFragment(MapsFragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun startTrip() {
        val locationRequest = LocationRequest.create().apply {
            interval = 1000  // Set interval to 1 second
            fastestInterval = 1000  // Set fastest interval to 1 second
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopTrip() {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Hitung total jarak, kecepatan maksimum, minimum, dan rata-rata
        var maxSpeed = 0.0
        var minSpeed = Double.MAX_VALUE
        var totalSpeed = 0.0
        var numSpeedRecords = 0

        for (tripData in tripDataList) {
            val speed = tripData["speed"] as? Double ?: 0.0
            if (speed > maxSpeed) {
                maxSpeed = speed
            }
            if (speed < minSpeed) {
                minSpeed = speed
            }
            totalSpeed += speed
            numSpeedRecords++
        }

        val averageSpeed = if (numSpeedRecords > 0) totalSpeed / numSpeedRecords else 0.0

        // Periksa apakah semua data adalah 0 atau null
        if (totalDistance > 0 || maxSpeed > 0 || minSpeed < Double.MAX_VALUE || averageSpeed > 0) {
            // Simpan data perjalanan ke Firestore
            val tripDocument = firestore.collection("trips").document()
            val tripData = mapOf(
                "totalDistance" to totalDistance,
                "maxSpeed" to maxSpeed,
                "minSpeed" to minSpeed,
                "averageSpeed" to averageSpeed,
                "timestamp" to System.currentTimeMillis()  // Tambahkan timestamp
            )
            tripDocument.set(tripData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Berhasil Menyimpan Data", Toast.LENGTH_SHORT).show()

                    // Reset data setelah menyimpan
                    resetTripData()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Error menyimpan data perjalanan: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } else {
            // Tampilkan pesan jika data tidak valid
            Toast.makeText(this, "Belum mulai jalan anda. joging sana atau balap", Toast.LENGTH_SHORT).show()
            // Reset data setelah gagal menyimpan
            resetTripData()
        }
    }

    private fun resetTripData() {
        totalDistance = 0.0
        lastLocation = null
        tripDataList.clear()
        distanceTextView.text = "Jarak: 0.00 km"
        speedTextView.text = "Kecepatan: 0.00 km/jam"
    }

    private fun updateLocation(location: Location) {
        val currentLocation = location
        if (lastLocation != null) {
            val distance = lastLocation!!.distanceTo(currentLocation).toDouble() / 1000 // Convert to kilometers
            totalDistance += distance
            distanceTextView.text = "Jarak: %.2f km".format(totalDistance)
        }
        lastLocation = currentLocation
    }
}
