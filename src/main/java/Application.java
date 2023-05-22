import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import kotlin.Pair;
import kotlin.Triple;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateActivitiesEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application extends ListenerAdapter {
    public static final List<String> LEAGUE_REACTION_EMOJIS = List.of("🤮", "🤓", "🤢");
    public static final String BRUH_FUNNY_0 = "https://media.discordapp.net/attachments/1089873652008362014/1089873701140439070/bruhfunny0.gif?width=622&height=622";
    public static final String BRUH_FUNNY_1 = "https://media.discordapp.net/attachments/1089873652008362014/1089875840642326538/bruhfunny1.gif?width=622&height=622";
    public static final String SHALL_NOT_BE_NAMED = "League of Legends";
    public static final String KEYWORD = "judgment";
    public static final String SET_ALARM = "set_alarm";
    public static final String SET_CHANNEL = "set_channel";

    public static final Logger LOGGER = LoggerFactory.getLogger("Anti League Bot");

    public static final DB DATABASE = DBMaker.fileDB("guilty_sinners.mapdb")
        .transactionEnable()
        .closeOnJvmShutdown()
        .make();
    public static final Map<Long, Long> judgmentTracker = DATABASE.hashMap("judgmentTracker")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();
    public static final Map<Long, Long> currentTimeTracker = DATABASE.hashMap("currentTracker")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();
    public static final Map<Long, Boolean> alarmPermissions = DATABASE.hashMap("alarmPermissions")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.BOOLEAN)
        .createOrOpen();
    public static final Map<Long, Long> alarmChannels = DATABASE.hashMap("alarmChannels")
        .keySerializer(Serializer.LONG)
        .valueSerializer(Serializer.LONG)
        .createOrOpen();

    public static void main(String[] args) {
        var jda = JDABuilder.createDefault(args[0])
            .addEventListeners(new Application())
            .enableIntents(
                GatewayIntent.MESSAGE_CONTENT,
                GatewayIntent.GUILD_MESSAGE_REACTIONS,
                GatewayIntent.GUILD_PRESENCES
            ).enableCache(
                CacheFlag.ACTIVITY,
                CacheFlag.ONLINE_STATUS
            ).setMemberCachePolicy(MemberCachePolicy.ONLINE)
            .build();
        registerCommands(jda);
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        /*if (event.getAuthor().isBot()) {
            return;
        }*/
        // will be reworked in the future. stay tuned.
        /*var message = event.getMessage().getContentRaw();
        if (message.toLowerCase().contains(SHALL_NOT_BE_NAMED.toLowerCase())) {
            for (String emojiCode : LEAGUE_REACTION_EMOJIS) {
                event.getChannel().addReactionById(event.getMessageId(), Emoji.fromUnicode(emojiCode)).queue();
            }
            event.getMessage().reply(BRUH_FUNNY_0).queue();
        }*/
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case KEYWORD -> {
                OptionMapping username = event.getOption("user");
                judgeUser(username.getAsUser(), event);
            }
            case SET_ALARM -> {
                var guildID = event.getGuild().getIdLong();
                var currentPermission = event.getOption("switch").getAsBoolean();
                this.addEntryToTracker(alarmPermissions, new Pair<>(guildID, currentPermission));
                event.reply("Anti-League Alarm is now " + (currentPermission ? "" : "DIS") + "ARMED!").queue();
            }
            case SET_CHANNEL -> {
                var guildID = event.getGuild().getIdLong();
                var channel = event.getOption("channel").getAsChannel();
                if (channel.getType() != ChannelType.TEXT) {
                    event.reply("Must specify a text channel!").queue();
                } else {
                    var channelID = channel.getIdLong();
                    this.addEntryToTracker(alarmChannels, new Pair<>(guildID, channelID));
                    event.reply("Anti-League Alarm will now warn in channel: " + event.getGuild().getTextChannelById(channelID).getName()).queue();
                }
            }
        }
    }

    private void judgeUser(User user, SlashCommandInteractionEvent event) {
        var permaTime = judgmentTracker.getOrDefault(user.getIdLong(), 0L);
        var currentTime = currentTimeTracker.getOrDefault(user.getIdLong(), 0L);
        if (permaTime + currentTime == 0L) {
            event.reply(user.getName() + " has not played " + SHALL_NOT_BE_NAMED + ", to my knowledge. Good!").queue();
        } else {
            var isCurrentlyPlaying = currentTimeTracker.containsKey(user.getIdLong());
            var convertedTime = getHoursMinutesSeconds(isCurrentlyPlaying
                ? permaTime + AntiLeagueHelper.getSysTimeInSecondsLong() - currentTime
                : permaTime
            );
            event.reply(user.getName() + " has played " + SHALL_NOT_BE_NAMED + " for "
                + convertedTime.getFirst() + " hours, "
                + convertedTime.getSecond() + " minutes, "
                + convertedTime.getThird() + " seconds"
                + (isCurrentlyPlaying ? ", and is currently playing L**gue!" : ". SAD!")
            ).queue();
            event.getChannel().sendMessage(isCurrentlyPlaying ? BRUH_FUNNY_1 : BRUH_FUNNY_0).queue();
        }
    }

    private Triple<Integer, Integer, Integer> getHoursMinutesSeconds(double time) {
        var hours = (int) (time / 3600);
        var minutes = (int) ((time - hours * 3600) / 60);
        var seconds = (int) (time - hours * 3600 - minutes * 60);
        return new Triple<>(hours, minutes, seconds);
    }

    private static void registerCommands(JDA jda) {
        var userOption = new OptionData(OptionType.USER, "user", "Pass judgment upon this user", true);
        var channelOption = new OptionData(OptionType.CHANNEL, "channel", "Alarm will send warnings to this channel", true);
        var switchOption = new OptionData(OptionType.BOOLEAN, "switch", "Should the alarm send warnings?", true);

        var judgmentCommand = Commands.slash(KEYWORD, "Judge a user's League habits")
            .setGuildOnly(true)
            .addOptions(userOption);
        var setChannelCommand = Commands.slash(SET_CHANNEL, "Set the channel for the alarm to warn in")
            .setGuildOnly(true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(
                Permission.MANAGE_CHANNEL
            ))
            .addOptions(channelOption);
        var alarmCommand = Commands.slash(SET_ALARM, "Toggle an alarm that warns when a user starts playing League")
            .setGuildOnly(true)
            .setDefaultPermissions(DefaultMemberPermissions.enabledFor(
                Permission.MANAGE_CHANNEL
            ))
            .addOptions(switchOption);
        jda.updateCommands().addCommands(judgmentCommand, alarmCommand, setChannelCommand).queue();
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void onUserUpdateActivities(@NotNull UserUpdateActivitiesEvent event) {
        var user = event.getUser();
        if (user.isBot()) {
            return;
        }
        var prevActivities = event.getOldValue();
        var newActivities = event.getNewValue();
        var hasPlayedLeague = prevActivities != null && prevActivities.contains(Activity.playing(SHALL_NOT_BE_NAMED));
        var isPlayingLeague = newActivities != null && newActivities.contains(Activity.playing(SHALL_NOT_BE_NAMED));

        if (isPlayingLeague && !hasPlayedLeague) {
            LOGGER.info(user.getName() + user.getDiscriminator() + " has started playing " + SHALL_NOT_BE_NAMED);
            this.addEntryToTracker(currentTimeTracker, new Pair<>(user.getIdLong(), AntiLeagueHelper.getSysTimeInSecondsLong()));
            if (alarmPermissions.getOrDefault(event.getGuild().getIdLong(), false)) {
                var channelID = alarmChannels.getOrDefault(event.getGuild().getIdLong(), 0L);
                if (!channelID.equals(0L)) {
                    event.getGuild().getTextChannelById(channelID).sendMessage(user.getName() + " has started playing L**gue!").queue();
                    event.getGuild().getTextChannelById(channelID).sendMessage(BRUH_FUNNY_1).queue();
                }
            }
        }
        else if (!isPlayingLeague && hasPlayedLeague) {
            LOGGER.info(user.getName() + user.getDiscriminator() + " has stopped playing " + SHALL_NOT_BE_NAMED);
            var lastTime = this.removeEntryFromTracker(currentTimeTracker, user.getIdLong());
            this.addEntryToTracker(judgmentTracker, new Pair<>(user.getIdLong(),
                judgmentTracker.getOrDefault(user.getIdLong(), 0L) + AntiLeagueHelper.getSysTimeInSecondsLong() - lastTime));
        }
    }

    private <K, V> void addEntryToTracker(Map<K, V> tracker, Pair<K, V> entry) {
        tracker.put(entry.getFirst(), entry.getSecond());
        DATABASE.commit();
    }

    private <K, V> V removeEntryFromTracker(Map<K, V> tracker, K entryKey) {
        var entryValue = tracker.remove(entryKey);
        DATABASE.commit();
        return entryValue;
    }
}