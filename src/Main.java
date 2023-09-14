import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.discordbots.api.client.DiscordBotListAPI;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.BOOLEAN;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;

public class Main extends ListenerAdapter {
    private static List<DailyQuote> dailyQuotes = new ArrayList<>();
    final Path dailyQuotePath = Paths.get("dailyTime.txt");

    static final Calendar startTime = new GregorianCalendar();

    private static JDA jda;
    private static StatCounter stats;

    public static void main(String[] args) throws IOException, InterruptedException {
        final String token = Files.readString(Paths.get("token.txt")).trim();

        stats = new StatCounter();
        jda = JDABuilder.createLight(token, EnumSet.noneOf(GatewayIntent.class))
                .addEventListeners(new Main())
                .setActivity(Activity.playing(stats.getNquotes() + " Quotes generated!"))
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
        commands.addCommands(
                Commands.slash("stats", "Some stats about the bot")
        );

        commands.queue();
        jda.awaitReady();
        initialize(jda);
        setTopGGCount();
    }

    public static void setTopGGCount() throws IOException {
        List<Guild> guilds = jda.getGuilds();
        String token = Files.readString(Paths.get("top-gg-token.txt")).trim();
        DiscordBotListAPI api = new DiscordBotListAPI.Builder()
                .token(token)
                .botId("430803590106447892")
                .build();

        api.setStats(guilds.size());
    }

    public static void initialize(JDA jda){
        ErrorLogger.deleteOldLogs();
        try {
            List<String> in = (Files.readAllLines(Paths.get("dailyTime.txt")));
            for(String e: in){
                DailyQuote quote = DailyQuote.fromLogFormat(e, jda);
                if(quote != null)
                    dailyQuotes.add(quote);
            }
            dailyQuotes.forEach(DailyQuote::startSchedule);
        }catch(Exception e){
            ErrorLogger.log(e);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event){
        if (event.getGuild() == null)
            return;
        switch (event.getName()) {
            case "quote":
                sendQuote(event);
                return;
            case "stats":
                stats(event);
                return;
            case "dailyquote":
                dailyQuote(event);
        }
    }

    public static void sendQuote(SlashCommandInteractionEvent event){
        String pictureUrl = getUrl();

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Quote", pictureUrl)
                .setImage(pictureUrl)
                .setColor(new Color(0, 102, 0)).build();

        event.replyEmbeds(embed).queue();
        stats.count();
    }

    public static void updatePresence(){
        jda.getPresence().setActivity(Activity.playing(stats.getNquotes() + " Quotes generated!"));
    }

    public static void stats(SlashCommandInteractionEvent event){
        try {
            setTopGGCount();
        }catch (Exception e){
            ErrorLogger.log(e);
        }
        List<Guild> guilds = jda.getGuilds();

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Stats")
                .setDescription("Number of servers: **" + guilds.size() + "**\n" +
                        "Number of quotes sent: **" + stats.getNquotes() + "**")
                .setColor(new Color(0, 102, 0)).build();
        event.replyEmbeds(embed).queue();
    }

    public static void sendQuoteNoReply(MessageChannel channel){
        String pictureUrl = getUrl();

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Quote", pictureUrl)
                .setImage(pictureUrl)
                .setColor(new Color(0, 102, 0)).build();

        channel.sendMessageEmbeds(embed).queue();
        stats.count();
    }


    public static String getUrl(){
        try {
            URLConnection connection = new URL("https://inspirobot.me/api?generate=true").openConnection();
            connection.connect();
            InputStream input = connection.getInputStream();
            //System.out.println(connection.getContentType());

            String pictureUrl = new BufferedReader(new InputStreamReader(input)).readLine();
            return pictureUrl;
        } catch (IOException e) {
            ErrorLogger.log(e);
            e.printStackTrace();
            return "Couldn't generate a picture.";
        }
    }

    public void dailyQuote(SlashCommandInteractionEvent event){
        if(event.getMember().isOwner()) {
            Boolean isActiveInput = event.getOption("status", false, OptionMapping::getAsBoolean);
            Integer timeIn = event.getOption("time", 0, OptionMapping::getAsInt);

            Calendar time = new GregorianCalendar();
            time.add(Calendar.MINUTE, timeIn);

            ArrayList<DailyQuote> tempList = new ArrayList<>(dailyQuotes);
            tempList.removeIf(e -> !e.getGuild().getId().equals(event.getGuild().getId()));
            if (isActiveInput && tempList.size() == 0 ) {
                DailyQuote dailyQuote = new DailyQuote(time, event.getGuild(), event.getMessageChannel());
                dailyQuote.startSchedule();
                dailyQuote.store(dailyQuotePath);
                dailyQuotes.add(dailyQuote);

                say(event, "Inspirobot will send a new quote starting in " + timeIn + " minutes and then every 24 hours.");

            } else if (isActiveInput && tempList.size() >= 1) {
                for (DailyQuote existing : tempList){
                    existing.stop();
                    dailyQuotes.remove(existing);
                }

                try {
                    Files.delete(dailyQuotePath);
                } catch (IOException e) {
                    ErrorLogger.log(e);
                }
                dailyQuotes.forEach(e -> e.store(dailyQuotePath));

                DailyQuote dailyQuote = new DailyQuote(time, event.getGuild(), event.getMessageChannel());
                dailyQuote.startSchedule();
                dailyQuote.store(dailyQuotePath);
                dailyQuotes.add(dailyQuote);
                say(event, "Removed old time. Inspirobot will now send a new quote starting in " + timeIn + " minutes and then every 24 hours.");

            } else if (!isActiveInput && tempList.size() >= 1) {
                for (DailyQuote existing : tempList){
                    existing.stop();
                    dailyQuotes.remove(existing);
                }

                try {
                    Files.delete(dailyQuotePath);
                } catch (IOException e) {
                    ErrorLogger.log(e);
                }
                dailyQuotes.forEach(e -> e.store(dailyQuotePath));
                say(event, "Inspirobot will no longer send a daily quote.");
            }
        }else{
            say(event, "Only the server owner can set up daily quotes.");
        }
    }


    public void say(SlashCommandInteractionEvent event, String content) {
        event.reply(content).queue();
    }
}