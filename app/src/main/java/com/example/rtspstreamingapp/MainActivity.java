package com.example.rtspstreamingapp;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;
    private EditText rtspUrl;
    private Button playButton, pipButton, recordButton;
    private LinearLayout buttonPanel;

    private static final int REQUEST_STORAGE_PERMISSION = 100;

    private boolean isRecording = false;
    private File recordedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        rtspUrl = findViewById(R.id.rtspUrl);
        playButton = findViewById(R.id.playButton);
        pipButton = findViewById(R.id.pipButton);
        recordButton = findViewById(R.id.recordButton); // Record button
        FrameLayout videoFrame = findViewById(R.id.videoLayout);
        buttonPanel = findViewById(R.id.buttonPanel);

        // Initialize VLCVideoLayout
        videoLayout = new VLCVideoLayout(this);
        videoFrame.addView(videoLayout);

        // Initialize LibVLC
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        libVLC = new LibVLC(this, options);

        // Initialize MediaPlayer
        mediaPlayer = new MediaPlayer(libVLC);

        // Play button logic
        playButton.setOnClickListener(v -> {
            String url = rtspUrl.getText().toString();
            if (!url.isEmpty()) {
                playStream(url);
            } else {
                Toast.makeText(this, "Please enter a valid RTSP URL", Toast.LENGTH_SHORT).show();
            }
        });

        // PiP button logic
        pipButton.setOnClickListener(v -> enterPictureInPictureMode());

        // Record button logic
        recordButton.setOnClickListener(v -> toggleRecording());
    }

    public void enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Define the aspect ratio of the PiP window
            Rational aspectRatio = new Rational(videoLayout.getWidth(), videoLayout.getHeight());

            // Set PictureInPictureParams
            PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
            pipBuilder.setAspectRatio(aspectRatio);

            // Enter PiP mode
            try {
                enterPictureInPictureMode(pipBuilder.build());
            } catch (Exception e) {
                Toast.makeText(this, "Failed to enter PiP mode", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "PiP mode is not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            // Hide UI components except video container
            rtspUrl.setVisibility(View.GONE);
            buttonPanel.setVisibility(View.GONE);
        } else {
            // Restore UI components when exiting PiP mode
            rtspUrl.setVisibility(View.VISIBLE);
            buttonPanel.setVisibility(View.VISIBLE);
        }
    }

    private void playStream(String url) {
        // Validate the URL
        if (!url.startsWith("rtsp://")) {
            Toast.makeText(this, "Invalid RTSP URL. It must start with rtsp://", Toast.LENGTH_SHORT).show();
            return;
        }

        // Stop the current media if playing
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }

        // Create a new Media object with the RTSP URL
        Media media = new Media(libVLC, Uri.parse(url));
        media.setHWDecoderEnabled(true, false); // Enable hardware acceleration
        media.addOption(":network-caching=150"); // Add network caching to reduce latency

        // Set the media to the MediaPlayer
        mediaPlayer.setMedia(media);
        mediaPlayer.attachViews(videoLayout, null, false, false);
        mediaPlayer.play();
    }

    private void toggleRecording() {
        if (isRecording) {
            // Stop recording
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            Toast.makeText(this, "Recording stopped. File saved: " + recordedFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            isRecording = false;
            recordButton.setText("Record");
        } else {
            // Start recording
            try {
                // Get the Downloads directory
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                recordedFile = new File(downloadsDir, "recorded_stream_" + System.currentTimeMillis() + ".mp4");

                // Create the Media object and set recording options
                Media media = new Media(libVLC, Uri.parse(rtspUrl.getText().toString()));
                media.addOption(":sout=#transcode{vcodec=h264,acodec=mp4a,vb=800,ab=128}:file{dst=" + recordedFile.getAbsolutePath() + "}");
                media.addOption(":no-sout-rtp-sap");
                media.addOption(":no-sout-standard-sap");
                media.addOption(":sout-keep");
                media.addOption(":network-caching=1000"); // Increase network caching
                media.setHWDecoderEnabled(false, false); // Disable hardware acceleration

                // Set the media to MediaPlayer and start recording
                mediaPlayer.setMedia(media);
                mediaPlayer.play();

                Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show();
                isRecording = true;
                recordButton.setText("Stop Recording");
            } catch (Exception e) {
                Toast.makeText(this, "Failed to start recording: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        if (libVLC != null) {
            libVLC.release();
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) { // Only required for Android 10 and below
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Storage permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}