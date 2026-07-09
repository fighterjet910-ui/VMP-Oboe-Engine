package com.audio.sound.master;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.view.ViewGroup;

public class VMP_ControllerView extends LinearLayout {

    private FloatingMenuWindow parentWindow;
    private SeekBar[] eqSeekBars = new SeekBar[10];
    private LinearLayout scrollContent; 
    
    public static float[] currentBands = new float[10]; 

    public VMP_ControllerView(Context context, FloatingMenuWindow window) {
        super(context);
        this.parentWindow = window;
        
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#E6080808")); 
        setPadding(25, 20, 25, 25);
        setLayoutParams(new LinearLayout.LayoutParams(600, ViewGroup.LayoutParams.WRAP_CONTENT));

        createHeaderSection(context);

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int safeHeight = (int) (Math.min(metrics.widthPixels, metrics.heightPixels) * 0.75);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, safeHeight
        );
        scrollView.setLayoutParams(scrollParams);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setScrollbarFadingEnabled(false);

        scrollContent = new LinearLayout(context);
        scrollContent.setOrientation(VERTICAL);
        scrollView.addView(scrollContent);
        addView(scrollView); 

        createActionSection(context); 
        
        // Sirf manual tuning wale sliders yahan rahenge
        addSetting(context, "Low-End (Bass/Sub)", 0);
        addSetting(context, "Footsteps (2.5kHz - MAX)", 4);
        addSetting(context, "Gunshots (4kHz)", 5);
        addSetting(context, "Air/Details (10kHz)", 8);
    }

    private void createHeaderSection(Context ctx) {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 15);

        TextView title = new TextView(ctx);
        title.setText("VMP-X ELITE");
        title.setTextColor(Color.parseColor("#00FFFF")); 
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(title);

        Button minBtn = new Button(ctx);
        minBtn.setText("_"); minBtn.setTextColor(Color.WHITE); minBtn.setBackgroundColor(Color.TRANSPARENT);
        minBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { parentWindow.minimizeMenu(); }
        });
        header.addView(minBtn);

        Button hideBtn = new Button(ctx);
        hideBtn.setText("X"); hideBtn.setTextColor(Color.RED); hideBtn.setBackgroundColor(Color.TRANSPARENT);
        hideBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { parentWindow.hideUI(); } 
        });
        header.addView(hideBtn);

        addView(header); 
    }

    private void createActionSection(final Context ctx) {
        LinearLayout actionLayout = new LinearLayout(ctx);
        actionLayout.setOrientation(HORIZONTAL);
        actionLayout.setPadding(0, 0, 0, 15);
        actionLayout.setGravity(Gravity.CENTER);

        Button resetBtn = new Button(ctx);
        resetBtn.setText("RESET ENGINE");
        resetBtn.setTextColor(Color.YELLOW);
        resetBtn.setBackgroundColor(Color.parseColor("#1A1A1A"));
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for(int i=0; i<10; i++) {
                    if(eqSeekBars[i] != null) eqSeekBars[i].setProgress(20);
                    currentBands[i] = 0f;
                }
                Toast.makeText(ctx, "EQ Reset", Toast.LENGTH_SHORT).show();
            }
        });
        actionLayout.addView(resetBtn);
        scrollContent.addView(actionLayout); 
    }

    private void addSetting(Context ctx, final String name, final int bandIndex) {
        LinearLayout cardBlock = new LinearLayout(ctx);
        cardBlock.setOrientation(VERTICAL);
        cardBlock.setBackgroundColor(Color.parseColor("#121212")); 
        cardBlock.setPadding(15, 15, 15, 15);
        
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blockParams.setMargins(0, 0, 0, 20); 
        cardBlock.setLayoutParams(blockParams);

        final TextView tv = new TextView(ctx); 
        float initialGain = currentBands[bandIndex]; 
        tv.setText(name + " [" + (initialGain > 0 ? "+" : "") + (int)initialGain + " dB]");
        tv.setTextColor(Color.LTGRAY);
        tv.setTextSize(12);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(10, 0, 0, 10);
        cardBlock.addView(tv);

        final SeekBar sb = new SeekBar(ctx);
        sb.setMax(40); 
        sb.setProgress((int)initialGain + 20); 
        eqSeekBars[bandIndex] = sb; 
        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sb.setLayoutParams(sbParams);
        cardBlock.addView(sb);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnRow.setPadding(10, 5, 10, 0);

        Button minusBtn = new Button(ctx);
        minusBtn.setText("-"); minusBtn.setTextColor(Color.WHITE); minusBtn.setBackgroundColor(Color.parseColor("#222222"));
        
        View spacer = new View(ctx);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1.0f));

        Button plusBtn = new Button(ctx);
        plusBtn.setText("+"); plusBtn.setTextColor(Color.WHITE); plusBtn.setBackgroundColor(Color.parseColor("#222222"));

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float gainValue = progress - 20;
                tv.setText(name + " [" + (gainValue > 0 ? "+" : "") + (int)gainValue + " dB]");
                PremiumAudioService.updateBand(bandIndex, gainValue);
                currentBands[bandIndex] = gainValue;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        minusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int p = sb.getProgress();
                if (p > 0) sb.setProgress(p - 1);
            }
        });
        plusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int p = sb.getProgress();
                if (p < 40) sb.setProgress(p + 1);
            }
        });

        btnRow.addView(minusBtn);
        btnRow.addView(spacer);
        btnRow.addView(plusBtn);
        
        cardBlock.addView(btnRow); 
        scrollContent.addView(cardBlock); 
    }
}
