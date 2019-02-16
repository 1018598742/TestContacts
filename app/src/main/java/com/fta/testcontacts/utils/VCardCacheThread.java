package com.fta.testcontacts.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;

import com.fta.testcontacts.MainActivity;
import com.fta.testcontacts.R;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class VCardCacheThread extends Thread implements DialogInterface.OnCancelListener {
    private static final String LOG_TAG = "VCardCacheThread";
    private static final String TAG = LOG_TAG;

    private boolean mCanceled;
    private PowerManager.WakeLock mWakeLock;
    private VCardParser mVCardParser;
    private final Uri[] mSourceUris;  // Given from a caller.
    private final String[] mSourceDisplayNames; // Display names for each Uri in mSourceUris.
    private final byte[] mSource;
    private final String mDisplayName;

    final static int VCARD_VERSION_V21 = 1;
    final static int VCARD_VERSION_V30 = 2;

    private Context mContext;

    private MainActivity.ImportRequestConnection mConnection;

    public VCardCacheThread(MainActivity.ImportRequestConnection connection, Context context, final Uri[] sourceUris, String[] sourceDisplayNames) {
        mSourceUris = sourceUris;
        mSourceDisplayNames = sourceDisplayNames;
        mSource = null;
        mContext = context;
        mConnection = connection;
//        final Context context = ImportVCardActivity.this;
        final PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK |
                        PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        mDisplayName = null;
    }

    @Override
    public void finalize() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            Log.w(LOG_TAG, "WakeLock is being held.");
            mWakeLock.release();
        }
    }

    @Override
    public void run() {
        Log.i(LOG_TAG, "vCard cache thread starts running.");
//        if (mConnection == null) {
//            throw new NullPointerException("vCard cache thread must be launched "
//                    + "after a service connection is established");
//        }

        mWakeLock.acquire();
        try {
            if (mCanceled == true) {
                Log.i(LOG_TAG, "vCard cache operation is canceled.");
                return;
            }

//            final Context context = ImportVCardActivity.this;
            // Uris given from caller applications may not be opened twice: consider when
            // it is not from local storage (e.g. "file:///...") but from some special
            // provider (e.g. "content://...").
            // Thus we have to once copy the content of Uri into local storage, and read
            // it after it.
            //
            // We may be able to read content of each vCard file during copying them
            // to local storage, but currently vCard code does not allow us to do so.
            int cache_index = 0;
            ArrayList<ImportRequest> requests = new ArrayList<ImportRequest>();
            if (mSource != null) {
                try {
                    requests.add(constructImportRequest(mSource, null, mDisplayName));
                } catch (VCardException e) {
                    FeedbackHelper.sendFeedback(mContext, LOG_TAG,
                            "Failed to cache vcard", e);
                    Log.i(TAG, "VCardCacheThread-run: 失败对话框");
//                    showFailureNotification(R.string.fail_reason_not_supported);
                    return;
                }
            } else {
                int i = 0;
                for (Uri sourceUri : mSourceUris) {
                    if (mCanceled) {
                        Log.i(LOG_TAG, "vCard cache operation is canceled.");
                        break;
                    }

                    String sourceDisplayName = mSourceDisplayNames[i++];

                    final ImportRequest request;
                    try {
                        request = constructImportRequest(null, sourceUri, sourceDisplayName);
                    } catch (VCardException e) {
                        FeedbackHelper.sendFeedback(mContext, LOG_TAG,
                                "Failed to cache vcard", e);
//                        showFailureNotification(R.string.fail_reason_not_supported);
                        return;
                    } catch (IOException e) {
                        FeedbackHelper.sendFeedback(mContext, LOG_TAG,
                                "Failed to cache vcard", e);
//                        showFailureNotification(R.string.fail_reason_io_error);
                        return;
                    }
                    if (mCanceled) {
                        Log.i(LOG_TAG, "vCard cache operation is canceled.");
                        return;
                    }
                    requests.add(request);
                }
            }
            if (!requests.isEmpty()) {
                mConnection.sendImportRequest(requests);
                Log.i(TAG, "VCardCacheThread-run: requests is not null");
                for (ImportRequest importRequest : requests) {
                    Log.i(TAG, "VCardCacheThread-run: " + importRequest.toString());
                }
            } else {
                Log.w(LOG_TAG, "Empty import requests. Ignore it.");
            }
        } catch (OutOfMemoryError e) {
            FeedbackHelper.sendFeedback(mContext, LOG_TAG,
                    "OutOfMemoryError occured during caching vCard", e);
            System.gc();
//            runOnUiThread(new ImportVCardActivity.DialogDisplayer(
//                    getString(R.string.fail_reason_low_memory_during_import)));
        } catch (IOException e) {
            FeedbackHelper.sendFeedback(mContext, LOG_TAG,
                    "IOException during caching vCard", e);
//            runOnUiThread(new ImportVCardActivity.DialogDisplayer(
//                    getString(R.string.fail_reason_io_error)));
        } finally {
            Log.i(LOG_TAG, "Finished caching vCard.");
            mWakeLock.release();
            try {
//                unbindService(mConnection);
            } catch (IllegalArgumentException e) {
                FeedbackHelper.sendFeedback(mContext, LOG_TAG,
                        "Cannot unbind service connection", e);
            }
//            mProgressDialogForCachingVCard.dismiss();
//            mProgressDialogForCachingVCard = null;
//            finish();
        }
    }

    /**
     * Reads localDataUri (possibly multiple times) and constructs {@link ImportRequest} from
     * its content.
     *
     * @arg localDataUri Uri actually used for the import. Should be stored in
     * app local storage, as we cannot guarantee other types of Uris can be read
     * multiple times. This variable populates {@link ImportRequest#uri}.
     * @arg displayName Used for displaying information to the user. This variable populates
     * {@link ImportRequest#displayName}.
     */
    private ImportRequest constructImportRequest(final byte[] data,
                                                 final Uri localDataUri, final String displayName)
            throws IOException, VCardException {
        final ContentResolver resolver = mContext.getContentResolver();
        VCardEntryCounter counter = null;
        VCardSourceDetector detector = null;
        int vcardVersion = VCARD_VERSION_V21;
        try {
            boolean shouldUseV30 = false;
            InputStream is;
            if (data != null) {
                is = new ByteArrayInputStream(data);
            } else {
                is = resolver.openInputStream(localDataUri);
            }
            mVCardParser = new VCardParser_V21();
            try {
                counter = new VCardEntryCounter();
                detector = new VCardSourceDetector();
                mVCardParser.addInterpreter(counter);
                mVCardParser.addInterpreter(detector);
                mVCardParser.parse(is);
            } catch (VCardVersionException e1) {
                try {
                    is.close();
                } catch (IOException e) {
                }

                shouldUseV30 = true;
                if (data != null) {
                    is = new ByteArrayInputStream(data);
                } else {
                    is = resolver.openInputStream(localDataUri);
                }
                mVCardParser = new VCardParser_V30();
                try {
                    counter = new VCardEntryCounter();
                    detector = new VCardSourceDetector();
                    mVCardParser.addInterpreter(counter);
                    mVCardParser.addInterpreter(detector);
                    mVCardParser.parse(is);
                } catch (VCardVersionException e2) {
                    throw new VCardException("vCard with unspported version.");
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }

            vcardVersion = shouldUseV30 ? VCARD_VERSION_V30 : VCARD_VERSION_V21;
        } catch (VCardNestedException e) {
            Log.w(LOG_TAG, "Nested Exception is found (it may be false-positive).");
            // Go through without throwing the Exception, as we may be able to detect the
            // version before it
        }
        return new ImportRequest(null,
                data, localDataUri, displayName,
                detector.getEstimatedType(),
                detector.getEstimatedCharset(),
                vcardVersion, counter.getCount());
    }

    public Uri[] getSourceUris() {
        return mSourceUris;
    }

    public void cancel() {
        mCanceled = true;
        if (mVCardParser != null) {
            mVCardParser.cancel();
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        Log.i(LOG_TAG, "Cancel request has come. Abort caching vCard.");
        cancel();
    }
}
