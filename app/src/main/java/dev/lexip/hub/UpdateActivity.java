package dev.lexip.hub;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Stack;

public class UpdateActivity extends AppCompatActivity {

    private Context context;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private boolean downloading = false;
    private String updateURL;
    private NotificationChannel channel;
    private BroadcastReceiver receiver;
    private Stack downloads;
    private String config;
    private DownloadManager downloadmanager;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remote Config: Initialize Firebase Remote Config
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(1000)
                .build();
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);
        mFirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults);
        mFirebaseRemoteConfig.fetch(1);

        mFirebaseRemoteConfig.fetchAndActivate()
                .addOnCompleteListener(this, new OnCompleteListener<Boolean>() {
                    @Override
                    public void onComplete(@NonNull Task<Boolean> task) {
                        ((TextView) findViewById(R.id.tvAppName)).setText(mFirebaseRemoteConfig.getString("app_name"));
                        ((TextView) findViewById(R.id.tvRomName)).setText(mFirebaseRemoteConfig.getString("rom_name"));
                        ((TextView) findViewById(R.id.tvMaintenanceType)).setText(mFirebaseRemoteConfig.getString("maintenance_type"));
                        ((TextView) findViewById(R.id.tvVersion)).setText(mFirebaseRemoteConfig.getString("latest_rom_version_title"));

                        // Footer
                        String appVersionName = "";
                        try {appVersionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
                        } catch (PackageManager.NameNotFoundException e) { e.printStackTrace();}
                        ((TextView) findViewById(R.id.tvAppVersion)).setText("v"+ appVersionName+"  -  lexip.dev/hub");
                        ((TextView) findViewById(R.id.tvAppVersion)).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent intent = new Intent(UpdateActivity.this, WebActivity.class);
                                Bundle b = new Bundle();
                                b.putString("url", "https://lexip.dev/hub");
                                intent.putExtras(b);
                                startActivity(intent);
                            }
                        });
                    }
                });

        if (getIntent().hasExtra("flash")) {
            flash();
            System.exit(0);
            return;
        }

        setContentView(R.layout.activity_update);
        context = UpdateActivity.this;
        downloads = new Stack();

        ((Switch) findViewById(R.id.switchFlashMagisk)).setChecked(true);
        ((Switch) findViewById(R.id.switchFlashMagisk)).setVisibility(View.VISIBLE);

        // Create the NotificationChannel
        channel = new NotificationChannel("Updating", "System Update", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Processing a system update.");
    }

    @Override
    public void onResume() {
        super.onResume();

        try {
            refreshConfig();
            if (config.contains("flash_magisk=false"))
                ((Switch) findViewById(R.id.switchFlashMagisk)).setChecked(false);
        } catch(NullPointerException e){}

        // Declare Listeners
        ((Button) findViewById(R.id.btnChangelog)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(UpdateActivity.this, WebActivity.class);
                Bundle b = new Bundle();
                b.putString("url", "https://telegra.ph/Changelog-12-14");
                intent.putExtras(b);
                startActivity(intent);
            }
        });

        // Check if the update package was already downloaded and if the download is still running
        try {
            new FileInputStream(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip"));
            if(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip").exists() && new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/magisk.zip").exists() &&  !((Button) findViewById(R.id.btnFlash)).getText().equals("CANCEL DOWNLOAD")) {
                if (String.valueOf(new File("/sdcard/" + Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip").length()).equals(mFirebaseRemoteConfig.getString("dumpling_bytes")) || String.valueOf(new File(Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip").length()).equals(mFirebaseRemoteConfig.getString("cheeseburger_bytes"))) {
                    ((Button) findViewById(R.id.btnFlash)).setVisibility(View.VISIBLE);
                    ((Button) findViewById(R.id.btnFlash)).setText("INSTALL AND REBOOT");
                    Toast.makeText((Context) UpdateActivity.this, "Ready to install.",
                            Toast.LENGTH_LONG).show();
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            if(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip").exists()) {
                new Thread() {
                    public void run() {
                        UpdateActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                ((Button) findViewById(R.id.btnFlash)).setText("CANCEL DOWNLOAD");
                                Toast.makeText((Context) UpdateActivity.this, "Downloading...",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }.start();
            }
        }

        ((Switch) findViewById(R.id.switchFlashMagisk)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            refreshConfig();
            try {
                if (isChecked && config.contains("flash_magisk=false")) {
                    String[] tmp = config.split("flash_magisk=false");
                    FileWriter writer = null;
                    writer = new FileWriter("/data/data/dev.lexip.hub/files/config");
                    writer.write(Arrays.toString(tmp) + "flash_magisk=true");
                    writer.close();
                } else if (!isChecked && config.contains("flash_magisk=true")) {
                    String[] tmp = config.split("flash_magisk=true");
                    FileWriter writer = null;
                    writer = new FileWriter("/data/data/dev.lexip.hub/files/config");
                    writer.write(Arrays.toString(tmp) + "flash_magisk=false");
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        });

        ((Button) findViewById(R.id.btnFlash)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((Button) findViewById(R.id.btnFlash)).getText().equals("INSTALL AND REBOOT")) {
                    flash();
                    return;
                } else if (((Button) findViewById(R.id.btnFlash)).getText().equals("CANCEL DOWNLOAD")) {
                    cancelUpdatingProcess();
                    Log.i("Update Activity","Update aborted by user.");
                    return;
                }

                // Request to ignore the battery optimization
                Intent intent = new Intent();
                String packageName = getPackageName();
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                }

                downloadUpdateFiles();
            }
        });
    }

    @Override
    public void onBackPressed () { }

    public void downloadUpdateFiles() {
        new Thread() {
            public void run() {
                if (getSystemProperty("org.pixelexperience.device").equals("dumpling"))
                    updateURL = mFirebaseRemoteConfig.getString("dumpling_download");
                else if (getSystemProperty("org.pixelexperience.device").equals("cheeseburger"))
                    updateURL = mFirebaseRemoteConfig.getString("cheeseburger_download");

                // Update UI
                new Thread() {
                    public void run() {
                        UpdateActivity.this.runOnUiThread(new Runnable() {
                            public void run() {
                                ((Button) findViewById(R.id.btnFlash)).setText("CANCEL DOWNLOAD");
                            }
                        });
                    }
                }.start();

                int[] downloadedFiles = {0};
                int neededDownloads = 2;

                // Remove old files
                deleteDirectory(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub"));

                // Start downloading
                downloadFile(mFirebaseRemoteConfig.getString("magisk_url"), "magisk.zip", true);
                downloadFile(updateURL, mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip", false);

                receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {

                            downloadedFiles[0] += 1;
                            if(downloadedFiles[0]< neededDownloads)
                                return;

                            // Check if the download is still running
                            try {
                                new FileInputStream(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip"));
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                                Log.w(context.getClassLoader().toString(),"Aborting...");
                                cancelUpdatingProcess();
                                downloadedFiles[0] = 0;
                                return;
                            }

                            // Update UI
                            new Thread() {
                                public void run() {
                                    UpdateActivity.this.runOnUiThread(new Runnable() {
                                        public void run() {
                                            ((Button) findViewById(R.id.btnFlash)).setText("INSTALL AND REBOOT");
                                        }
                                    });
                                }
                            }.start();

                            // Purge all download objects
                            downloads.clear();

                            intent = new Intent(context, UpdateActivity.class);
                            intent.putExtra("flash", true);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

                            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, "Updating")
                                    .setSmallIcon(R.drawable.ic_notify_android)
                                    .setContentTitle("System Update")
                                    .setContentText("Download complete. Tap here to reboot.")
                                    .setPriority(NotificationCompat.PRIORITY_MAX)
                                    .setContentIntent(pendingIntent)
                                    .setOngoing(true)
                                    .setColorized(true)
                                    .setColor(Color.argb(255,150,255,150));

                            NotificationManager notificationManager = getSystemService(NotificationManager.class);
                            notificationManager.createNotificationChannel(channel);
                            notificationManager.notify(0, notificationBuilder.build());


                            UpdateActivity.this.runOnUiThread(new Runnable() {
                                public void run() {
                                    findViewById(R.id.btnFlash).setVisibility(View.VISIBLE);
                                    ((Button) findViewById(R.id.btnFlash)).setText("INSTALL AND REBOOT");
                                }
                            });
                        }
                    }
                };
                registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        }.start();
    }


    /**
     * The actual flashing process
     * Creates the recovery command and reboots to recovery
     */
    public void flash() {
        try {
            if(!String.valueOf(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip").length()).equals(mFirebaseRemoteConfig.getString("dumpling_bytes")) && !String.valueOf(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip").length()).equals(mFirebaseRemoteConfig.getString("cheeseburger_bytes"))){
                Log.w("Flash","ROM Package corrupted");
                cancelUpdatingProcess();
                return;
            }

            File cmdFile = new File("/cache/recovery/command");
            if (cmdFile.exists() && cmdFile.isFile())
                cmdFile.delete();

            FileWriter myWriter = new FileWriter("/cache/recovery/command");
            myWriter.write("boot-recovery\n--update_package=" + Environment.DIRECTORY_DOWNLOADS + "/hub/" + mFirebaseRemoteConfig.getString("latest_rom_version") + ".zip\n");
            refreshConfig();
            if (config.contains("flash_magisk=true") && new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub/magisk.zip").exists())
                myWriter.write("--update_package=" + Environment.DIRECTORY_DOWNLOADS + "/hub/magisk.zip\n");
            myWriter.write("--wipe_cache\nreboot");
            myWriter.close();

            new Thread() {
                public void run() {
                    // Reboot to recovery
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    pm.reboot("recovery");
                }
            }.start();
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            Log.i("Flash", "Update failed", e);
        }
    }

    public void downloadFile(String url, String filename, boolean silent) {
        downloadmanager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("System Update")
                .setDescription("Preparing")
                .setVisibleInDownloadsUi(false)
                .setDestinationInExternalPublicDir((String.valueOf(Environment.DIRECTORY_DOWNLOADS)), "/hub/" + filename);

        if (silent)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        else
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

        downloads.push((long) downloadmanager.enqueue(request));
    }

    public String getSystemProperty(String key) {
        String value = null;
        try {
            value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return value;
    }

    public void refreshConfig() {
        try {
            if (!new File("/data/data/dev.lexip.hub/files/config").exists()) {
                FileWriter writer = null;
                writer = new FileWriter("/data/data/dev.lexip.hub/files/config");
                writer.write("flash_magisk=true");
                writer.close();

            } else {
                File myObj = new File("/data/data/dev.lexip.hub/files/config");
                Scanner reader = new Scanner(myObj);
                while (reader.hasNextLine()) {
                    config += reader.nextLine();
                }
                reader.close();

                if (!config.contains("flash_magisk")) {
                    FileWriter writer = null;
                    writer = new FileWriter("/data/data/dev.lexip.hub/files/config");
                    writer.write("flash_magisk=true");
                    writer.close();
                }
            }
        } catch (IOException e) {
        }
    }

    public boolean deleteDirectory(File dir) {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return dir.delete();
    }

    public void cancelUpdatingProcess(){
        Log.i(context.getClassLoader().toString(),"Canceling the update process...");

        // Cancel Downloads
        while (!downloads.isEmpty()) {
            downloadmanager.remove(((long) downloads.pop()));
        }

        // Delete all files
        deleteDirectory(new File("/sdcard/"+Environment.DIRECTORY_DOWNLOADS + "/hub"));

        // Update UI
        UpdateActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                ((Button) findViewById(R.id.btnFlash)).setText("DOWNLOAD UPDATE");
            }
        });
    }
}