package com.example.androidautomedia;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.androidautomedia.shared.MyMusicService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 123; // Choose any unique integer value
    private static final String TAG = "MainActivity";
    private static final String MY_MEDIA_ROOT_ID = "media_root_id";
    private TextView currentSongTextView;
    private Button playPauseButton;
    private SeekBar seekBar;
    private MediaBrowserCompat mediaBrowser;

    private ArrayList<String> songTitles = new ArrayList<>();
    private ArrayList<String> mediaIds = new ArrayList<>();
    MediaControllerCompat mediaController;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    currentSongTextView = findViewById(R.id.text_current_song);
    playPauseButton = findViewById(R.id.button_play_pause);
    seekBar = findViewById(R.id.seek_bar);
    // Check if READ_EXTERNAL_STORAGE permission is granted
    if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

        // Permission is not granted, request the permission from the user
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
    } else {
        // Permission is already granted
        Log.d(TAG, "READ_EXTERNAL_STORAGE permission already granted");
        initializeMediaBrowser();
    }
}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {// If request is cancelled, the result arrays are empty
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission granted");
                initializeMediaBrowser();
                savePermissionStatus(true);
            } else {
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission denied");
                savePermissionStatus(false);
            }
        }
    }
    private final BroadcastReceiver durationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("song_duration_update".equals(intent.getAction())) {
                int duration = intent.getIntExtra("duration", 0);
                seekBar.setMax(duration);
            }
        }
    };
    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(durationReceiver, new IntentFilter("song_duration_update"));
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (mediaBrowser.isConnected()) {
            mediaBrowser.disconnect();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(durationReceiver);
    }


    private void savePermissionStatus(boolean status) {
        SharedPreferences sharedPreferences = getSharedPreferences("permissions", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("storage_permission", status);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaBrowser != null) {
            mediaBrowser.disconnect();
        }
    }

    private void initializeMediaBrowser() {
        mediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, MyMusicService.class),
                connectionCallback,
                null);
        mediaBrowser.connect();
    }

    private final MediaBrowserCompat.ConnectionCallback connectionCallback = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            mediaBrowser.subscribe(MY_MEDIA_ROOT_ID, subscriptionCallback);
            MediaSessionCompat.Token token = mediaBrowser.getSessionToken();
            mediaController = new MediaControllerCompat(MainActivity.this, token);
            MediaControllerCompat.setMediaController(MainActivity.this, mediaController);
            mediaController.registerCallback(mediaControllerCallback);
        }
    };

    private final MediaBrowserCompat.SubscriptionCallback subscriptionCallback = new MediaBrowserCompat.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            updateUI(children);
        }
    };
    private final MediaControllerCompat.Callback mediaControllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
            if (state != null) {
                if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    updateSeekBarRunnable.run();
                    playPauseButton.setText(R.string.pause);
                } else {
                    seekBar.removeCallbacks(updateSeekBarRunnable);
                    playPauseButton.setText(R.string.play);
                }
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
            String title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
            currentSongTextView.setText(title);
        }

    };

    private final Runnable updateSeekBarRunnable = new Runnable() {
        @Override
        public void run() {
            MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MainActivity.this);
            if (mediaController != null && seekBar != null) {
                PlaybackStateCompat playbackState = mediaController.getPlaybackState();
                if (playbackState != null && playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                    long currentPosition = playbackState.getPosition();
                    seekBar.setProgress((int)currentPosition);
                }
            }
            assert seekBar != null;
            seekBar.postDelayed(this, 1000); // Update seek bar every second
        }
    };
    private void updateUI(List<MediaBrowserCompat.MediaItem> mediaItems) {
        ListView listView = findViewById(R.id.list_view);
        for (MediaBrowserCompat.MediaItem mediaItem : mediaItems) {
            String title = Objects.requireNonNull(mediaItem.getDescription().getTitle()).toString();
            String mediaId = mediaItem.getMediaId(); 
            songTitles.add(title);
            mediaIds.add(mediaId);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, songTitles);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String mediaId = mediaIds.get(position);
            playSong(mediaId);
        });
        Button previousButton = findViewById(R.id.button_previous);
        previousButton.setOnClickListener((id)-> skipToPrevious());
        Button nextButton = findViewById(R.id.button_next);
        nextButton.setOnClickListener((id)->skipToNext());
        Button playPauseButton = findViewById(R.id.button_play_pause);
        playPauseButton.setOnClickListener((id)->{
            MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MainActivity.this);
            if (mediaController != null) {
                if(mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING){
                    pause();
                }else if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PAUSED){
                    play();
                }
            }else{
                Log.d("ELSE","mediaController is NULL");
            }
        });
        TextView txtMarquee = (TextView) findViewById(R.id.text_current_song);
        txtMarquee.setSelected(true);
        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MainActivity.this);
        if (mediaController != null) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){
                    Log.d("SEEK TO", String.valueOf(progress));
                    mediaController.getTransportControls().seekTo((int)progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
                    mediaController.getTransportControls().pause();
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PAUSED) {
                    mediaController.getTransportControls().play();
                }
            }
        });}

    }
    private void playSong(String mediaId) {
        if (mediaController != null) {
            mediaController.getTransportControls().playFromMediaId(mediaId, null);
        }
    }private void play() {
        if (mediaController != null) {
            mediaController.getTransportControls().play();
        }
    }private void pause() {
        if (mediaController != null) {
            mediaController.getTransportControls().pause();
        }
    }private void skipToNext() {
        if (mediaController != null) {
            mediaController.getTransportControls().skipToNext();
        }
    }private void skipToPrevious() {
        if (mediaController != null) {
            mediaController.getTransportControls().skipToPrevious();
        }
    }

}