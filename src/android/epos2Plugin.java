package com.cordova.plugin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Base64;
import android.util.Log;

import com.epson.epos2.Epos2Exception;
import com.epson.epos2.discovery.Discovery;
import com.epson.epos2.discovery.DiscoveryListener;
import com.epson.epos2.discovery.FilterOption;
import com.epson.epos2.printer.Printer;
import com.epson.epos2.printer.PrinterStatusInfo;
import com.epson.epos2.printer.ReceiveListener;
import com.epson.eposprint.Print;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class epos2Plugin extends CordovaPlugin {
    private static final String TAG = "EPOS2Plugin";
    private CallbackContext callbackContext = null;
    private Printer printer = null;

    private final String ALIGN_LEFT = "[LEFT]";
    private final String ALIGN_CENTER = "[CENTER]";
    private final String ALIGN_RIGHT = "[RIGHT]";

    private final String TEXT_BOLD = "[BOLD]";
    private final String TEXT_UNDERLINE = "[UNDERLINE]";
    private final String TEXT_NORMAL = "[NORMAL]";

    private final String TEXT_SMALL = "[SMALL]";
    private final String TEXT_MEDIUM = "[MEDIUM]";

    private final String CUT_FEED = "[CUT]";

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) {
        this.callbackContext = callbackContext;

        try {
            if (action.equals("startDiscover")) {
                startDiscovery(callbackContext);
            } else if (action.equals("stopDiscover")) {
                stopDiscovery(callbackContext);
            } else if (action.equals("connectPrinter")) {
                String target = args.getString(0);
                connectPrinter(target, callbackContext);
            } else if (action.equals("disconnectPrinter")) {
                disconnectPrinter();
            } else if (action.equals("print")) {
                String[] lines = convertToArray(args, 0);
                printLines(lines, callbackContext);
            } else if (action.equals("printImage")) {
                String imageData = args.getString(0);
                printImage(imageData, callbackContext);
            } else if (action.equals("printReceipt")) {
                String image64 = args.getString(0);
                String[] lines = convertToArray(args.getJSONArray(1), 0);
                printReceipt(image64, lines, callbackContext);
            } else if (action.equals("checkStatus")) {
                checkStatus(callbackContext);
            }
        } catch (JSONException jse) {
            callbackContext.error("Command failed due to bad parameters.");
        }

        return true;
    }

    private void startDiscovery(final CallbackContext callbackContext) {
        FilterOption mFilterOption = new FilterOption();
        mFilterOption.setDeviceType(Discovery.TYPE_PRINTER);
        mFilterOption.setEpsonFilter(Discovery.FILTER_NAME);
        try {
            Discovery.start(webView.getContext(), mFilterOption, discoveryListener);
        } catch (Epos2Exception e) {
            Log.e(TAG, "Error discovering printer: " + e.getErrorStatus(), e);
            callbackContext.error("Error discovering printer: " + e.getErrorStatus());
        }
    }

    private void stopDiscovery(final CallbackContext callbackContext) {
        while (true) {
            try {
                Discovery.stop();
                PluginResult result = new PluginResult(Status.OK, true);
                callbackContext.sendPluginResult(result);
                break;
            } catch (Epos2Exception e) {
                if (e.getErrorStatus() != Epos2Exception.ERR_PROCESSING) {
                    PluginResult result = new PluginResult(Status.ERROR, false);
                    callbackContext.sendPluginResult(result);
                    return;
                }
            }
        }
    }

    private void connectPrinter(final String targetIP, final CallbackContext callbackContext) {
        Log.d(TAG, "Connecting Printer");

        try {
            printer = new Printer(Printer.TM_T88, Printer.MODEL_ANK, webView.getContext());
            printer.setReceiveEventListener(receiveListener);
        } catch (Epos2Exception e) {
            callbackContext.error("Error creating printer: " + e.getErrorStatus());
            Log.e(TAG, "Error creating printer: " + e.getErrorStatus(), e);
            return;
        }

        try {
            printer.connect("TCP:" + targetIP, Printer.PARAM_DEFAULT);
        } catch (Epos2Exception e) {
            callbackContext.error("Error connecting printer: " + e.getErrorStatus());
            Log.e(TAG, "Error connecting printer: " + e.getErrorStatus(), e);
            return;
        }

        try {
            printer.beginTransaction();
        } catch (Epos2Exception e) {
            callbackContext.error("Error beginning transaction");
            Log.e(TAG, "Error beginning transaction", e);
            return;
        }

        PluginResult result = new PluginResult(Status.OK, "Done connecting");
        callbackContext.sendPluginResult(result);
    }

    private void disconnectPrinter() {
        if (printer == null) {
            callbackContext.success("already disconnected");
            return;
        }

        try {
            printer.endTransaction();
        } catch (Epos2Exception e) {
            callbackContext.error("Error ending transaction: " + e.getErrorStatus());
            Log.e(TAG, "Error ending transaction: " + e.getErrorStatus(), e);
            return;
        }

        try {
            printer.disconnect();
        } catch (Epos2Exception e) {
            callbackContext.error("Error disconnecting printer: " + e.getErrorStatus());
            Log.e(TAG, "Error disconnecting printer: " + e.getErrorStatus(), e);
            return;
        }

        printer.clearCommandBuffer();
        printer.setReceiveEventListener(null);
        printer = null;

        callbackContext.success("disconnected");
    }

    private void printLines(final String[] lines, final CallbackContext mCallbackContext) {
        try {
            if (notReadyToPrint()) {
                mCallbackContext.error("Not ready to print");
                return;
            }

            for (String line : lines) {
                if ("\n".equals(line) || "".equals(line.trim())) {
                    printer.addFeedLine(1);
                } else if (line.equals(ALIGN_LEFT)) {
                    printer.addTextAlign(Printer.ALIGN_LEFT);
                } else if (line.equals(ALIGN_CENTER)) {
                    printer.addTextAlign(Printer.ALIGN_CENTER);
                } else if (line.equals(ALIGN_RIGHT)) {
                    printer.addTextAlign(Printer.ALIGN_RIGHT);
                } else if (line.equals(TEXT_BOLD)) {
                    printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.TRUE, Printer.PARAM_DEFAULT);
                } else if (line.equals(TEXT_UNDERLINE)) {
                    printer.addTextStyle(Printer.FALSE, Printer.TRUE, Printer.FALSE, Printer.PARAM_DEFAULT);
                } else if (line.equals(TEXT_NORMAL)) {
                    printer.addTextStyle(Printer.FALSE, Printer.FALSE, Printer.FALSE, Printer.PARAM_DEFAULT);
                } else if (line.equals(TEXT_SMALL)) {
                    printer.addTextSize(1, 1);
                } else if (line.equals(TEXT_MEDIUM)) {
                    printer.addTextSize(2, 2);
                } else if (line.equals(CUT_FEED)) {
                    printer.addCut(Printer.CUT_NO_FEED);
                } else {
                    printer.addText(line);
                    printer.addFeedLine(1);
                }

            }

            printer.addFeedLine(2);

            printer.addCut(Printer.CUT_FEED);

            printer.sendData(Printer.PARAM_DEFAULT);

            Thread.sleep(300);
            printer.clearCommandBuffer();
        } catch (Epos2Exception e) {
            mCallbackContext.error("Printing failed due to error: " + e.getErrorStatus());
        } catch (InterruptedException e) {
            mCallbackContext.error("Printing failed because the thread was interrupted.");
        }
    }

    private void printImage(final String imageBase64, final CallbackContext mCallbackContext) {
        try {
            if (notReadyToPrint()) {
                mCallbackContext.error("Not ready to print");
//
//                // Attempt to disconnect from printer
//                if (!rawDisconnect()) {
//                    mCallbackContext.error("Error disconnecting");
//                    Log.e(TAG, "Error disconnecting");
//                }
                return;
            }

            addImage(imageBase64, 512, 100);

            printer.sendData(Printer.PARAM_DEFAULT);

            // Wait for a little bit, then clear buffer to avoid double prints
            Thread.sleep(200);
            printer.clearCommandBuffer();

            mCallbackContext.success("Printing completed");
        } catch (Epos2Exception e) {
            mCallbackContext.error("Printing failed due to error: " + e.getErrorStatus());
        } catch (InterruptedException e) {
            mCallbackContext.error("Printing failed because the thread was interrupted.");
        }
    }

    private void printReceipt(final String imageBase64, final String[] lines, final CallbackContext mCallbackContext) {
        try {
            if (notReadyToPrint()) {
                mCallbackContext.error("Not ready to print");
                return;
            }

            // Add image if present
            addImage(imageBase64, 512, 100);

            // Print text along with rest of buffer
            printLines(lines, mCallbackContext);

            mCallbackContext.success("Printing complete");

        } catch (Epos2Exception e) {
            mCallbackContext.error("Printing failed due to error: " + e.getErrorStatus());
        }
    }

    private void addImage(final String imageBase64, int maxWidth, int maxHeight) throws Epos2Exception {
        if (imageBase64 != null && imageBase64.trim() != "") {
            Bitmap image = scale(convertBase64ToBitmap(imageBase64), maxWidth, maxHeight);
            printer.addImage(image, 0, 0, image.getWidth(), image.getHeight(), Printer.PARAM_DEFAULT,
                    Printer.MODE_MONO, Printer.HALFTONE_DITHER, 1.0,
                    Printer.PARAM_DEFAULT);
        }
    }

    private void checkStatus(final CallbackContext mCallbackContext) {
        try {
            if (printer == null) {
                mCallbackContext.error("Not connected to printer");
                return;
            }

            PrinterStatusInfo stat = printer.getStatus();

            if (stat == null) {
                mCallbackContext.error("Failed to get status");
                return;
            }

            JSONObject responseObj = new JSONObject();

            responseObj.put("connected", stat.getConnection() == Printer.TRUE ? "true" : "false");
            responseObj.put("online", stat.getOnline() == Printer.TRUE ? "true" : "false");
            responseObj.put("paper", getPaperState(stat.getPaper()));
            responseObj.put("cover", stat.getCoverOpen() == Printer.TRUE ? "Open" : "Closed");

            mCallbackContext.success(responseObj);
        } catch (JSONException exc) {
            mCallbackContext.error("Failed to build status response.");
            Log.e(TAG, "Failed to build status response", exc);
        }
    }

    /**
     * Triggers when a printer is found.
     */
    private DiscoveryListener discoveryListener = deviceInfo -> {
        JSONObject item = new JSONObject();

        try {
            item.put("deviceName", deviceInfo.getDeviceName());
            item.put("target", deviceInfo.getTarget());
            item.put("ipAddress", deviceInfo.getIpAddress());
            item.put("macAddress", deviceInfo.getMacAddress());
            item.put("deviceType", deviceInfo.getDeviceType());
            item.put("bdAddress", deviceInfo.getBdAddress());
        } catch (JSONException e) {
            callbackContext.error("Error building device info");
        }

        callbackContext.success(item);
    };

    /**
     * Triggers when the printer acknowledges having received commands.
     */
    private ReceiveListener receiveListener = (printer, code, status, printJobId) -> {
        Log.d(TAG, "print success. status: " + status.getErrorStatus());

        // Disconnect after printer receives data????? why???
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    disconnectPrinter();
//                }
//            }).start();
    };

    private boolean notReadyToPrint() {
        if (printer == null) {
            return true;
        }

        PrinterStatusInfo status = printer.getStatus();
        if (status == null) {
            return true;
        }

        if (status.getConnection() == Printer.FALSE) {
            return true; // Not connected
        } else if (status.getOnline() == Printer.FALSE) {
            return true; // Not online (???)
        }

        return false;
    }

    private Bitmap convertBase64ToBitmap(String b64) {
        byte[] imageAsBytes = Base64.decode(b64.getBytes(), Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length);
    }

    private Bitmap scale(Bitmap bitmap, int maxWidth, int maxHeight) {
        // Determine the constrained dimension, which determines both dimensions.
        int width;
        int height;
        float widthRatio = (float) bitmap.getWidth() / maxWidth;
        float heightRatio = (float) bitmap.getHeight() / maxHeight;
        // Width constrained.
        if (widthRatio >= heightRatio) {
            width = maxWidth;
            height = (int) (((float) width / bitmap.getWidth()) * bitmap.getHeight());
        }
        // Height constrained.
        else {
            height = maxHeight;
            width = (int) (((float) height / bitmap.getHeight()) * bitmap.getWidth());
        }
        Bitmap scaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        float ratioX = (float) width / bitmap.getWidth();
        float ratioY = (float) height / bitmap.getHeight();
        float middleX = width / 2.0f;
        float middleY = height / 2.0f;
        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        canvas.drawBitmap(bitmap, middleX - bitmap.getWidth() / 2, middleY - bitmap.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));
        return scaledBitmap;
    }

    private String getPaperState(int code) {
        switch (code) {
            case Printer.PAPER_OK:
                return "OK";
            case Printer.PAPER_NEAR_END:
                return "Low";
            case Printer.PAPER_EMPTY:
                return "Empty";
        }
        return "Unknown";
    }

    private boolean rawDisconnect() {
        try {
            if (printer == null) {
                return true;
            }
            printer.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String[] convertToArray(final JSONArray args, int startIndex) throws JSONException {
        if (startIndex > args.length()) {
            startIndex = 0;
        }
        String[] arr = new String[args.length()];
        for (int index = startIndex; index < args.length(); index++) {
            arr[index] = args.getString(index).trim();
        }
        return arr;
    }

}
