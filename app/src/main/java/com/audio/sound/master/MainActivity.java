package com.audio.sound.master;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    
    private SharedPreferences prefs;
    private ArrayList<String> configList;
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 100);
            }
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            }
        }

        prefs = getSharedPreferences("VMP_CONFIGS", MODE_PRIVATE);
        Set<String> savedSet = prefs.getStringSet("CONFIG_NAMES", new HashSet<String>());
        configList = new ArrayList<>(savedSet);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 50, 50, 50);

        Button startBtn = new Button(this);
        startBtn.setText("START VMP-X STEALTH ENGINE");
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 🔥 CRASH SHIELD: Ab app crash hone ke bajaye error batayegi
                try {
                    Intent serviceIntent = new Intent(MainActivity.this, PremiumAudioService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    Toast.makeText(MainActivity.this, "Engine Starting...", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "MAIN CRASH: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        mainLayout.addView(startBtn);

        final EditText configInput = new EditText(this);
        configInput.setHint("Enter Custom Config Name");
        mainLayout.addView(configInput);

        Button saveBtn = new Button(this);
        saveBtn.setText("SAVE CURRENT SETTINGS");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = configInput.getText().toString();
                if(!name.isEmpty()) {
                    try {
                        // 🔥 FIX: currentSpatial variable hata diya gaya hai kyunki ab wo hardware par automatically chalta hai
                        PremiumAudioService.saveConfiguration(name, VMP_ControllerView.currentBands, 0);
                        
                        if(!configList.contains(name)) {
                            configList.add(name);
                            prefs.edit().putStringSet("CONFIG_NAMES", new HashSet<>(configList)).apply();
                            adapter.notifyDataSetChanged();
                        }
                        Toast.makeText(MainActivity.this, "Saved!", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Save Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        mainLayout.addView(saveBtn);

        final Spinner configSpinner = new Spinner(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, configList);
        configSpinner.setAdapter(adapter);
        mainLayout.addView(configSpinner);

        Button loadBtn = new Button(this);
        loadBtn.setText("LOAD SELECTED CONFIG");
        loadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(configSpinner.getSelectedItem() != null) {
                    PremiumAudioService.loadConfiguration(configSpinner.getSelectedItem().toString());
                    Toast.makeText(MainActivity.this, "Loaded!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        mainLayout.addView(loadBtn);

        setContentView(mainLayout);
    }
            }
          
