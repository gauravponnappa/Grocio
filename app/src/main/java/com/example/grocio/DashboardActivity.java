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
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
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
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome, tvUserName, tvLogo, tvLocationBubble, tvStoreListEmpty, tvNearbyTitle, tvShoppingListPreview, tvCurrentListName, btnManageLists, tvTransportText, tvListTotal;
    private LinearLayout llStoreContainer, btnTransportMode;
    private ImageButton btnLocation, btnProfileTop, btnAdd, btnDeleteList;
    private ImageView ivTransportIcon;
    private CardView cvUploadReceipt, cvShoppingList;
    private View dashboardContent, shoppingListContent, spentContent;
    private AutoCompleteTextView etItem;
    private RecyclerView rvShoppingList, rvSpentList;
    private ShoppingListAdapter shoppingAdapter;
    private List<ShoppingItem> shoppingItems;
    private List<String> listNames;
    private String currentListName = "My Shopping List";
    private android.content.SharedPreferences sharedPreferences;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseFirestore db;
    private FirebaseDatabase rtdb;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;

    private boolean isWalkMode = false;
    private String selectedPrice = "";
    private String selectedStore = "";

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
                .child(fileName);

        com.google.firebase.storage.StorageMetadata metadata = new com.google.firebase.storage.StorageMetadata.Builder()
                .setCustomMetadata("userId", user.getUid())
                .build();

        Toast.makeText(this, "Uploading...", Toast.LENGTH_SHORT).show();

        storageRef.putFile(fileUri, metadata)
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
        rtdb = FirebaseDatabase.getInstance("https://list-d3f8b-default-rtdb.firebaseio.com/");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), "AIzaSyDjhXB43MMrsyP06ZJY7VmjvkisiPFNi1Y");
        }
        placesClient = Places.createClient(this);

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
        spentContent = findViewById(R.id.spentContent);
        etItem = findViewById(R.id.etItem);
        btnAdd = findViewById(R.id.btnAdd);
        rvShoppingList = findViewById(R.id.rvShoppingList);
        rvSpentList = findViewById(R.id.rvSpentList);
        tvCurrentListName = findViewById(R.id.tvCurrentListName);
        btnManageLists = findViewById(R.id.btnManageLists);
        btnDeleteList = findViewById(R.id.btnDeleteList);
        tvListTotal = findViewById(R.id.tvListTotal);
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
            fetchLocation();
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        setupUserDisplay();
        setupBottomNavigation(bottomNav);
        setupSearchSuggestions();
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
        
        if (!listNames.contains(currentListName)) {
            currentListName = listNames.isEmpty() ? "My Shopping List" : listNames.get(0);
        }
        
        tvCurrentListName.setText(currentListName);
        loadCurrentListItems();
        calculateTotal();
        
        shoppingAdapter = new ShoppingListAdapter(shoppingItems, new ShoppingListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                ShoppingItem item = shoppingItems.get(position);
                item.isChecked = !item.isChecked;
                saveShoppingItems();
                shoppingAdapter.notifyItemChanged(position);
                calculateTotal();
            }

            @Override
            public void onDeleteClick(int position) {
                shoppingItems.remove(position);
                saveShoppingItems();
                shoppingAdapter.notifyItemRemoved(position);
                updateShoppingListPreview();
                calculateTotal();
            }
        });
        rvShoppingList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvShoppingList.setAdapter(shoppingAdapter);

        btnAdd.setOnClickListener(v -> {
            String name = etItem.getText().toString().trim();
            if (!name.isEmpty()) {
                shoppingItems.add(new ShoppingItem(name, selectedPrice, selectedStore, false));
                saveShoppingItems();
                shoppingAdapter.notifyItemInserted(shoppingItems.size() - 1);
                etItem.setText("");
                selectedPrice = "";
                selectedStore = "";
                updateShoppingListPreview();
                calculateTotal();
            }
        });
        
        etItem.setOnItemClickListener((parent, view, position, id) -> {
            Map<String, String> suggestion = (Map<String, String>) parent.getItemAtPosition(position);
            selectedPrice = suggestion.get("price");
            selectedStore = suggestion.get("store");
        });
    }

    private void calculateTotal() {
        double total = 0;
        if (shoppingItems != null) {
            for (ShoppingItem item : shoppingItems) {
                if (!item.isChecked && item.price != null && !item.price.isEmpty()) {
                    try {
                        String cleanPrice = item.price.replace("£", "").trim();
                        if (!cleanPrice.isEmpty()) {
                            total += Double.parseDouble(cleanPrice);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        tvListTotal.setText(String.format(Locale.UK, "£%.2f", total));
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
        shoppingItems = new ArrayList<>();
        for (String csv : set) {
            shoppingItems.add(ShoppingItem.fromCsv(csv));
        }
    }

    private void saveShoppingItems() {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (ShoppingItem item : shoppingItems) {
            set.add(item.toCsv());
        }
        sharedPreferences.edit().putStringSet("shopping_list_" + currentListName, set).apply();
    }

    private void showManageListsDialog() {
        String[] options = new String[listNames.size() + 1];
        for (int i = 0; i < listNames.size(); i++) options[i] = listNames.get(i);
        options[listNames.size()] = "+ Create New List";

        new AlertDialog.Builder(this)
                .setTitle("Manage Lists")
                .setItems(options, (dialog, which) -> {
                    if (which == listNames.size()) showCreateListDialog();
                    else switchList(listNames.get(which));
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
        setupShoppingList();
    }

    private void showShoppingList(boolean show) {
        dashboardContent.setVisibility(show ? View.GONE : View.VISIBLE);
        spentContent.setVisibility(View.GONE);
        shoppingListContent.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showSpentContent() {
        dashboardContent.setVisibility(View.GONE);
        shoppingListContent.setVisibility(View.GONE);
        spentContent.setVisibility(View.VISIBLE);
        loadSpentData();
    }

    private void loadSpentData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("receipts")
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        updateSpentList(new ArrayList<>());
                        return;
                    }
                    List<String> fileNames = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots) {
                        String fileName = doc.getString("fileName");
                        if (fileName != null) {
                            String receiptId = ("receipts/" + fileName).replace(".", "_").replace("#", "_").replace("$", "_").replace("/", "_").replace("[", "_").replace("]", "_");
                            fileNames.add(receiptId);
                        }
                    }
                    fetchReceiptsFromRTDB(fileNames);
                });
    }

    private void fetchReceiptsFromRTDB(List<String> ids) {
        rtdb.getReference("receipts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Map<String, Object>> receiptsData = new ArrayList<>();
                for (String id : ids) {
                    DataSnapshot receiptSnap = snapshot.child(id);
                    if (receiptSnap.exists() && "processed".equals(receiptSnap.child("status").getValue(String.class))) {
                        Map<String, Object> data = (Map<String, Object>) receiptSnap.getValue();
                        if (data != null) receiptsData.add(data);
                    }
                }
                sortAndUpdateSpent(receiptsData);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sortAndUpdateSpent(List<Map<String, Object>> list) {
        Collections.sort(list, (o1, o2) -> {
            Long t1 = (Long) o1.get("processedAt");
            Long t2 = (Long) o2.get("processedAt");
            return Long.compare(t2 != null ? t2 : 0, t1 != null ? t1 : 0);
        });
        updateSpentList(list);
    }

    private void updateSpentList(List<Map<String, Object>> list) {
        rvSpentList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvSpentList.setAdapter(new SpentListAdapter(list));
    }

    private static class SpentListAdapter extends RecyclerView.Adapter<SpentListAdapter.ViewHolder> {
        private final List<Map<String, Object>> receipts;
        SpentListAdapter(List<Map<String, Object>> receipts) { this.receipts = receipts; }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_spent_receipt, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> data = receipts.get(position);
            Map<String, Object> store = (Map<String, Object>) data.get("store");
            Map<String, Object> receipt = (Map<String, Object>) data.get("receipt");
            Map<String, Object> summary = (Map<String, Object>) data.get("summary");
            if (store != null) {
                holder.tvStoreName.setText(String.valueOf(store.getOrDefault("name", "Unknown Store")));
                holder.tvStoreAddress.setText(String.valueOf(store.getOrDefault("address", "No Address")));
            }
            if (receipt != null) {
                Object total = receipt.get("total");
                double totalVal = 0;
                if (total instanceof Number) totalVal = ((Number) total).doubleValue();
                else if (total instanceof String) {
                    try { totalVal = Double.parseDouble((String) total); } catch (Exception ignored) {}
                }
                holder.tvTotalAmount.setText(String.format(Locale.UK, "£%.2f", totalVal));
                
                Object date = receipt.get("date");
                holder.tvDate.setText(date != null ? date.toString() : "");
            }
            if (summary != null) {
                Object count = summary.get("itemCount");
                holder.tvItemCount.setText((count != null ? count.toString() : "0") + " items");
            }
        }
        @Override public int getItemCount() { return receipts.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvStoreName, tvTotalAmount, tvStoreAddress, tvDate, tvItemCount;
            ViewHolder(View v) {
                super(v);
                tvStoreName = v.findViewById(R.id.tvStoreName); tvTotalAmount = v.findViewById(R.id.tvTotalAmount);
                tvStoreAddress = v.findViewById(R.id.tvStoreAddress); tvDate = v.findViewById(R.id.tvDate);
                tvItemCount = v.findViewById(R.id.tvItemCount);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (shoppingListContent.getVisibility() == View.VISIBLE || spentContent.getVisibility() == View.VISIBLE) showShoppingList(false);
        else super.onBackPressed();
    }

    private void updateShoppingListPreview() {
        if (shoppingItems == null || shoppingItems.isEmpty()) {
            tvShoppingListPreview.setText("No items in your list");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, shoppingItems.size()); i++) sb.append("• ").append(shoppingItems.get(i).name).append("\n");
            if (shoppingItems.size() > 3) sb.append("...");
            tvShoppingListPreview.setText(sb.toString().trim());
        }
    }

    private static class ShoppingListAdapter extends RecyclerView.Adapter<ShoppingListAdapter.ViewHolder> {
        private final List<ShoppingItem> items;
        private final OnItemClickListener listener;
        interface OnItemClickListener { void onItemClick(int position); void onDeleteClick(int position); }
        ShoppingListAdapter(List<ShoppingItem> items, OnItemClickListener l) { this.items = items; this.listener = l; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shopping_list, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ShoppingItem item = items.get(position);
            holder.tvItemName.setText(item.name);
            if (item.price != null && !item.price.isEmpty()) {
                holder.tvItemPrice.setVisibility(View.VISIBLE);
                holder.tvItemPrice.setText("£" + item.price + (item.store.isEmpty() ? "" : " at " + item.store));
            } else holder.tvItemPrice.setVisibility(View.GONE);

            if (item.isChecked) {
                holder.tvItemName.setPaintFlags(holder.tvItemName.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                holder.tvItemName.setAlpha(0.5f);
                holder.ivCheck.setImageResource(android.R.drawable.checkbox_on_background);
            } else {
                holder.tvItemName.setPaintFlags(holder.tvItemName.getPaintFlags() & (~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG));
                holder.tvItemName.setAlpha(1.0f);
                holder.ivCheck.setImageResource(android.R.drawable.checkbox_off_background);
            }
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
            holder.btnDelete.setOnClickListener(v -> listener.onDeleteClick(position));
        }
        @Override public int getItemCount() { return items.size(); }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvItemName, tvItemPrice; ImageButton btnDelete; ImageView ivCheck;
            ViewHolder(View v) { super(v); tvItemName = v.findViewById(R.id.tvItemName); tvItemPrice = v.findViewById(R.id.tvItemPrice); btnDelete = v.findViewById(R.id.btnDelete); ivCheck = v.findViewById(R.id.ivCheck); }
        }
    }

    static class ShoppingItem {
        String name, price, store;
        boolean isChecked;

        ShoppingItem(String n, String p, String s, boolean c) {
            this.name = (n != null) ? n.replace("|", " ").trim() : "";
            this.price = (p != null) ? p.replace("|", " ").trim() : "";
            this.store = (s != null) ? s.replace("|", " ").trim() : "";
            this.isChecked = c;
        }

        String toCsv() {
            return name + "|" + price + "|" + store + "|" + isChecked;
        }

        static ShoppingItem fromCsv(String csv) {
            if (csv == null || csv.isEmpty()) return new ShoppingItem("", "", "", false);
            String[] parts = csv.split("\\|", -1);
            if (parts.length >= 4) {
                return new ShoppingItem(parts[0], parts[1], parts[2], Boolean.parseBoolean(parts[3]));
            } else if (parts.length > 0) {
                return new ShoppingItem(parts[0], "", "", false);
            }
            return new ShoppingItem("", "", "", false);
        }
    }

    private void checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation();
        else locationPermissionLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }

    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> { if (location != null) updateLocationUI(location); });
    }

    private void updateLocationUI(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                tvLogo.setTextColor(ContextCompat.getColor(this, R.color.neo_mint_accent));
                tvLocationBubble.setVisibility(View.GONE);
                fetchNearbyPlaces();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void fetchNearbyPlaces() {
        List<Place.Field> fields = java.util.Arrays.asList(Place.Field.DISPLAY_NAME, Place.Field.TYPES, Place.Field.LOCATION);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        placesClient.findCurrentPlace(FindCurrentPlaceRequest.newInstance(fields)).addOnSuccessListener(response -> {
            tvStoreListEmpty.setVisibility(View.GONE); llStoreContainer.removeAllViews();
            int count = 0;
            for (PlaceLikelihood pl : response.getPlaceLikelihoods()) {
                Place p = pl.getPlace(); List<String> types = p.getPlaceTypes();
                if (types != null && (types.contains("supermarket") || types.contains("grocery_or_supermarket"))) {
                    addStoreView(p); count++;
                }
                if (count >= 5) break;
            }
            if (count == 0) showClosestStores("Unknown");
        }).addOnFailureListener(e -> showClosestStores("Unknown"));
    }

    private void addStoreView(Place place) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_supermarket, llStoreContainer, false);
        TextView tvStoreName = itemView.findViewById(R.id.tvStoreName), tvStoreDistance = itemView.findViewById(R.id.tvStoreDistance), tvStoreFuel = itemView.findViewById(R.id.tvStoreFuel), tvStoreTime = itemView.findViewById(R.id.tvStoreTime), tvStorePrice = itemView.findViewById(R.id.tvStorePrice);
        ImageView ivFuelIcon = (ImageView) ((LinearLayout)itemView.findViewById(R.id.llStoreDetails)).getChildAt(1);
        tvStoreName.setText(place.getDisplayName());
        double dist = (Math.random() * 1.5) + 0.1;
        tvStoreDistance.setText(String.format(Locale.UK, "%.1f miles", dist));
        int time = (int)(dist * (isWalkMode ? 18 : 4) + (isWalkMode ? 0 : 3));
        double fuel = isWalkMode ? 0 : dist * 0.16;
        ivFuelIcon.setVisibility(isWalkMode ? View.GONE : View.VISIBLE);
        tvStoreFuel.setVisibility(isWalkMode ? View.GONE : View.VISIBLE);
        tvStoreFuel.setText(String.format(Locale.UK, "£%.2f", fuel));
        tvStoreTime.setText(time + " min");
        tvStorePrice.setText(new String[]{"£", "££", "£££"}[(int)(Math.random() * 3)]);
        llStoreContainer.addView(itemView);
    }

    private void showClosestStores(String city) {
        llStoreContainer.removeAllViews();
        String[] names = {"Waitrose", "M&S", "Sainsbury's", "Tesco"};
        for (int i = 0; i < names.length; i++) {
            View v = LayoutInflater.from(this).inflate(R.layout.item_supermarket, llStoreContainer, false);
            ((TextView)v.findViewById(R.id.tvStoreName)).setText(names[i]);
            double dist = 0.4 + (i * 0.3);
            ((TextView)v.findViewById(R.id.tvStoreDistance)).setText(dist + " miles");
            llStoreContainer.addView(v);
        }
    }

    private void setupUserDisplay() {
        FirebaseUser user = mAuth.getCurrentUser();
        String name = (user != null && user.getDisplayName() != null) ? user.getDisplayName().split(" ")[0] : "User";
        tvUserName.setText(name);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        tvWelcome.setText(hour < 12 ? "Good Morning," : hour < 16 ? "Good Afternoon," : hour < 21 ? "Good Evening," : "Welcome Back,");
    }

    private void setupBottomNavigation(BottomNavigationView nav) {
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) { showShoppingList(false); return true; }
            if (id == R.id.nav_scan) { showScanDialog(); return true; }
            if (id == R.id.nav_list) { showShoppingList(true); return true; }
            if (id == R.id.nav_spent) { showSpentContent(); return true; }
            return false;
        });
    }

    private void showScanDialog() {
        new AlertDialog.Builder(this).setTitle("Select Source").setItems(new String[]{"Camera", "Gallery"}, (d, w) -> {
            if (w == 0) takePhotoLauncher.launch(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
            else pickImageLauncher.launch(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
        }).show();
    }

    private void showProfileMenu() {
        new AlertDialog.Builder(this).setTitle("Profile").setItems(new String[]{"Logout", "Delete Account"}, (d, w) -> {
            if (w == 0) { mAuth.signOut(); finish(); }
            else showDeleteAccountConfirmation();
        }).show();
    }

    private void showDeleteAccountConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(DashboardActivity.this, "Account deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(DashboardActivity.this, "Failed to delete account: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupSearchSuggestions() {
        class SuggestionAdapter extends android.widget.BaseAdapter implements android.widget.Filterable {
            private List<Map<String, String>> full = new ArrayList<>(), filtered = new ArrayList<>();
            public void setData(List<Map<String, String>> d) { full = d; filtered = new ArrayList<>(d); notifyDataSetChanged(); }
            @Override public int getCount() { return filtered.size(); }
            @Override public Map<String, String> getItem(int p) { return filtered.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View v, ViewGroup pr) {
                if (v == null) v = LayoutInflater.from(pr.getContext()).inflate(R.layout.item_suggestion, pr, false);
                Map<String, String> item = getItem(p);
                ((TextView)v.findViewById(R.id.tvSuggestionName)).setText(item.get("name"));
                ((TextView)v.findViewById(R.id.tvSuggestionStore)).setText(item.get("store"));
                ((TextView)v.findViewById(R.id.tvSuggestionPrice)).setText("£" + item.get("price"));
                return v;
            }
            @Override public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override protected FilterResults performFiltering(CharSequence c) {
                        List<Map<String, String>> res = new ArrayList<>();
                        if (c != null) {
                            String pat = c.toString().toLowerCase().trim();
                            for (Map<String, String> i : full) if (i.get("name").toLowerCase().contains(pat)) res.add(i);
                        }
                        FilterResults fr = new FilterResults(); fr.values = res; fr.count = res.size(); return fr;
                    }
                    @Override protected void publishResults(CharSequence c, FilterResults r) {
                        filtered.clear(); if (r.values != null) filtered.addAll((List) r.values); notifyDataSetChanged();
                    }
                    @Override public CharSequence convertResultToString(Object v) { return ((Map<String, String>) v).get("name"); }
                };
            }
        }
        SuggestionAdapter adapter = new SuggestionAdapter();
        etItem.setAdapter(adapter);
        rtdb.getReference("receipts").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                List<Map<String, String>> list = new ArrayList<>();
                for (DataSnapshot rs : s.getChildren()) {
                    String sn = rs.child("store").child("name").getValue(String.class);
                    for (DataSnapshot is : rs.child("items").getChildren()) {
                        String n = is.child("name").getValue(String.class);
                        Double p = is.child("unitPrice").getValue(Double.class);
                        if (n != null) {
                            Map<String, String> m = new HashMap<>();
                            m.put("name", n);
                            m.put("store", sn != null ? sn : "Unknown");
                            m.put("price", p != null ? String.format(Locale.UK, "%.2f", p) : "0.00");
                            list.add(m);
                        }
                    }
                }
                adapter.setData(list);
            }
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        });
    }
}