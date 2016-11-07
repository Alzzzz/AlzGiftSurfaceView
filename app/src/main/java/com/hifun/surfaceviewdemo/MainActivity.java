package com.hifun.surfaceviewdemo;

import android.graphics.PixelFormat;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_toast).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, "弹出", Toast.LENGTH_SHORT).show();
            }
        });

        RecycleSurfaceView sfvTrack = (RecycleSurfaceView)findViewById(R.id.zenClockSurface1);
        sfvTrack.setZOrderOnTop(true);    // necessary
        SurfaceHolder sfhTrack = sfvTrack.getHolder();
        sfhTrack.setFormat(PixelFormat.TRANSLUCENT);

    }
}
