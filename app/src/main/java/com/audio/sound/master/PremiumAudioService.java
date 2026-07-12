package com.audio.sound.master;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PremiumAudioService extends Service {

    private static PremiumAudioService instance;
    private long nativeProcessorHandle = 0; 
    private FloatingMenuWindow uiWindow;
    private SharedPreferences memoryMatrix;
    private static final String CHANNEL_ID = "vmp_warp_speed_core";
    
    private AudioManager audioManager;
    private ConcurrentHashMap<Integer, Equalizer> activeEqualizers = new ConcurrentHashMap<>();

    private native long initNativeEngine();
    private native void setNativeBandGain(long handle, int band, float gain);
    private native void releaseNativeEngine(long handle);

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("SHOW_VMP_UI".equals(action) && uiWindow != null) {
                uiWindow.showIcon();
            } else if ("STOP_VMP_ENGINE".equals(action)) {
                if (uiWindow != null) uiWindow.hideUI();
                stopSelf(); 
            }
        }
    };

    private AudioManager.AudioPlaybackCallback playbackCallback = new AudioManager.AudioPlaybackCallback() {
        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            super.onPlaybackConfigChanged(configs);
            // 🔥 FIX: Android 9+ (API 28) ke liye theek methods ka istemaal kiya gaya hai
            if (configs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                for (AudioPlaybackConfiguration config : configs) {
                    if (config.getPlayerState() == AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
                        int sessionId = config.getSessionId();
                        if (sessionId != 0 && sessionId != AudioManager.AUDIO_SESSION_ID_GENERATE) {
                            attachStealthEqToSession(sessionId);
                        }
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        memoryMatrix = getSharedPreferences("VMP_CONFIGS", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        verifyVMPSecurityMatrix(); 

        IntentFilter filter = new IntentFilter();
        filter.addAction("SHOW_VMP_UI");
        filter.addAction("STOP_VMP_ENGINE");
        
        if (Build.VERSION.SDK_INT >= 34) { 
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(actionReceiver, filter);
        }
        
        try {
            System.loadLibrary("elite_audio_engine");
            lockServiceToForegroundMemory();
            nativeProcessorHandle = initNativeEngine();
        } catch (Throwable t) {
            showToast("FATAL ERROR: " + t.getMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            audioManager.registerAudioPlaybackCallback(playbackCallback, new Handler(Looper.getMainLooper()));
            playbackCallback.onPlaybackConfigChanged(audioManager.getActivePlaybackConfigurations());
            attachStealthEqToSession(0);
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) uiWindow = new FloatingMenuWindow(this);
            } else {
                uiWindow = new FloatingMenuWindow(this);
            }
        } catch (Throwable t) {
            showToast("UI Error: " + t.getMessage());
        }
    }

    private void attachStealthEqToSession(int sessionId) {
        if (!activeEqualizers.containsKey(sessionId)) {
            try {
                Equalizer eq = new Equalizer(10000, sessionId);
                eq.setEnabled(true);
                activeEqualizers.put(sessionId, eq);
                
                if (VMP_ControllerView.currentBands != null) {
                    for (int i = 0; i < 10; i++) {
                        applyGainToEq(eq, i, VMP_ControllerView.currentBands[i]);
                    }
                }
            } catch (Exception e) {
                // Ignore silent blockages
            }
        }
    }

    private void verifyVMPSecurityMatrix() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            System.setProperty("vmp.engine.integrity", "locked");
        }
    }

    private void showToast(final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PremiumAudioService.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void lockServiceToForegroundMemory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "VMP Hardware Core", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Elite Audio Processing Stream");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Intent showIntent = new Intent("SHOW_VMP_UI");
        PendingIntent pShow = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent("STOP_VMP_ENGINE");
        PendingIntent pStop = PendingIntent.getBroadcast(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        
        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        Notification notification = builder
            .setContentTitle("AIDE Pro VMP Audio Engine")
            .setContentText("Trillion times warp speed processing active")
            .setContentIntent(pShow)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP ENGINE", pStop)
            .setOngoing(true)
            .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(202, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(202, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(202, notification);
        }
    }

    public static void updateBand(int bandIndex, float gainValue) {
        if (instance != null && instance.nativeProcessorHandle != 0) {
            instance.setNativeBandGain(instance.nativeProcessorHandle, bandIndex, gainValue);
        }
        
        if (instance != null) {
            for (Equalizer eq : instance.activeEqualizers.values()) {
                applyGainToEq(eq, bandIndex, gainValue);
            }
        }
    }

    private static void applyGainToEq(Equalizer eq, int bandIndex, float gainValue) {
        if (eq != null && eq.getEnabled()) {
            try {
                short numBands = eq.getNumberOfBands();
                if (numBands > 0) {
                    short hardwareBand = (short) ((bandIndex * numBands) / 10);
                    short millibels = (short) (gainValue * 100);
                    
                    short[] range = eq.getBandLevelRange();
                    if (millibels < range[0]) millibels = range[0];
                    if (millibels > range[1]) millibels = range[1];
                    
                    eq.setBandLevel(hardwareBand, millibels);
                }
            } catch (Exception e) {}
        }
    }
    
    public static void updateSpatialAwareness(int strengthValue) { }

    public static void loadConfiguration(String configName) {
        if (instance != null) {
            for(int i = 0; i < 10; i++) {
                float savedGain = instance.memoryMatrix.getFloat(configName + "_band_" + i, 0f);
                updateBand(i, savedGain);
                if (VMP_ControllerView.currentBands != null && VMP_ControllerView.currentBands.length > i) {
                    VMP_ControllerView.currentBands[i] = savedGain; 
                }
            }
        }
    }

    public static void saveConfiguration(String name, float[] bands, int spatial) {
        if (instance != null && bands != null && bands.length >= 10) {
            SharedPreferences.Editor editor = instance.memoryMatrix.edit();
            for(int i = 0; i < 10; i++) {
                editor.putFloat(name + "_band_" + i, bands[i]);
            }
            editor.apply();
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(actionReceiver);
        } catch (IllegalArgumentException e) {}
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback);
        }
        
        if (nativeProcessorHandle != 0) {
            releaseNativeEngine(nativeProcessorHandle);
            nativeProcessorHandle = 0;
        }
        
        for (Equalizer eq : activeEqualizers.values()) {
            if (eq != null) {
                eq.setEnabled(false);
                eq.release();
            }
        }
        activeEqualizers.clear();
        
        if (uiWindow != null) {
            uiWindow.hideUI();
            uiWindow = null;
        }
        
        instance = null;
        super.onDestroy();
    }
        }
                
