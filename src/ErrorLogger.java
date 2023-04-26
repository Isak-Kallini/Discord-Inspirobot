import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class ErrorLogger {
    private static final Path filePath = FileSystems.getDefault().getPath("log/log" + timeToLogFormat(new GregorianCalendar()) + ".txt");

    public static void log(Exception e){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        try {
            Files.write(filePath, Arrays.asList(timeToLogFormat(new GregorianCalendar()) + " " + sw.toString()),
                    java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            e.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static String timeToLogFormat(Calendar c){
        return (c.get(Calendar.YEAR) + "-" +
                (c.get(Calendar.MONTH) + 1) + "-" +
                c.get(Calendar.DAY_OF_MONTH) + "-" +
                c.get(Calendar.HOUR_OF_DAY) + "." +
                c.get(Calendar.MINUTE)) + "." +
                c.get(Calendar.SECOND);
    }

    public static void deleteOldLogs(){
        try {
            DirectoryStream stream = Files.newDirectoryStream(FileSystems.getDefault().getPath("log"));
            ArrayList<String> logFiles = new ArrayList<>();
            stream.forEach(e -> {
                if(e.toString().contains("log\\log")){
                    logFiles.add(e.toString());
                }
            });
            while(logFiles.size() > 5){
                Files.delete(Paths.get(logFiles.get(0)));
                logFiles.remove(0);
            }
        } catch (IOException e) {
            log(e);
        }
    }
}
