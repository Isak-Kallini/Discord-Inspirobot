import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static net.dv8tion.jda.api.interactions.commands.OptionType.BOOLEAN;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;

public class Main extends ListenerAdapter {

    public static void main(String[] args) throws IOException, InterruptedException {
        final String token = Files.readString(Paths.get("token.txt")).trim();


        Main main = new Main();
        final JDA jda = JDABuilder.createLight(token, EnumSet.noneOf(GatewayIntent.class))
                .addEventListeners(new Main())
                .build();

        CommandListUpdateAction commands = jda.updateCommands();

        commands.addCommands(
                Commands.slash("quote", "Generate a quote.")
        );

        commands.addCommands(
                Commands.slash("dailyquote", "Toggle if the bot should send daily quotes")
                        .addOptions(new OptionData(BOOLEAN, "status", "Should the bot send a quote every day?")
                            .setRequired(true))
                        .addOptions(new OptionData(INTEGER, "time", "Time for the message to be sent everyday, starting in x minutes"))

        );

        commands.queue();


        /*
        jda.getRestPing().queue(ping ->
                // shows ping in milliseconds
                System.out.println("Logged in with ping: " + ping)
        );*/
        jda.awaitReady();
        main.initialize(jda);



    }

    public void initialize(JDA jda){
        try {
            String[] in = (Files.readString(Paths.get("dailyTime.txt")).trim()).split(" ");
            Calendar time = new GregorianCalendar();
            time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(in[0]));
            time.set(Calendar.MINUTE, Integer.parseInt(in[1]));
            if(time.before(new GregorianCalendar())){
                time.add(Calendar.DAY_OF_MONTH, 1);
            }

            String guildId = in[2];
            String channelId = in[3];

            Guild guild = jda.getGuildById(Long.parseLong(guildId));
            TextChannel channel = guild.getTextChannelById(channelId);
            scheduleDaily(timer, time, 24*60*60*1000, channel);
            timerIsActive = true;
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        if (event.getGuild() == null)
            return;
        switch (event.getName()) {
            case "quote":
                sendQuote(event.getChannel());
                return;
            case "dailyquote":
                dailyQuote(event);
        }
    }


    public void sendQuote(MessageChannel channel){
        String pictureUrl = getUrl();

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Quote", pictureUrl)
                .setImage(pictureUrl)
                .setColor(new Color(0, 102, 0)).build();

        channel.sendMessageEmbeds(embed).queue();
    }


    public String getUrl(){
        try {
            URLConnection connection = new URL("https://inspirobot.me/api?generate=true").openConnection();
            connection.connect();
            InputStream input = connection.getInputStream();
            //System.out.println(connection.getContentType());

            String pictureUrl = new BufferedReader(new InputStreamReader(input)).readLine();
            return pictureUrl;
        } catch (IOException e) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                Files.write(Paths.get("log.txt"), (sw.toString()).getBytes());
            }catch(IOException f){
                e.printStackTrace();
            }
            e.printStackTrace();
            return "Couldn't generate a picture.";
        }
    }

    Boolean timerIsActive = false;
    Timer timer = new Timer();

    public void scheduleDaily(Timer t, Calendar d, int period, MessageChannel channel){
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                sendQuote(channel);
            }
        }, d.getTime(), period);
    }

    public void dailyQuote(SlashCommandInteractionEvent event){
        if(event.getMember().isOwner()) {
            Boolean isActiveInput = event.getOption("status", false, OptionMapping::getAsBoolean);
            Integer timeIn = event.getOption("time", 0, OptionMapping::getAsInt);
            int period = 24 * 60 * 60 * 1000;
            Calendar time = new GregorianCalendar();
            time.add(Calendar.MINUTE, timeIn);
            System.out.println(timeIn + " " + time.getTime());
            if (isActiveInput && !timerIsActive) {
                timerIsActive = true;
                scheduleDaily(timer, time, period, event.getMessageChannel());
                storeTime(time, event);
                say(event, "Inspirobot will send a new quote starting in " + timeIn + " minutes and then every 24 hours.");

            } else if (timerIsActive && isActiveInput) {
                timer.cancel();
                timer = new Timer();
                scheduleDaily(timer, time, period, event.getMessageChannel());
                storeTime(time, event);
                say(event, "Removed old time. Inspirobot will now send a new quote starting in " + timeIn + " minutes and then every 24 hours.");
            } else if (!isActiveInput && timerIsActive) {
                timer.cancel();
                timer = new Timer();
                try {
                    Files.delete(Paths.get("dailyTime.txt"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                say(event, "Inspirobot will no longer send a daily quote.");
            }
        }
    }

    public void storeTime(Calendar time, SlashCommandInteractionEvent event) {
        Integer hour = time.get(Calendar.HOUR_OF_DAY);
        Integer minute = time.get(Calendar.MINUTE);
        String guildId = event.getGuild().getId();
        String channelId = event.getMessageChannel().getId();

        try {
            Files.write(Paths.get("dailyTime.txt"), (hour + " " + minute + " " + guildId + " " + channelId).getBytes());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void say(SlashCommandInteractionEvent event, String content) {
        event.reply(content).queue();
    }
}