import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Triple;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.user.UserActivityEndEvent;
import net.dv8tion.jda.api.events.user.UserActivityStartEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.mapdb.DB;
import org.mapdb.DBMaker;

public class Application extends ListenerAdapter {
    public static final List<String> LEAGUE_REACTION_EMOJIS = List.of("U+1F92E", "U+1F913", "U+1F922");
    public static final String BRUH_FUNNY_0 = "https://media.discordapp.net/attachments/1089873652008362014/1089873701140439070/bruhfunny0.gif?width=622&height=622";
    public static final String SHALL_NOT_BE_NAMED = "League of Legends";
    public static final String KEYWORD = "~judge";

    private static final DB DATABASE = DBMaker.fileDB("guilty_sinners.mapdb").transactionEnable().make();
    private final Map<String, Double> judgmentTracker = new HashMap<>();
    private final Map<String, Double> currentTimeTracker = new HashMap<>();

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
        if (message.startsWith(KEYWORD)) {
            this.judgeUser(message, event);
        }
    }

    private void judgeUser(String message, MessageReceivedEvent event) {
        var user = message.substring(KEYWORD.length()).trim();
        if (user.isEmpty()) {
            event.getChannel().sendMessage("Must specify a user to judge: '" + KEYWORD + " [user]'").queue();
        } else {
            var time = this.judgmentTracker.getOrDefault(user, 0.0);
            if (time == 0.0) {
                event.getChannel().sendMessage(user + " has not played " + SHALL_NOT_BE_NAMED + " to my knowledge. Good on them!").queue();
            } else {
                var isCurrentlyPlaying = this.currentTimeTracker.containsKey(user);
                var convertedTime = this.getHoursMinutesSeconds(isCurrentlyPlaying
                    ? time + System.currentTimeMillis() / 1000.0 - this.currentTimeTracker.getOrDefault(user, 0.0)
                    : time
                );
                event.getChannel().sendMessage(user + " has played " + SHALL_NOT_BE_NAMED + " for "
                    + convertedTime.getFirst() + " hours, "
                    + convertedTime.getSecond() + " minutes, "
                    + convertedTime.getThird() + " seconds"
                    + (isCurrentlyPlaying ? ", and is currently playing L**gue!" : ". SAD!")
                ).queue();
            }
        }
    }

    private Triple<Integer, Integer, Integer> getHoursMinutesSeconds(double time) {
        var hours = (int) (time / 3600);
        var minutes = (int) ((time - hours * 3600) / 60);
        var seconds = (int) (time - hours * 3600 - minutes * 60);
        return new Triple<>(hours, minutes, seconds);
    }

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        var oldName = event.getOldName();
        var newName = event.getNewName();
        if (this.judgmentTracker.containsKey(oldName)) {
            this.judgmentTracker.put(newName, this.judgmentTracker.remove(oldName));
        }
        if (this.currentTimeTracker.containsKey(oldName)) {
            this.currentTimeTracker.put(newName, this.currentTimeTracker.remove(oldName));
        }
    }

    @Override
    public void onUserActivityStart(UserActivityStartEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        if (event.getNewActivity().getName().equals(SHALL_NOT_BE_NAMED)) {
            this.currentTimeTracker.put(event.getUser().getName(), System.currentTimeMillis() / 1000.0);
        }
    }

    @Override
    public void onUserActivityEnd(UserActivityEndEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        var lastTime = this.currentTimeTracker.remove(event.getUser().getName());
        this.judgmentTracker.put(event.getUser().getName(),
            this.judgmentTracker.getOrDefault(event.getUser().getName(), 0.0) + (System.currentTimeMillis() / 1000.0 - lastTime));
    }
}