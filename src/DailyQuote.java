import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class DailyQuote {
    public static DailyQuote fromLogFormat(String s, JDA jda){
        String[] in = s.split(" ");
        Calendar time = new GregorianCalendar();
        time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(in[0]));
        time.set(Calendar.MINUTE, Integer.parseInt(in[1]));
        if(time.before(new GregorianCalendar())){
            time.add(Calendar.DAY_OF_MONTH, 1);
        }
        Guild guild = jda.getGuildById(Long.parseLong(in[2]));
        TextChannel channel = guild.getTextChannelById(in[3]);
        return new DailyQuote(time, guild, channel);
    }

    private Timer timer = new Timer();
    private Calendar time = new GregorianCalendar();
    private Guild guild;
    private MessageChannel channel;

    public DailyQuote(Calendar t, Guild g, MessageChannel c){
        time = t;
        guild = g;
        channel = c;
    }

    public void startSchedule(){
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Main.sendQuoteNoReply(channel);
            }
        }, time.getTime(), 24*60*60*1000);
    }

    public void stop(){
        timer.cancel();
    }

    public void store(Path path){
        try {
            Files.write(path, Arrays.asList(toLogFormat()), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String toLogFormat(){
        return time.get(Calendar.HOUR_OF_DAY) + " " + time.get(Calendar.MINUTE) + " " + guild.getId() + " " + channel.getId();
    }

    public Guild getGuild(){
        return guild;
    }
}
