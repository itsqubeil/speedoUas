package test.dapuk.speedouas

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import test.dapuk.speedouas.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripDetailFragment : Fragment() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var tripDetailsContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_trip_details, container, false)
        tripDetailsContainer = view.findViewById(R.id.tripDetailsContainer)
        firestore = FirebaseFirestore.getInstance()
        loadTripDetails()
        return view
    }

    private fun loadTripDetails() {
        firestore.collection("trips")
            .orderBy("timestamp", Query.Direction.DESCENDING) // Order by timestamp
            .get()
            .addOnSuccessListener { result ->
                tripDetailsContainer.removeAllViews() // Clear previous views
                for (document in result) {
                    val tripData = document.data
                    val distance = tripData["totalDistance"] as? Double ?: 0.0
                    val maxSpeed = tripData["maxSpeed"] as? Double ?: 0.0
                    val minSpeed = tripData["minSpeed"] as? Double ?: 0.0
                    val averageSpeed = tripData["averageSpeed"] as? Double ?: 0.0
                    val timestamp = tripData["timestamp"] as? Long ?: 0L

                    val date = Date(timestamp)
                    val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                    val dateString = dateFormat.format(date)

                    // Create a new CardView for each trip data
                    val cardView = layoutInflater.inflate(R.layout.card_trip_detail, tripDetailsContainer, false)
                    val distanceTextView: TextView = cardView.findViewById(R.id.card_distance_text)
                    val maxSpeedTextView: TextView = cardView.findViewById(R.id.card_max_speed_text)
                    val minSpeedTextView: TextView = cardView.findViewById(R.id.card_min_speed_text)
                    val averageSpeedTextView: TextView = cardView.findViewById(R.id.card_average_speed_text)
//                    val timestampTextView: TextView = cardView.findViewById(R.id.card_timestamp_text)

                    distanceTextView.text = "Total Distance: %.2f km".format(distance)
                    maxSpeedTextView.text = "Max Speed: %.2f km/h".format(maxSpeed)
                    minSpeedTextView.text = "Min Speed: %.2f km/h".format(minSpeed)
                    averageSpeedTextView.text = "Average Speed: %.2f km/h".format(averageSpeed)
//                    timestampTextView.text = "Timestamp: $dateString"

                    tripDetailsContainer.addView(cardView)
                }
            }
            .addOnFailureListener { e ->
                // Handle error
            }
    }
}
