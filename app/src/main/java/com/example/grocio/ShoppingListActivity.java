package com.example.grocio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShoppingListActivity extends AppCompatActivity {

    private EditText etItem;
    private ImageButton btnAdd;
    private RecyclerView rvShoppingList;
    private ShoppingListAdapter adapter;
    private List<String> items;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etItem = findViewById(R.id.etItem);
        btnAdd = findViewById(R.id.btnAdd);
        rvShoppingList = findViewById(R.id.rvShoppingList);

        sharedPreferences = getSharedPreferences("GrocioPrefs", Context.MODE_PRIVATE);
        loadItems();

        adapter = new ShoppingListAdapter(items, this::removeItem);
        rvShoppingList.setLayoutManager(new LinearLayoutManager(this));
        rvShoppingList.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            String newItem = etItem.getText().toString().trim();
            if (!newItem.isEmpty()) {
                items.add(newItem);
                saveItems();
                adapter.notifyItemInserted(items.size() - 1);
                etItem.setText("");
            } else {
                Toast.makeText(this, "Please enter an item", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadItems() {
        Set<String> set = sharedPreferences.getStringSet("shopping_list", new HashSet<>());
        items = new ArrayList<>(set);
    }

    private void saveItems() {
        sharedPreferences.edit().putStringSet("shopping_list", new HashSet<>(items)).apply();
    }

    private void removeItem(int position) {
        items.remove(position);
        saveItems();
        adapter.notifyItemRemoved(position);
    }

    private static class ShoppingListAdapter extends RecyclerView.Adapter<ShoppingListAdapter.ViewHolder> {
        private final List<String> items;
        private final OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(int position);
        }

        ShoppingListAdapter(List<String> items, OnItemClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.textView.setText(items.get(position));
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;

            ViewHolder(View view) {
                super(view);
                textView = view.findViewById(android.R.id.text1);
            }
        }
    }
}