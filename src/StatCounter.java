import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;

public class StatCounter {
    private static final Path filePath = FileSystems.getDefault().getPath("stats.txt");
    private int nquotes = 0;

    public StatCounter(){
        try {
            List<String> in = Files.readAllLines(filePath);
            nquotes = Integer.parseInt(in.get(0));
        }catch (IOException e){
            ErrorLogger.log(e);
        }
    }

    public void count(){
        nquotes++;
        try {
            Files.write(filePath, Arrays.asList(Integer.toString(nquotes)),
                    java.nio.charset.StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        }catch (IOException e){
            ErrorLogger.log(e);
        }
        Main.updatePresence();
    }

    public int getNquotes(){
        return nquotes;
    }
}
