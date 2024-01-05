package com.example.ptyxiak2;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UStats {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("d-M-yyyy HH:mm:ss");
    public static final String TAG = UStats.class.getSimpleName();

    // Map package names to display names
    private static final Map<String, String> appDisplayNameMap = new HashMap<>();

    //This is used to replace the package name with the app's name in the logfile
    static {
        appDisplayNameMap.put("com.instagram.android", "Instagram");
        appDisplayNameMap.put("com.facebook.katana", "Facebook");
        appDisplayNameMap.put("com.zhiliaoapp.musically", "TikTok");
        appDisplayNameMap.put("com.linkedin.android", "LinkedIn");
        appDisplayNameMap.put("com.facebook.orca", "Messenger");
    }

    // Directory to store CSV files
    private static final String CSV_DIRECTORY_PATH = Environment.getExternalStorageDirectory().getPath() + "/Documents/csv-files";

    public static List<UsageStats> getUsageStatsList(Context context) {
        UsageStatsManager usm = getUsageStatsManager(context);
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.MONTH, -1);
        long startTime = calendar.getTimeInMillis();

        Log.d(TAG, "Range start:" + dateFormat.format(startTime));
        Log.d(TAG, "Range end:" + dateFormat.format(endTime));

        List<UsageStats> usageStatsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        return usageStatsList;
    }

    public static void printUsageStats(Context context, List<UsageStats> usageStatsList, List<String> trackedApps) {
        for (UsageStats u : usageStatsList) {
            if (trackedApps.contains(u.getPackageName())) {
                // Get the app's usage events
                List<UsageEvents.Event> appEvents = getAppUsageEvents(context, u.getPackageName());

                // Initialize variables to track the last event time and calculate duration
                long lastEventTime = 0;

                // Iterate through events and filter for the specified app
                for (UsageEvents.Event event : appEvents) {
                    if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                        lastEventTime = event.getTimeStamp();
                        Log.d(TAG, "App: " + getAppDisplayName(u.getPackageName()) + "\t" +
                                "Timestamp: " + dateFormat.format(event.getTimeStamp()) +
                                "\tEvent Type: " + getEventTypeLabel(event.getEventType()));

                        // Write to CSV file
                        writeToFile(u.getPackageName(), event.getTimeStamp(), 0); // Duration is initially 0
                    } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                        // Calculate the duration between opening and closing the app
                        long duration = event.getTimeStamp() - lastEventTime;

                        if (!formatDuration(duration).equals("00:00:00")) {
                            Log.d(TAG, "App: " + getAppDisplayName(u.getPackageName()) + "\t" +
                                    "Timestamp: " + dateFormat.format(event.getTimeStamp()) +
                                    "\tEvent Type: " + getEventTypeLabel(event.getEventType()) +
                                    "\tDuration: " + formatDuration(duration));

                            // Write to CSV file
                            writeToFile(u.getPackageName(), event.getTimeStamp(), duration);
                        }
                    }
                }
            }
        }
    }

    // This method writes data to a CSV file
    private static void writeToFile(String appName, long timeStamp, long duration) {
        try {
            File csvDirectory = new File(CSV_DIRECTORY_PATH);
            if (!csvDirectory.exists()) {
                csvDirectory.mkdirs();
            }

            String fileName = appName + ".csv";
            File csvFile = new File(csvDirectory, fileName);

            boolean isNewFile = !csvFile.exists();

            FileWriter writer = new FileWriter(csvFile, true); // true for append mode

            if (isNewFile) {
                // Write header if the file is new
                writer.append("Timestamp,Duration\n");
            }

            // Write data
            writer.append(dateFormat.format(timeStamp)).append(",").append(formatDuration(duration)).append("\n");

            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //this prints the list of the apps that we are tracking
    public static void printCurrentUsageStatus(Context context) {
        // Define the list of tracked apps (Instagram and Facebook)
        List<String> trackedApps = Arrays.asList("com.instagram.android", "com.facebook.katana", "com.zhiliaoapp.musically",
                "com.linkedin.android", "com.facebook.orca");

        // Get the usage stats and print for tracked apps
        printUsageStats(context, getUsageStatsList(context), trackedApps);
    }

    @SuppressWarnings("ResourceType")
    private static UsageStatsManager getUsageStatsManager(Context context) {
        UsageStatsManager usm = (UsageStatsManager) context.getSystemService("usagestats");
        return usm;
    }

    private static List<UsageEvents.Event> getAppUsageEvents(Context context, String packageName) {
        // Obtain the usage stats manager
        UsageStatsManager usm = getUsageStatsManager(context);

        // Set the start and end time for querying events
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        calendar.add(Calendar.YEAR, -1);
        long startTime = calendar.getTimeInMillis();

        // Query events for the specified app in the given time range
        UsageEvents appEvents = usm.queryEvents(startTime, endTime);
        List<UsageEvents.Event> events = new ArrayList<>();

        // Iterate through events and filter for the specified app
        while (appEvents.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            appEvents.getNextEvent(event);
            if (event.getPackageName().equals(packageName)) {
                events.add(event);
            }
        }

        return events;
    }

    private static String getAppDisplayName(String packageName) {
        // Get app display name from the mapping, or return the original package name
        return appDisplayNameMap.getOrDefault(packageName, packageName);
    }

    private static String getEventTypeLabel(int eventType) {
        // Map event types to labels
        Map<Integer, String> eventTypeLabels = new HashMap<>();
        eventTypeLabels.put(UsageEvents.Event.ACTIVITY_RESUMED, "App opened");
        eventTypeLabels.put(UsageEvents.Event.ACTIVITY_PAUSED, "App closed");

        // Get the label for the given event type
        return eventTypeLabels.getOrDefault(eventType, String.valueOf(eventType));
    }

    private static String formatDuration(long duration) {
        // Convert duration to hours, minutes, and seconds
        long hours = (duration / (1000 * 60 * 60)) % 24;
        long minutes = (duration / (1000 * 60)) % 60;
        long seconds = (duration / 1000) % 60;

        while (hours==0 && minutes==0 && seconds==0) {
            return String.format("00:00:00");
        }


        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
