package com.example.grocio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvUserName, tvLogo, tvLocationBubble, tvStoreListEmpty, tvNearbyTitle, tvShoppingListPreview, tvCurrentListName, btnManageLists, tvTransportText;
    private LinearLayout llStoreContainer, btnTransportMode;
    private ImageButton btnLocation, btnProfileTop, btnAdd, btnDeleteList;
    private ImageView ivTransportIcon;
    private CardView cvUploadReceipt, cvShoppingList;
    private View dashboardContent, shoppingListContent;
    private EditText etItem;
    private RecyclerView rvShoppingList;
    private ShoppingListAdapter adapter;
    private java.util.List<String> shoppingItems;
    private java.util.List<String> listNames;
    private String currentListName = "My Shopping List";
    private android.content.SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedLocationClient;

    private boolean isWalkMode = false;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (fineLocationGranted != null && fineLocationGranted) {
                    fetchLocation();
                } else if (coarseLocationGranted != null && coarseLocationGranted) {
                    fetchLocation();
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    uploadReceipt(imageUri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> takePhotoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.graphics.Bitmap photo = (android.graphics.Bitmap) result.getData().getExtras().get("data");
                    Uri tempUri = getImageUri(photo);
                    uploadReceipt(tempUri);
                }
            }
    );

    private Uri getImageUri(android.graphics.Bitmap bitmap) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "Receipt_" + System.currentTimeMillis(), null);
        return Uri.parse(path);
    }

    private void uploadReceipt(Uri fileUri) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || fileUri == null) return;

        String fileName = "receipt_" + System.currentTimeMillis() + ".jpg";
        StorageReference storageRef = storage.getReference()
                .child("receipts")
                .child(user.getUid())
                .child(fileName);

        Toast.makeText(this, "Uploading receipt...", Toast.LENGTH_SHORT).show();

        storageRef.putFile(fileUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return storageRef.getDownloadUrl();
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();
                        saveReceiptMetadata(user.getUid(), fileName, downloadUri.toString());
                    } else {
                        Toast.makeText(DashboardActivity.this, "Upload failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveReceiptMetadata(String userId, String fileName, String downloadUrl) {
        Map<String, Object> receipt = new HashMap<>();
        receipt.put("userId", userId);
        receipt.put("fileName", fileName);
        receipt.put("url", downloadUrl);
        receipt.put("timestamp", com.google.firebase.Timestamp.now());

        db.collection("receipts")
                .add(receipt)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(DashboardActivity.this, "Receipt saved to Firestore!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DashboardActivity.this, "Error saving metadata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserName = findViewById(R.id.tvUserName);
        tvLogo = findViewById(R.id.tvLogo);
        tvLocationBubble = findViewById(R.id.tvLocationBubble);
        tvStoreListEmpty = findViewById(R.id.tvStoreListEmpty);
        llStoreContainer = findViewById(R.id.llStoreContainer);
        btnLocation = findViewById(R.id.btnLocation);
        btnProfileTop = findViewById(R.id.btnProfileTop);
        cvUploadReceipt = findViewById(R.id.cvUploadReceipt);
        tvNearbyTitle = findViewById(R.id.tvNearbyTitle);
        btnTransportMode = findViewById(R.id.btnTransportMode);
        cvShoppingList = findViewById(R.id.cvShoppingList);
        tvShoppingListPreview = findViewById(R.id.tvShoppingListPreview);
        dashboardContent = findViewById(R.id.dashboardContent);
        shoppingListContent = findViewById(R.id.shoppingListContent);
        etItem = findViewById(R.id.etItem);
        btnAdd = findViewById(R.id.btnAdd);
        rvShoppingList = findViewById(R.id.rvShoppingList);
        tvCurrentListName = findViewById(R.id.tvCurrentListName);
        btnManageLists = findViewById(R.id.btnManageLists);
        btnDeleteList = findViewById(R.id.btnDeleteList);
        btnTransportMode = findViewById(R.id.btnTransportMode);
        ivTransportIcon = findViewById(R.id.ivTransportIcon);
        tvTransportText = findViewById(R.id.tvTransportText);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        sharedPreferences = getSharedPreferences("GrocioPrefs", MODE_PRIVATE);
        setupShoppingList();

        btnLocation.setOnClickListener(v -> checkLocationPermissions());
        btnProfileTop.setOnClickListener(v -> showProfileMenu());
        cvUploadReceipt.setOnClickListener(v -> showScanDialog());
        cvShoppingList.setOnClickListener(v -> showShoppingList(true));
        btnManageLists.setOnClickListener(v -> showManageListsDialog());
        btnDeleteList.setOnClickListener(v -> showDeleteListConfirmation());

        btnTransportMode.setOnClickListener(v -> {
            isWalkMode = !isWalkMode;
            ivTransportIcon.setImageResource(isWalkMode ? R.drawable.ic_walk : R.drawable.ic_car);
            tvTransportText.setText(isWalkMode ? "Walking" : "Driving");
            fetchLocation(); // Refresh data with new mode
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0); // Keep bottom padding for nav
            return insets;
        });

        setupUserDisplay();
        setupBottomNavigation(bottomNav);
        
        // Initial check on launch
        checkLocationPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateShoppingListPreview();
    }

    private void setupShoppingList() {
        java.util.Set<String> namesSet = sharedPreferences.getStringSet("list_names", new java.util.HashSet<>(java.util.Arrays.asList("My Shopping List")));
        listNames = new java.util.ArrayList<>(namesSet);
        currentListName = sharedPreferences.getString("current_list_name", "My Shopping List");
        
        // Ensure current list is in the list names
        if (!listNames.contains(currentListName)) {
            if (!listNames.isEmpty()) {
                currentListName = listNames.get(0);
            } else {
                currentListName = "My Shopping List";
                listNames.add(currentListName);
            }
        }
        
        tvCurrentListName.setText(currentListName);
        loadCurrentListItems();
        
        adapter = new ShoppingListAdapter(shoppingItems, new ShoppingListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                // Toggle completion or similar if needed
            }

            @Override
            public void onDeleteClick(int position) {
                shoppingItems.remove(position);
                saveShoppingItems();
                adapter.notifyItemRemoved(position);
                updateShoppingListPreview();
            }
        });
        rvShoppingList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvShoppingList.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            String item = etItem.getText().toString().trim();
            if (!item.isEmpty()) {
                shoppingItems.add(item);
                saveShoppingItems();
                adapter.notifyItemInserted(shoppingItems.size() - 1);
                etItem.setText("");
                updateShoppingListPreview();
            }
        });
    }

    private void showDeleteListConfirmation() {
        if (listNames.size() <= 1) {
            Toast.makeText(this, "Cannot delete the last list", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete List")
                .setMessage("Are you sure you want to delete '" + currentListName + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    listNames.remove(currentListName);
                    sharedPreferences.edit().remove("shopping_list_" + currentListName).apply();
                    sharedPreferences.edit().putStringSet("list_names", new java.util.HashSet<>(listNames)).apply();
                    switchList(listNames.get(0));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadCurrentListItems() {
        java.util.Set<String> set = sharedPreferences.getStringSet("shopping_list_" + currentListName, new java.util.HashSet<>());
        shoppingItems = new java.util.ArrayList<>(set);
    }

    private void saveShoppingItems() {
        sharedPreferences.edit().putStringSet("shopping_list_" + currentListName, new java.util.HashSet<>(shoppingItems)).apply();
    }

    private void showManageListsDialog() {
        String[] options = new String[listNames.size() + 1];
        for (int i = 0; i < listNames.size(); i++) {
            options[i] = listNames.get(i);
        }
        options[listNames.size()] = "+ Create New List";

        new AlertDialog.Builder(this)
                .setTitle("Manage Lists")
                .setItems(options, (dialog, which) -> {
                    if (which == listNames.size()) {
                        showCreateListDialog();
                    } else {
                        switchList(listNames.get(which));
                    }
                })
                .show();
    }

    private void showCreateListDialog() {
        EditText input = new EditText(this);
        input.setHint("List Name");
        new AlertDialog.Builder(this)
                .setTitle("New List")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty() && !listNames.contains(name)) {
                        listNames.add(name);
                        sharedPreferences.edit().putStringSet("list_names", new java.util.HashSet<>(listNames)).apply();
                        switchList(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void switchList(String name) {
        currentListName = name;
        sharedPreferences.edit().putString("current_list_name", currentListName).apply();
        tvCurrentListName.setText(currentListName);
        loadCurrentListItems();
        setupShoppingList(); // Re-setup to refresh adapter and view
    }

    private void showShoppingList(boolean show) {
        if (show) {
            dashboardContent.setVisibility(View.GONE);
            shoppingListContent.setVisibility(View.VISIBLE);
        } else {
            shoppingListContent.setVisibility(View.GONE);
            dashboardContent.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (shoppingListContent.getVisibility() == View.VISIBLE) {
            showShoppingList(false);
        } else {
            super.onBackPressed();
        }
    }

    private void updateShoppingListPreview() {
        if (shoppingItems == null || shoppingItems.isEmpty()) {
            tvShoppingListPreview.setText("No items in your list");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, shoppingItems.size()); i++) {
                sb.append("• ").append(shoppingItems.get(i)).append("\n");
            }
            if (shoppingItems.size() > 3) sb.append("...");
            tvShoppingListPreview.setText(sb.toString().trim());
        }
    }

    private static class ShoppingListAdapter extends RecyclerView.Adapter<ShoppingListAdapter.ViewHolder> {
        private final java.util.List<String> items;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(int position);
            void onDeleteClick(int position);
        }

        ShoppingListAdapter(java.util.List<String> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shopping_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.tvItemName.setText(items.get(position));
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
            holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvItemName;
            ImageButton btnDelete;

            ViewHolder(View view) {
                super(view);
                tvItemName = view.findViewById(R.id.tvItemName);
                btnDelete = view.findViewById(R.id.btnDelete);
            }
        }
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                updateLocationUI(location);
            } else {
                Toast.makeText(this, "Failed to get location. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateLocationUI(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String city = addresses.get(0).getLocality();
                if (city == null) city = addresses.get(0).getSubAdminArea();
                
                // Update UI state to success
                tvLogo.setTextColor(ContextCompat.getColor(this, R.color.neo_mint_accent));
                tvLocationBubble.setVisibility(View.GONE);
                
                showClosestStores(city);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showClosestStores(String city) {
        tvStoreListEmpty.setVisibility(View.GONE);
        llStoreContainer.removeAllViews();
        
        String[] names = {"Waitrose & Partners", "Marks & Spencer", "Sainsbury's Local", "Tesco Superstore"};
        double[] distanceValues = {0.4, 0.9, 1.2, 1.5};
        String[] prices = {"£££", "£££", "££", "£"};

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < names.length; i++) {
            View itemView = inflater.inflate(R.layout.item_supermarket, llStoreContainer, false);
            
            ImageView ivStoreArrow = itemView.findViewById(R.id.ivStoreArrow);
            TextView tvStoreName = itemView.findViewById(R.id.tvStoreName);
            TextView tvStoreDistance = itemView.findViewById(R.id.tvStoreDistance);
            TextView tvStoreFuel = itemView.findViewById(R.id.tvStoreFuel);
            TextView tvStoreTime = itemView.findViewById(R.id.tvStoreTime);
            TextView tvStorePrice = itemView.findViewById(R.id.tvStorePrice);
            ImageView ivFuelIcon = (ImageView) ((LinearLayout)itemView.findViewById(R.id.llStoreDetails)).getChildAt(1);

            // Set random rotation to the arrow
            float randomRotation = (float) (Math.random() * 360);
            ivStoreArrow.setRotation(randomRotation);

            tvStoreName.setText(names[i]);
            tvStoreDistance.setText(distanceValues[i] + " miles");
            
            // Calculate values
            int timeMins;
            double fuelCost;
            
            if (isWalkMode) {
                timeMins = (int) (distanceValues[i] * 20); // 20 mins per mile
                fuelCost = 0.0;
                ivFuelIcon.setAlpha(0.3f);
            } else {
                timeMins = (int) (distanceValues[i] * 5 + 2); // 5 mins per mile + traffic
                fuelCost = distanceValues[i] * 0.15; // 15p per mile
                ivFuelIcon.setAlpha(1.0f);
            }

            tvStoreFuel.setText(String.format(Locale.UK, "£%.2f", fuelCost));
            tvStoreTime.setText(timeMins + " min");
            tvStorePrice.setText(prices[i]);

            llStoreContainer.addView(itemView);

            // Add divider if not last
            if (i < names.length - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1);
                params.setMargins(180, 0, 0, 0); // Align with text
                divider.setLayoutParams(params);
                divider.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                divider.setAlpha(0.2f);
                llStoreContainer.addView(divider);
            }
        }
    }

    private void setupUserDisplay() {
        FirebaseUser user = mAuth.getCurrentUser();
        String fullName = (user != null && user.getDisplayName() != null) ? user.getDisplayName() : "Grocio User";
        
        // Use first name only
        String firstName = fullName.split(" ")[0];
        tvUserName.setText(firstName);

        // Dynamic Greeting
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (timeOfDay < 12) {
            greeting = "Good Morning,";
        } else if (timeOfDay < 16) {
            greeting = "Good Afternoon,";
        } else if (timeOfDay < 21) {
            greeting = "Good Evening,";
        } else {
            greeting = "Good Night,";
        }
        tvWelcome.setText(greeting);
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                showShoppingList(false);
                return true;
            } else if (itemId == R.id.nav_scan) {
                showScanDialog();
                return true;
            } else if (itemId == R.id.nav_list) {
                showShoppingList(true);
                return true;
            }
            return false;
        });
    }

    private void showScanDialog() {
        String[] options = {"Camera", "Upload from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Select Image Source")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        takePhotoLauncher.launch(takePictureIntent);
                    } else {
                        Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        pickImageLauncher.launch(pickPhotoIntent);
                    }
                })
                .show();
    }

    private void showProfileMenu() {
        String[] options = {"My Orders", "Address Book", "Payment Methods", "Notifications", "Logout", "Delete Account & Data"};
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Profile Settings");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // My Orders
                    Toast.makeText(this, "Opening Orders...", Toast.LENGTH_SHORT).show();
                    break;
                case 4: // Logout
                    logoutUser();
                    break;
                case 5: // Delete Account
                    showDeleteConfirmation();
                    break;
                default:
                    Toast.makeText(this, "Selected: " + options[which], Toast.LENGTH_SHORT).show();
                    break;
            }
        });
        builder.show();
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        // Redirect to Login if needed
        finish();
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account and all associated data? This action is permanent.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(DashboardActivity.this, "Account deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(DashboardActivity.this, "Failed to delete account", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }
}