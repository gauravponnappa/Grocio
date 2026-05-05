package com.example.grocio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

public class LocationSearchActivity extends AppCompatActivity {

    private static final String SERVER_URL = "http://YOUR_GOOGLE_VM_IP/search"; // Replace with your VM IP
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();
    private FusedLocationProviderClient fusedLocationClient;
    private String currentLocationString = "Unknown Location";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_search);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fetchCurrentLocation();

        SearchView searchView = findViewById(R.id.searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                sendSearchToServer(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                updateLocationString(location);
            }
        });
    }

    private void updateLocationString(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                currentLocationString = address.getLocality() + ", " + address.getCountryName();
            }
        } catch (IOException e) {
            e.printStackTrace();
            currentLocationString = location.getLatitude() + ", " + location.getLongitude();
        }
    }

    private void sendSearchToServer(String query) {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) userId = "anonymous";

        JSONObject json = new JSONObject();
        try {
            json.put("query", query);
            json.put("location", currentLocationString);
            json.put("userId", userId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("LocationSearch", "Server error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(LocationSearchActivity.this, "Failed to connect to server", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    final String responseData = response.body().string();
                    Log.d("LocationSearch", "Server response: " + responseData);
                    runOnUiThread(() -> Toast.makeText(LocationSearchActivity.this, "Search sent successfully", Toast.LENGTH_SHORT).show());
                } else {
                    Log.e("LocationSearch", "Unexpected code " + response);
                    runOnUiThread(() -> Toast.makeText(LocationSearchActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}
