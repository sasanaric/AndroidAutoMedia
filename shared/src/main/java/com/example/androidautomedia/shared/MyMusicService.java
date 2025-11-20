package com.example.androidautomedia.shared;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

//import android.support.v4.media.MediaBrowserCompat.MediaItem;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.MediaBrowserServiceCompat;

import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 * <p>
 * To implement a MediaBrowserService, you need to:
 *
 * <ul>
 *
 * <li> Extend {@link MediaBrowserServiceCompat}, implementing the media browsing
 *      related methods {@link MediaBrowserServiceCompat#onGetRoot} and
 *      {@link MediaBrowserServiceCompat#onLoadChildren};
 * <li> In onCreate, start a new {@link MediaSessionCompat} and notify its parent
 *      with the session"s token {@link MediaBrowserServiceCompat#setSessionToken};
 *
 * <li> Set a callback on the {@link MediaSessionCompat#setCallback(MediaSessionCompat.Callback)}.
 *      The callback will receive all the user"s actions, like play, pause, etc;
 *
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 *      {@link android.media.MediaPlayer})
 *
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 *      {@link MediaSessionCompat#setPlaybackState(android.support.v4.media.session.PlaybackStateCompat)}
 *      {@link MediaSessionCompat#setMetadata(android.support.v4.media.MediaMetadataCompat)} and
 *      {@link MediaSessionCompat#setQueue(java.util.List)})
 *
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 *      android.media.browse.MediaBrowserService
 *
 * </ul>
 * <p>
 * To make your app compatible with Android Auto, you also need to:
 *
 * <ul>
 *
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 *      with a &lt;automotiveApp&gt; root element. For a media app, this must include
 *      an &lt;uses name="media"/&gt; element as a child.
 *      For example, in AndroidManifest.xml:
 *          &lt;meta-data android:name="com.google.android.gms.car.application"
 *              android:resource="@xml/automotive_app_desc"/&gt;
 *      And in res/values/automotive_app_desc.xml:
 *          &lt;automotiveApp&gt;
 *              &lt;uses name="media"/&gt;
 *          &lt;/automotiveApp&gt;
 *
 * </ul>
 */
public class MyMusicService extends MediaBrowserServiceCompat implements AudioManager.OnAudioFocusChangeListener{

    private static final String MY_MEDIA_ROOT_ID = "media_root_id";
    private static final String TAG = "MyMusicService";
    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private MediaSessionCallback mediaSessionCallback;
    private List<MediaItem> allMediaItems = new ArrayList<>();
    private List<MediaItem> playlistMediaItems = new ArrayList<>();
    private List<MediaItem> queueMediaItems = new ArrayList<>();
    private int currentMediaItemPosition = -1;
    private AudioManager audioManager;
    private AudioAttributes playbackAttributes;
    private AudioFocusRequest focusRequest;



    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        // Creating audio focus request to get access of the audio for playing a song in a car
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build();
        mediaPlayer = new MediaPlayer();
        mediaSession = new MediaSessionCompat(getBaseContext() // getBaseContext() --> this
                , "MyMusicService");

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SET_REPEAT_MODE |
                                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                );
        mediaSession.setPlaybackState(stateBuilder.build());
        // MySessionCallback() has methods that handle callbacks from a media controller
        mediaSessionCallback = new MediaSessionCallback(mediaPlayer);
        mediaSession.setCallback(mediaSessionCallback);
        setSessionToken(mediaSession.getSessionToken());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaSession.release();
        mediaPlayer.release();
    }

    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName,
                                 int clientUid,
                                 Bundle rootHints) {
        return new BrowserRoot(MY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        // Loading all songs from phone
        if (TextUtils.equals(parentId,MY_MEDIA_ROOT_ID)) {
            List<MediaItem> songs = loadSongsFromDevice();
            for (MediaItem song : songs) {
                mediaItems.add(convertToMediaBrowserMediaItem(song));
            }
        } else {
            // Handle loading children based on the parent ID
            // Use this for playlists
        }

        result.sendResult(mediaItems);
    }
    @SuppressLint("Range")
    private List<MediaItem> loadSongsFromDevice() {
        if (allMediaItems.isEmpty()) {
            ContentResolver contentResolver = getContentResolver();
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_ID
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            try (Cursor cursor = contentResolver.query(uri, projection, selection, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        long id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
                        String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                        String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                        long albumId = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
                        Uri artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId);

                        MediaItem mediaItem = new MediaItem.Builder()
                                .setMediaId(String.valueOf(id))
                                .setUri(Uri.withAppendedPath(uri, String.valueOf(id)))
                                .setMimeType(MimeTypes.AUDIO_MPEG)
                                .setMediaMetadata(new MediaMetadata.Builder()
                                        .setTitle(title)
                                        .setArtist(artist)
                                        .setArtworkUri(artworkUri)
                                        .build())
                                .build();

                        allMediaItems.add(mediaItem);
                    } while (cursor.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading songs from device: " + e.getMessage());
            }
        }
        allMediaItems.sort((item1, item2) -> {
            String title1 = (String) Objects.requireNonNull(item1.mediaMetadata.title);
            String title2 = (String) Objects.requireNonNull(item2.mediaMetadata.title);
            return title1.compareToIgnoreCase(title2);
        });
        return allMediaItems;
    }

    private MediaBrowserCompat.MediaItem convertToMediaBrowserMediaItem(MediaItem mediaItem) {
        assert mediaItem.mediaMetadata.title != null;
        assert mediaItem.mediaMetadata.artist != null;
        assert mediaItem.localConfiguration != null;

        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(mediaItem.mediaId)
                .setTitle(mediaItem.mediaMetadata.title.toString())
                .setSubtitle(mediaItem.mediaMetadata.artist.toString())
                .setMediaUri(mediaItem.localConfiguration.uri)
                .setIconUri(mediaItem.mediaMetadata.artworkUri)
                .build();

        return new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
//            case AudioManager.AUDIOFOCUS_GAIN:
////                if (!mediaPlayer.isPlaying()) {
////                    mediaPlayer.start();
////                }
//                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                mediaPlayer.stop();
                mediaPlayer.reset();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
                break;
        }
    }

    private final class MediaSessionCallback extends MediaSessionCompat.Callback {
        private final MediaPlayer mediaPlayer;

        public MediaSessionCallback(MediaPlayer mediaPlayer) {
            this.mediaPlayer = mediaPlayer;
            mediaPlayer.setOnCompletionListener(mediaPlayer1 -> {
                Log.d("onCompletion()","NEXT");
                onSkipToNext();
            });
        }
        @Override
        public void onPlay() {
            Log.d("onPlay()","CALLED");
            if(!mediaPlayer.isPlaying()){
                mediaPlayer.start();
                mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1.0f)
                        .setActions(
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SEEK_TO |
                                PlaybackStateCompat.ACTION_SET_REPEAT_MODE |
                                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                        )
                        .build());
                mediaSession.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
                mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE);
                Intent intent = new Intent("song_duration_update");
                intent.putExtra("duration", getSongDuration());
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
            }
        }


        @Override
        public void onSkipToQueueItem(long queueId) {

        }

        @Override
        public void onSeekTo(long position) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo((int) position);
                mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(mediaPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                                mediaPlayer.getCurrentPosition(), mediaPlayer.isPlaying() ? 1.0f : 0.0f)
                        .setActions(
                                PlaybackStateCompat.ACTION_PAUSE |
                                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                        PlaybackStateCompat.ACTION_SEEK_TO |
                                        PlaybackStateCompat.ACTION_SET_REPEAT_MODE |
                                        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                        )
                        .build());
            }
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            super.onAddQueueItem(description);
        }


        @Override
        public void onSetRepeatMode(int repeatMode) {
            super.onSetRepeatMode(repeatMode);
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            super.onSetShuffleMode(shuffleMode);
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d("onPlayFromMediaId()", mediaId);
            MediaItem selectedMediaItem = getMediaFromMediaId(mediaId);
            assert selectedMediaItem != null;
            Log.d("MEDIAITEMURI",String.valueOf(selectedMediaItem.mediaMetadata.artworkUri));
            assert Objects.requireNonNull(selectedMediaItem).localConfiguration != null;
            assert selectedMediaItem.localConfiguration != null;
            Uri mediaUri = selectedMediaItem.localConfiguration.uri;
            int focusResult = audioManager.requestAudioFocus(focusRequest);
            if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.d("FOCUS REQUEST","GRANTED");
                try {
                    mediaPlayer.reset();
                    mediaPlayer.setDataSource(getApplicationContext(), mediaUri);
                    mediaPlayer.prepare();
                    setMetadata(selectedMediaItem);
                    onPlay();
                } catch (IOException e) {
                    Log.e(TAG, "Error playing media: " + e.getMessage());
                }
            } else {
                Log.d("FOCUS REQUEST","NOT GRANTED");
            }
        }


        @Override
        public void onPause() {
            if(mediaPlayer.isPlaying()){
                mediaPlayer.pause();
                mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PAUSED, mediaPlayer.getCurrentPosition(), 1.0f)
                        .setActions(
                                PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SEEK_TO
                        )
                        .build());
            }
        }

        @Override
        public void onStop() {
            if(mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0)
                        .build());
            }
        }


        public int getSongDuration() {
            if (mediaPlayer != null) {
                return mediaPlayer.getDuration();
            }
            return 0;
        }


        @Override
        public void onSkipToNext() {
            Log.d("onSkipToNext()","NEXT");
            currentMediaItemPosition++;
            if(currentMediaItemPosition == allMediaItems.size()){
                currentMediaItemPosition = 0;
            }
            onPlayFromMediaId(allMediaItems.get(currentMediaItemPosition).mediaId,null);
        }

        @Override
        public void onSkipToPrevious() {
            Log.d("onSkipToPrevious()","PREVIOUS");
            currentMediaItemPosition--;
            if(currentMediaItemPosition < 0){
                currentMediaItemPosition = allMediaItems.size() - 1;
            }
            onPlayFromMediaId(allMediaItems.get(currentMediaItemPosition).mediaId,null);
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
        }
        private MediaItem getMediaFromMediaId(String mediaId) {
            currentMediaItemPosition = -1;
                for (MediaItem mediaItem : allMediaItems) {
                    currentMediaItemPosition++;
                    if (mediaItem.mediaId.equals(mediaId)) {
                        return mediaItem;
                    }
                }
            currentMediaItemPosition = -1;
            return null;
        }
        private void setMetadata(MediaItem mediaItem) {
            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, (String) mediaItem.mediaMetadata.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, (String) mediaItem.mediaMetadata.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, (String) mediaItem.mediaMetadata.albumArtist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, String.valueOf(mediaItem.mediaMetadata.artworkUri))
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, String.valueOf(mediaItem.mediaMetadata.artworkUri))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getSongDuration())
                    .build());
        }

    }
}