import java.util.List;
import java.util.Map;

import kotlin.Pair;
import kotlin.Triple;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.UserActivityEndEvent;
import net.dv8tion.jda.api.events.user.UserActivityStartEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

public class Application extends ListenerAdapter {
    public static final List<String> LEAGUE_REACTION_EMOJIS = List.of("🤮", "🤓", "🤢");
    public static final String BRUH_FUNNY_0 = "https://media.discordapp.net/attachments/1089873652008362014/1089873701140439070/bruhfunny0.gif?width=622&height=622";
    public static final String BRUH_FUNNY_1 = "https://media.discordapp.net/attachments/1089873652008362014/1089875840642326538/bruhfunny1.gif?width=622&height=622";
    public static final String SHALL_NOT_BE_NAMED = "League of Legends";
    public static final String KEYWORD = "judgment";
    public static final String ALARM_WORD = "alarm";

    private static final DB DATABASE = DBMaker.fileDB("guilty_sinners.mapdb").transactionEnable().make();
    private static final Map<Long, Long> judgmentTracker = DATABASE.hashMap("judgmentTracker")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();
    private static final Map<Long, Long> currentTimeTracker = DATABASE.hashMap("currentTracker")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();
    private static final Map<Long, Boolean> alarmPermissions = DATABASE.hashMap("alarmPermissions")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.BOOLEAN)
        .createOrOpen();

    public static void main(String[] args) {
        JDABuilder.createDefault(args[0])
            .addEventListeners(new Application())
            .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_PRESENCES)
            .build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        var message = event.getMessage().getContentRaw();
        if (message.toLowerCase().contains("league of legends")) {
            for (String emojiCode : LEAGUE_REACTION_EMOJIS) {
                event.getChannel().addReactionById(event.getMessageId(), Emoji.fromUnicode(emojiCode)).queue();
            }
            event.getMessage().reply(BRUH_FUNNY_0).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals(KEYWORD)) {
            OptionMapping username = event.getOption("user");
            if (username == null) {
                event.reply("Must specify a user: '/" + KEYWORD + " [user]'.").queue();
            } else {
                judgeUser(username.getAsUser(), event);
            }
        }
        if (event.getName().equals(ALARM_WORD)) {
            event.getGuild().getIdLong();
        }
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        var userOption = new OptionData(OptionType.USER, "user", "Pass judgment upon this user", true);
        var judgmentCommand = Commands.slash("judgment", "Judge a user's League habits").addOptions(userOption);
        var alarmCommand = Commands.slash("alarm", "Toggle an alarm for when a user starts playing League");
        event.getGuild().updateCommands().addCommands(judgmentCommand, alarmCommand).queue();
    }

    private void judgeUser(User user, SlashCommandInteractionEvent event) {
        var time = judgmentTracker.getOrDefault(user.getIdLong(), 0L);
        if (time.equals(0L)) {
            event.reply(user.getName() + " has not played " + SHALL_NOT_BE_NAMED + " to my knowledge. Good on them!").queue();
        } else {
            var isCurrentlyPlaying = currentTimeTracker.containsKey(user.getIdLong());
            var convertedTime = getHoursMinutesSeconds(isCurrentlyPlaying
                ? time + System.currentTimeMillis() / 1000 - currentTimeTracker.getOrDefault(user.getIdLong(), 0L)
                : time
            );
            event.reply(user + " has played " + SHALL_NOT_BE_NAMED + " for "
                + convertedTime.getFirst() + " hours, "
                + convertedTime.getSecond() + " minutes, "
                + convertedTime.getThird() + " seconds"
                + (isCurrentlyPlaying ? ", and is currently playing L**gue!" : ". SAD!")
            ).queue();
        }
    }

    private Triple<Integer, Integer, Integer> getHoursMinutesSeconds(double time) {
        var hours = (int) (time / 3600);
        var minutes = (int) ((time - hours * 3600) / 60);
        var seconds = (int) (time - hours * 3600 - minutes * 60);
        return new Triple<>(hours, minutes, seconds);
    }

    private void addEntryToTracker(Map<Long, Long> tracker, Pair<Long, Long> entry) {
        tracker.put(entry.getFirst(), entry.getSecond());
        DATABASE.commit();
    }

    private Long removeEntryFromTracker(Map<Long, Long> tracker, Long entryKey) {
        var entryValue = tracker.remove(entryKey);
        DATABASE.commit();
        return entryValue;
    }

    @Override
    public void onUserActivityStart(UserActivityStartEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        if (event.getNewActivity().getName().equals(SHALL_NOT_BE_NAMED)) {
            this.addEntryToTracker(currentTimeTracker, new Pair<>(event.getUser().getIdLong(), System.currentTimeMillis() / 1000));
        }
    }

    @Override
    public void onUserActivityEnd(UserActivityEndEvent event) {
        var user = event.getUser();
        if (user.isBot()) {
            return;
        }
        if (event.getOldActivity().getName().equals(SHALL_NOT_BE_NAMED) && currentTimeTracker.containsKey(user.getIdLong())) {
            var lastTime = this.removeEntryFromTracker(currentTimeTracker, user.getIdLong());
            this.addEntryToTracker(judgmentTracker, new Pair<>(user.getIdLong(),
                judgmentTracker.getOrDefault(user.getIdLong(), 0L) + (System.currentTimeMillis() / 1000) - lastTime));
        }
    }
}