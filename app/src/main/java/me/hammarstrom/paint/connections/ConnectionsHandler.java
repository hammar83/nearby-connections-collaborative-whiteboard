package me.hammarstrom.paint.connections;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AppIdentifier;
import com.google.android.gms.nearby.connection.AppMetadata;
import com.google.android.gms.nearby.connection.Connections;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.hammarstrom.paint.HostListDialog;
import me.hammarstrom.paint.R;

/**
 * Created by Fredrik Hammarström on 12/10/15.
 */
public class ConnectionsHandler implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        Connections.ConnectionRequestListener,
        Connections.MessageListener,
        Connections.EndpointDiscoveryListener {

    private final String TAG = ConnectionsHandler.this.getClass().getName();

    /**
     * Interface to be implemented in the calling activity
     */
    public interface OnRemoteDrawingReceivedListener {

        /**
         * Method called when a new message has arrived
         * @param coords A list of the coordinates of the path to draw
         */
        void onRemoteDrawingReceived(List<String> coords);
    }

    public GoogleApiClient googleApiClient;
    private AlertDialog mConnectionRequestDialog;
    private HostListDialog mMyListDialog;
    private Context context;
    private OnRemoteDrawingReceivedListener mCallback;

    /** The endpoint ID of the connected peer, used for messaging **/
    private String mOtherEndpointId;

    /**
     * Timeout for advertising and discovery in millis.
     * If 0L, advertising and discovery runs indefinitely.
     */
    private static final long TIMEOUT_ADVERTISE = 0L;
    private static final long TIMEOUT_DISCOVER = 0L;

    public ConnectionsHandler(Context context, OnRemoteDrawingReceivedListener callback) {
        this.context = context;
        mCallback = callback;
        googleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Nearby.CONNECTIONS_API)
                .build();
    }

    public void connect() {
        googleApiClient.connect();
    }

    public void disconnect() {
        if(googleApiClient != null) {
            googleApiClient.disconnect();
        }
    }

    /**
     * Begin advertising for Nearby Connections, if possible.
     */
    public void startAdvertising() {
        if (!isConnectedToNetwork()) {
            Toast.makeText(context, "Not connected to WiFi", Toast.LENGTH_LONG).show();
            return;
        }

        // Advertising with an AppIdentifer lets other devices on the network discover
        // this application and prompt the user to install the application.
        List<AppIdentifier> appIdentifierList = new ArrayList<>();
        appIdentifierList.add(new AppIdentifier(context.getPackageName()));
        AppMetadata appMetadata = new AppMetadata(appIdentifierList);

        // Advertise for Nearby Connections. This will broadcast the service id defined in
        // AndroidManifest.xml. By passing 'null' for the name, the Nearby Connections API
        // will construct a default name based on device model such as 'LGE Nexus 5'.
        String name = null;
        Nearby.Connections.startAdvertising(googleApiClient, name, appMetadata, TIMEOUT_ADVERTISE, this).setResultCallback(new ResultCallback<Connections.StartAdvertisingResult>() {
            @Override
            public void onResult(Connections.StartAdvertisingResult result) {
                if (result.getStatus().isSuccess()) {
                    Log.d(TAG, "Advertise success");
                } else {
                    int statusCode = result.getStatus().getStatusCode();
                    if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                        Log.d(TAG, "Already advertising");
                    }
                }
            }
        });
    }

    /**
     * Begin discovering devices advertising Nearby Connections, if possible.
     */
    public void startDiscovery() {
        if (!isConnectedToNetwork()) {
            Toast.makeText(context, "Not connected to WiFi", Toast.LENGTH_LONG).show();
            return;
        }

        // Discover nearby apps that are advertising with the required service ID.
        String serviceId = context.getString(R.string.service_id);
        Nearby.Connections.startDiscovery(googleApiClient, serviceId, TIMEOUT_DISCOVER, this)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.d(TAG, "Discovery success");
                        } else {
                            // If the user hits 'Discover' multiple times in the timeout window,
                            // the error will be STATUS_ALREADY_DISCOVERING
                            int statusCode = status.getStatusCode();
                            if (statusCode == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                                Log.d(TAG, "Already discovering");
                            }
                        }
                    }
                });
    }

    /**
     * Check if the device is connected (or connecting) to a WiFi network.
     * @return true if connected or connecting, false otherwise.
     */
    private boolean isConnectedToNetwork() {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return (info != null && info.isConnectedOrConnecting());
    }

    /**
     * Send a connection request to a given endpoint.
     * @param endpointId the endpointId to which you want to connect.
     * @param endpointName the name of the endpoint to which you want to connect. Not required to
     *                     make the connection, but used to display after success or failure.
     */
    private void connectTo(String endpointId, final String endpointName) {
        // Send a connection request to a remote endpoint. By passing 'null' for the name,
        // the Nearby Connections API will construct a default name based on device model
        // such as 'LGE Nexus 5'.
        String myName = null;
        byte[] myPayload = null;
        Nearby.Connections.sendConnectionRequest(googleApiClient, myName, endpointId, myPayload,
                new Connections.ConnectionResponseCallback() {
                    @Override
                    public void onConnectionResponse(String endpointId, Status status,
                                                     byte[] bytes) {
                        if (status.isSuccess()) {
                            Toast.makeText(context, "Connected to " + endpointName, Toast.LENGTH_SHORT).show();
                            mOtherEndpointId = endpointId;
                        }
                    }
                }, this);
    }

    /**
     * Send a reliable message to the connected peer. Takes the contents of the EditText and
     * sends the message as a byte[].
     */
    public void sendMessage(List<String> coords) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(coords);
            byte[] bytes = bos.toByteArray();
            Nearby.Connections.sendReliableMessage(googleApiClient, mOtherEndpointId, bytes);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<byte[]> divideArray(byte[] source, int chunksize) {

        List<byte[]> result = new ArrayList<byte[]>();
        int start = 0;
        while (start < source.length) {
            int end = Math.min(source.length, start + chunksize);
            result.add(Arrays.copyOfRange(source, start, end));
            start += chunksize;
        }

        return result;
    }

    @Override
    public void onConnectionRequest(final String endpointId, final String deviceId, final String endpointName, byte[] payload) {
        mConnectionRequestDialog = new AlertDialog.Builder(context)
                .setTitle("Connection Request")
                .setMessage("Do you want to connect to " + endpointName + "?")
                .setCancelable(false)
                .setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        byte[] payload = null;
                        Nearby.Connections.acceptConnectionRequest(googleApiClient, endpointId, payload, ConnectionsHandler.this)
                                .setResultCallback(new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(Status status) {
                                        if (status.isSuccess()) {
                                            mOtherEndpointId = endpointId;
                                        } else {

                                        }
                                    }
                                });
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Nearby.Connections.rejectConnectionRequest(googleApiClient, endpointId);
                    }
                }).create();

        mConnectionRequestDialog.show();
    }

    @Override
    public void onMessageReceived(String s, byte[] bytes, boolean b) {
        Log.d(TAG, "Message received : " + s);
        try {

            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bis);
            List<String> coords = (List<String>) ois.readObject();
            mCallback.onRemoteDrawingReceived(coords);

        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEndpointFound(final String endpointId, String deviceId, String serviceId, final String endpointName) {
        Log.d(TAG, "Endpoint found: " + deviceId);
        if (mMyListDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle("Endpoint(s) Found")
                    .setCancelable(true)
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mMyListDialog.dismiss();
                        }
                    });

            // Create the HostListDialog with a listener
            mMyListDialog = new HostListDialog(context, builder, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String selectedEndpointName = mMyListDialog.getItemKey(which);
                    String selectedEndpointId = mMyListDialog.getItemValue(which);

                    ConnectionsHandler.this.connectTo(selectedEndpointId, selectedEndpointName);
                    mMyListDialog.dismiss();
                }
            });
        }

        mMyListDialog.addItem(endpointName, endpointId);
        mMyListDialog.show();
    }

    @Override
    public void onEndpointLost(String endpointId) {
        if (mMyListDialog != null) {
            mMyListDialog.removeItemByValue(endpointId);
        }
    }

    @Override
    public void onDisconnected(String s) {

    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {
        googleApiClient.reconnect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
