import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.BOOLEAN;
import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;

public class Main extends ListenerAdapter {
    private static List<DailyQuote> dailyQuotes = new ArrayList<>();
    final Path dailyQuotePath = Paths.get("dailyTime.txt");

    public static void main(String[] args) throws IOException, InterruptedException {
        final String token = Files.readString(Paths.get("token.txt")).trim();

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
        jda.awaitReady();
        initialize(jda);



    }

    public static void initialize(JDA jda){
        try {
            List<String> in = (Files.readAllLines(Paths.get("dailyTime.txt")));
            for(String e: in){
                dailyQuotes.add(DailyQuote.fromLogFormat(e, jda));
            }
            dailyQuotes.forEach(DailyQuote::startSchedule);
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
                sendQuote(event);
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
    }

    public static void sendQuoteNoReply(MessageChannel channel){
        String pictureUrl = getUrl();

        MessageEmbed embed = new EmbedBuilder()
                .setTitle("Quote", pictureUrl)
                .setImage(pictureUrl)
                .setColor(new Color(0, 102, 0)).build();

        channel.sendMessageEmbeds(embed).queue();
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

            } else if (isActiveInput && tempList.size() == 1) {
                DailyQuote existingQuote = tempList.get(0);
                existingQuote.stop();
                dailyQuotes.remove(existingQuote);

                try {
                    Files.delete(dailyQuotePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                dailyQuotes.forEach(e -> e.store(dailyQuotePath));

                DailyQuote dailyQuote = new DailyQuote(time, event.getGuild(), event.getMessageChannel());
                dailyQuote.startSchedule();
                dailyQuote.store(dailyQuotePath);
                dailyQuotes.add(dailyQuote);
                say(event, "Removed old time. Inspirobot will now send a new quote starting in " + timeIn + " minutes and then every 24 hours.");

            } else if (!isActiveInput && tempList.size() == 1) {
                DailyQuote existingQuote = tempList.get(0);
                existingQuote.stop();
                dailyQuotes.remove(existingQuote);

                try {
                    Files.delete(dailyQuotePath);
                } catch (IOException e) {
                    e.printStackTrace();
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