package com.openxc.sinks;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.openxc.messages.VehicleMessage;
import com.openxc.messages.formatters.JsonFormatter;
import com.openxc.util.FileOpener;

import net.gotev.uploadservice.MultipartUploadRequest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Record raw vehicle messages to a file as JSON.
 *
 * This data sink is a simple passthrough that records every raw vehicle
 * message as it arrives to a file on the device. It uses a heuristic to
 * detect different "trips" in the vehicle, and splits the recorded trace by
 * trip.
 *
 * The heuristic is very simple: if we haven't received any new data in a while,
 * consider the previous trip to have ended. When activity resumes, start a new
 * trip.
 */
public class FileRecorderSink implements VehicleDataSink {
    private final static String TAG = "FileRecorderSink";
    private final static int INTER_TRIP_THRESHOLD_MINUTES = 5;
    private final static int FILE_THRESHOLD_MINUTES = 2;
    private final static String UPLOAD_URL = "http://doodle.isi.edu/upload/com.siliconribbon.obd_openxc";
    private static SimpleDateFormat sDateFormatter =
            new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);

    private FileOpener mFileOpener;
    private BufferedWriter mWriter;
    private Calendar mLastMessageReceived;
    private Calendar mLastFileOpened;
    private String mLastFileName;
    private Context mContext;
    private String mDirectory;

    public FileRecorderSink(FileOpener fileOpener) {
        mFileOpener = fileOpener;
    }

    public FileRecorderSink(FileOpener fileOpener, Context context, String directory){
        mContext = context;
        mFileOpener = fileOpener;
        mDirectory = directory;
    }
    @Override
    public synchronized void receive(VehicleMessage message)
            throws DataSinkException {
        if(mLastMessageReceived == null ||
                Calendar.getInstance().getTimeInMillis() - mLastMessageReceived.getTimeInMillis()
                        > INTER_TRIP_THRESHOLD_MINUTES * 60 * 1000 ||
                Calendar.getInstance().getTimeInMillis() - mLastFileOpened.getTimeInMillis()
                        > FILE_THRESHOLD_MINUTES * 60 * 1000) {
            Log.i(TAG, "Detected a new trip or splitting recorded trace file");
            try {
                if(mLastFileName != null && mContext != null){
                    //Zip and Upload
                    Log.d(TAG, "zip and upload");
                    File externalStoragePath = Environment.getExternalStorageDirectory();
//                    File file = new File(externalStoragePath.getAbsolutePath() + File.separator + mDirectory + File.separator + mLastFileName);
                    String mfilePath = externalStoragePath.getAbsolutePath() + File.separator + mDirectory + File.separator + mLastFileName;
                    uploadMultipart(mContext, mfilePath, mLastFileName);
                }
                mLastFileName = openTimestampedFile();
                mLastFileOpened = Calendar.getInstance();
            } catch(IOException e) {
                throw new DataSinkException(
                        "Unable to open file for recording", e);
            }
        }

        if(mWriter == null) {
            throw new DataSinkException(
                    "No valid writer - not recording trace line");
        }

        mLastMessageReceived = Calendar.getInstance();
        try {
            mWriter.write(JsonFormatter.serialize(message));
            mWriter.newLine();
        } catch(IOException e) {
            throw new DataSinkException("Unable to write message to file");
        }
    }

    @Override
    public synchronized void stop() {
        close();
        Log.i(TAG, "Shutting down");
    }

    public synchronized void flush() {
        if(mWriter != null) {
            try {
                mWriter.flush();
            } catch(IOException e) {
                Log.w(TAG, "Unable to flush writer", e);
            }
        }
    }

    private synchronized void close() {
        if(mWriter != null) {
            try {
                mWriter.close();
            } catch(IOException e) {
                Log.w(TAG, "Unable to close output file", e);
            }
            mWriter = null;
        }
    }

    private synchronized String openTimestampedFile() throws IOException {
        Calendar calendar = Calendar.getInstance();
        String filename = sDateFormatter.format(
                calendar.getTime()) + ".json";
        if(mWriter != null) {
            close();
        }
        mWriter = mFileOpener.openForWriting(filename);
        Log.i(TAG, "Opened trace file " + filename + " for writing");
        return filename;
    }

    private String uploadMultipart(final Context context, String file_path, String file_name) {
        try {
            String uploadId =
                    new MultipartUploadRequest(context, UPLOAD_URL)
                            .addFileToUpload(file_path, "file", file_name)
                            .setAutoDeleteFilesAfterSuccessfulUpload(true)
                            .setMaxRetries(1000000)
                            .startUpload();
            return uploadId;
        } catch (Exception exc) {
            Log.e("AndroidUploadService", exc.getMessage(), exc);
            return null;
        }
    }
}
