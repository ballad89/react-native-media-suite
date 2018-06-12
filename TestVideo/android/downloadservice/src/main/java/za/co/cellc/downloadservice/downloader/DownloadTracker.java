package za.co.cellc.downloadservice.downloader;

import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.ActionFile;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadManager.TaskState;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.ProgressiveDownloadHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.offline.TrackKey;
import com.google.android.exoplayer2.source.dash.offline.DashDownloadHelper;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloadHelper;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloadHelper;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks media that has been downloaded.
 *
 * <p>Tracked downloads are persisted using an {@link ActionFile}, however in a real application
 * it's expected that state will be stored directly in the application's media database, so that it
 * can be queried efficiently together with other information about the media.
 */
public class DownloadTracker implements DownloadManager.Listener {

    /** Listens for changes in the tracked downloads. */
    public interface Listener {

        /** Called when the tracked downloads changed. */
        void onDownloadsChanged();
    }

    private static final String TAG = "DownloadTracker";

    private final ReactApplicationContext context;
    private final DataSource.Factory dataSourceFactory;
    private final TrackNameProvider trackNameProvider;
    private final CopyOnWriteArraySet<Listener> listeners;
    private final HashMap<Uri, DownloadAction> trackedDownloadStates;
    private final ActionFile actionFile;
    private final Handler actionFileWriteHandler;

    public DownloadTracker(
            ReactApplicationContext context,
            DataSource.Factory dataSourceFactory,
            File actionFile,
            DownloadAction.Deserializer[] deserializers) {
        this.context = context;
        this.dataSourceFactory = dataSourceFactory;
        this.actionFile = new ActionFile(actionFile);
        trackNameProvider = new DefaultTrackNameProvider(context.getResources());
        listeners = new CopyOnWriteArraySet<>();
        trackedDownloadStates = new HashMap<>();
        HandlerThread actionFileWriteThread = new HandlerThread("DownloadTracker");
        actionFileWriteThread.start();
        actionFileWriteHandler = new Handler(actionFileWriteThread.getLooper());
        loadTrackedActions(deserializers);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isDownloaded(Uri uri) {
        return trackedDownloadStates.containsKey(uri);
    }

    @SuppressWarnings("unchecked")
    public <K> List<K> getOfflineStreamKeys(Uri uri) {
        if (!trackedDownloadStates.containsKey(uri)) {
            return Collections.emptyList();
        }
        DownloadAction action = trackedDownloadStates.get(uri);
        if (action instanceof SegmentDownloadAction) {
            return ((SegmentDownloadAction) action).keys;
        }
        return Collections.emptyList();
    }

    public DownloadAction getDownloadAction(String name, Uri uri, String extension ){
        DownloadHelper downloadHelper = getDownloadHelper(uri, extension);
        List<TrackKey> trackKeys = new ArrayList<>();
        DownloadAction downloadAction =
                downloadHelper.getDownloadAction(Util.getUtf8Bytes(name), trackKeys);
        return downloadAction;
    }

    public void toggleDownload( String name, Uri uri, String extension) {
        DownloadHelper downloadHelper = getDownloadHelper(uri, extension);
        if (isDownloaded(uri)) {
            Log.i(TAG, "Remove download");
            DownloadAction removeAction =
                    downloadHelper.getRemoveAction(Util.getUtf8Bytes(name));
            startServiceWithAction(removeAction);
        } else {
            Log.i(TAG, "Start download");
            List<TrackKey> trackKeys = new ArrayList<>();
            DownloadAction downloadAction =
                    downloadHelper.getDownloadAction(Util.getUtf8Bytes(name), trackKeys);
            startDownload(downloadAction);
        }
    }

    // DownloadManager.Listener

    @Override
    public void onInitialized(DownloadManager downloadManager) {
        // Do nothing.
    }

    @Override
    public void onTaskStateChanged(DownloadManager downloadManager, TaskState taskState) {
        DownloadAction action = taskState.action;
        Uri uri = action.uri;
        if ((action.isRemoveAction && taskState.state == TaskState.STATE_COMPLETED)
                || (!action.isRemoveAction && taskState.state == TaskState.STATE_FAILED)) {
            // A download has been removed, or has failed. Stop tracking it.
            if (trackedDownloadStates.remove(uri) != null) {
                handleTrackedDownloadStatesChanged();
            }
        }
    }

    @Override
    public void onIdle(DownloadManager downloadManager) {
        // Do nothing.
    }

    // Internal methods

    private void loadTrackedActions(DownloadAction.Deserializer[] deserializers) {
        try {
            DownloadAction[] allActions = actionFile.load(deserializers);
            for (DownloadAction action : allActions) {
                trackedDownloadStates.put(action.uri, action);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load tracked actions", e);
        }
    }

    private void handleTrackedDownloadStatesChanged() {
        for (Listener listener : listeners) {
            listener.onDownloadsChanged();
        }
        final DownloadAction[] actions = trackedDownloadStates.values().toArray(new DownloadAction[0]);
        actionFileWriteHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            actionFile.store(actions);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to store tracked actions", e);
                        }
                    }
                });
    }

    private void startDownload(DownloadAction action) {
        if (trackedDownloadStates.containsKey(action.uri)) {
            // This content is already being downloaded. Do nothing.
            return;
        }
        Log.i(TAG, "Started Download");
        trackedDownloadStates.put(action.uri, action);
        handleTrackedDownloadStatesChanged();
        startServiceWithAction(action);
    }

    private void startServiceWithAction(DownloadAction action) {
        DownloadService.startWithAction(context, DownloadService.class, action, false);
    }

    private DownloadHelper getDownloadHelper(Uri uri, String extension) {
        int type = Util.inferContentType(uri, extension);
        switch (type) {
            case C.TYPE_DASH:
                return new DashDownloadHelper(uri, dataSourceFactory);
            case C.TYPE_SS:
                return new SsDownloadHelper(uri, dataSourceFactory);
            case C.TYPE_HLS:
                return new HlsDownloadHelper(uri, dataSourceFactory);
            case C.TYPE_OTHER:
                return new ProgressiveDownloadHelper(uri);
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

}