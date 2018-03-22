/*
    Author: Jonathan Gilliam
    All rights reserved.
 */

import com.google.api.services.drive.Drive;
import com.google.api.services.sheets.v4.Sheets;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Main extends Application {
    public Scene scene;

    private static final String home = System.getProperty("user.home") + "\\EZ3D";

    // Get user properties
    private static Properties prop = new Properties();
    static {
        try {
            prop.load(new FileInputStream(System.getProperty("user.home") + "\\EZ3D\\EZ3D.properties"));
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }
    private static Sheets sheetService;
    private static Drive driveService;
    private static ArrayList<List<String>> data;

    /**
     * Searches through users to find closest one to inputted search
     * @return list of folders that match search closest
     */
    static ArrayList<File> findDir(String dir) {
        ArrayList<File> results = new ArrayList<>();

        if (new File("files\\").exists()) {
            File[] users = new File("files\\").listFiles();
            for (File user : users) {
                if (!user.isDirectory())
                    continue;
                if (user.toString().contains(dir))
                    results.add(user);
            }
        }
        return results;
    }

    /**
     *  Delete any files that are 7+ days old to free up space
     */
    private static void deleteOldFiles() {
        ArrayList<File> files = filesInDirectory(new File("files\\"), true);

        for(File file:files) {
            long diff = new Date().getTime() - file.lastModified();

            if (file.isDirectory()) {
                if (file.list().length == 0) {
                    file.delete();
                    System.out.println("Removing " + file.getPath() + " for being an empty directory");
                }
            }
            else if (diff > Float.parseFloat(prop.getProperty("FILE_RETENTION_PERIOD")) * 24 * 60 * 60 * 1000) {
                System.out.println("Removing " + file.getPath() + " for being 7+ days old");
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    /**
     * Goes through given directory, and gets all files inside it and it's subdirectories
     * @param folder The directory you want to search through
     * @param includeDir weather to include directories
     * @return List off files in directory/subdirectories
     */
    static ArrayList<File> filesInDirectory(File folder, boolean includeDir) {
        ArrayList<File> files = new ArrayList<>();

        for (String filePath : Objects.requireNonNull(folder.list())) {
            File file = new File(folder.getPath() + "\\" + filePath);
            if (file.isDirectory())
            {
                files.addAll(filesInDirectory(file, includeDir));
                if (includeDir)
                    files.add(file);
            }

            else
                files.add(file);
        }
        // Sort files by last modified date, newest first
        files.sort(Comparator.comparingLong(File::lastModified));

        return files;
    }

    /**
     * Downloads new files added to the sheet
     * @throws IOException if unable to save to file
     */
    static void refreshFiles() throws IOException {
        refreshData();

        for(int i=0; i<data.get(0).size(); i++) {
            if (data.get(0).get(i).equals("") || data.get(1).get(i).equals("") || data.get(2).get(i).equals("") || data.get(3).get(i).equals("") || data.get(4).get(i).equals(""))
                continue;

            long diff;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/y H:m:s");
            try {
                LocalDateTime dateTime1 = LocalDateTime.parse(data.get(4).get(i), formatter);
                LocalDateTime dateTime2 = LocalDateTime.now();
                diff = java.time.Duration.between(dateTime1, dateTime2).toDays();
            } catch (DateTimeParseException dtpe) {
                continue;
            }

            if (diff < Float.parseFloat(prop.getProperty("FILE_RETENTION_PERIOD")) && data.size() >= 5) {
                // build user's file path
                String path = String.format("files\\%s\\%c_%s_%s_0.stl",
                        data.get(0).get(i), data.get(1).get(i).charAt(0), data.get(2).get(i),
                        data.get(5).get(i));

                File file = new File(path);

                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();

                if (!file.exists() && data.get(3).size() > i) {
                    Pattern pattern = Pattern.compile("[-\\w]{25,}");
                    Matcher matcher = pattern.matcher(data.get(3).get(i));

                    for (int j=0; matcher.find(); j++) {
                        System.out.println("downloading file " + matcher.group() + " for " + data.get(0).get(i));

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        driveService.files().get(matcher.group())
                                .executeMediaAndDownloadTo(outputStream);

                        outputStream.writeTo(new FileOutputStream(path));
                        outputStream.close();

                        path = path.replace(j + ".stl", (j+1) + ".stl");
                    }
                }
            }
        }
    }

    /**
     * Refreshes the Data list
     * @throws IOException if unable to search through folders
     */
    @SuppressWarnings("unchecked") // ignore cast warning
    private static void refreshData() throws IOException {
        data.clear();

        // Gets the title row from the sheet
        String titleRange = prop.getProperty("SHEET_NAME") + "!A1:1";
        System.out.println("Spreadsheet id:" + prop.getProperty("SPREADSHEET_ID"));

        ArrayList<String> titleResult = (ArrayList<String>)(sheetService.spreadsheets().values()
                .get(prop.getProperty("SPREADSHEET_ID"), titleRange)
                .execute().getValues().toArray()[0]);

        // Gets correct title labels we're looking for from config file
        String[] labels = {prop.getProperty("EMAIL_COLUMN_LABEL", "Email"),
                prop.getProperty("FIRST_NAME_COLUMN_LABEL", "First Name"),
                prop.getProperty("LAST_NAME_COLUMN_LABEL", "Last Name"),
                prop.getProperty("DRIVE_LINKS_COLUMN_LABEL", "Files"),
                prop.getProperty("TIMESTAMP_COLUMN_LABEL", "Timestamp"),
                prop.getProperty("PRINT_ID_COLUMN_LABEL", "Print ID")
        };

        for (String label : labels) {
            // Searches through the sheet's title row for the labels from the config file
            int index = titleResult.indexOf(label);
            if (label.isEmpty())
                throw new IllegalArgumentException(
                        "Config file missing inputs. Fill in 'search module' COLUMN_LABEL section");

            if (index != -1) {
                String range = String.format("%s!%c2:%c", prop.getProperty("SHEET_NAME", "Desktop 3D"),
                        (char) ('A' + index), (char) ('A' + index));

                // Request the column for the specific label
                List<List<Object>> results = sheetService.spreadsheets().values()
                        .get(prop.getProperty("SPREADSHEET_ID"), range)
                        .execute().getValues();

                // Rotate result into a row, and add it to data
                ArrayList<String> temp = new ArrayList<>();
                for (List<Object> result : results) {
                    if (result.isEmpty())
                        temp.add("");
                    else
                        temp.add(result.get(0).toString());
                }
                data.add(temp);
            }
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("EZ3D.fxml"));
        primaryStage.setTitle("EZ3D");
        primaryStage.setScene(scene = new Scene(root, 500, 300));
        primaryStage.setOnCloseRequest(e -> System.exit(1));
        primaryStage.show();
    }


    public static void main(String[] args) throws Exception {
        data = new ArrayList<>();

        // Authorize services
        sheetService = GoogleServices.getSheetsService();
        driveService = GoogleServices.getDriveService();

        Thread refreshLocalFiles = new Thread(() -> {
            while (true) {
                try {
                    if (prop.getProperty("ENABLE_STL_SEARCH", "true").equalsIgnoreCase("true"))
                        refreshFiles();
                    if (prop.getProperty("REMOVE_OLD_FILES", "true").equalsIgnoreCase("true"))
                        deleteOldFiles();
                    Thread.sleep((long) (Float.parseFloat(prop.getProperty("FILE_REFRESH_RATE")) * 60 * 1000));
                } catch(Exception ioe){
                    ioe.printStackTrace();
                    System.exit(1);
                }
            }
        });
        refreshLocalFiles.start();

        launch(args);
    }
}
