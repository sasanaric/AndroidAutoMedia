package com.example.androidautomedia;

import android.content.ComponentName;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import com.example.androidautomedia.shared.MyMusicService;

import java.util.ArrayList;
import java.util.List;

public class AndroidMediaScreen extends Screen {

    private List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
    private MediaBrowserCompat mediaBrowser;
    private MediaSessionCompat.Token mediaSessionToken;
    private MediaControllerCompat mediaController;

    public AndroidMediaScreen(CarContext carContext) {
        super(carContext);
        connectToMediaBrowserService();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return createTemplateWithMediaItems(mediaItems);
    }

    // Method to connect to the MediaBrowserServiceCompat
    private void connectToMediaBrowserService() {
        mediaBrowser = new MediaBrowserCompat(this.getCarContext(),
                new ComponentName(this.getCarContext(), MyMusicService.class),
                connectionCallback,
                null);
        mediaBrowser.connect();
    }

    // Connection callback for the MediaBrowserServiceCompat
    private MediaBrowserCompat.ConnectionCallback connectionCallback = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            // Get the root media ID
            String rootMediaId = mediaBrowser.getRoot();
            // Subscribe to the media ID to receive media items
            mediaBrowser.subscribe(rootMediaId, subscriptionCallback);
            // Get the MediaSession token
            mediaSessionToken = mediaBrowser.getSessionToken();
            if (mediaController == null) {
                mediaController = new MediaControllerCompat(getCarContext(), mediaSessionToken);
            }
        }
    };
    // Subscription callback for the MediaBrowserServiceCompat
    private MediaBrowserCompat.SubscriptionCallback subscriptionCallback = new MediaBrowserCompat.SubscriptionCallback() {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
            mediaItems.clear();
            mediaItems.addAll(children);
        }
    };

    private Template createTemplateWithMediaItems(List<MediaBrowserCompat.MediaItem> mediaItemsList){
        Pane.Builder paneBuilder = new Pane.Builder();
        for (MediaBrowserCompat.MediaItem mediaItem : mediaItemsList) {
            CharSequence title = mediaItem.getDescription().getTitle();
            if (title != null) {
                Row row = new Row.Builder()
                        .setTitle(title.toString())
                        .setOnClickListener(() -> {
                            if (mediaItem.isPlayable()) {
                                playSong(mediaItem.getMediaId());
                            }
                        })
                        .build();
                paneBuilder.addRow(row);
            }
        }
        return new PaneTemplate.Builder(paneBuilder.build())
                .setTitle("Song List")
                .setHeaderAction(Action.APP_ICON)
                .build();
    }
    private void playSong(String mediaId) {
            if (mediaController != null) {
                mediaController.getTransportControls().playFromMediaId(mediaId, null);
            }
    }
}

