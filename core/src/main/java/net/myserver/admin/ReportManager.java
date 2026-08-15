package net.myserver.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ReportManager {
    private static final File REPORTS_FILE = new File("world_data", "reports.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static List<ReportEntry> reports = new ArrayList<>();

    public static class ReportEntry {
        public String reporterName;
        public String reportedName;
        public String reason;
        public double x, y, z;
        public long timestamp;

        public ReportEntry(String reporterName, String reportedName, String reason, double x, double y, double z) {
            this.reporterName = reporterName;
            this.reportedName = reportedName;
            this.reason = reason;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static void init() {
        if (REPORTS_FILE.exists()) {
            try (FileReader reader = new FileReader(REPORTS_FILE)) {
                Type type = new TypeToken<List<ReportEntry>>(){}.getType();
                reports = gson.fromJson(reader, type);
                if (reports == null) reports = new ArrayList<>();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void addReport(ReportEntry entry) {
        reports.add(entry);
        try (FileWriter writer = new FileWriter(REPORTS_FILE)) {
            gson.toJson(reports, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
