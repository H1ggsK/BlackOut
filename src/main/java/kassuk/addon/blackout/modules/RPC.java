package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import org.meteordev.starscript.Script;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author OLEPOSSU
 * TrueFaxx: more options + safer cycling
 */
public class RPC extends BlackOutModule {
    public RPC() {
        super(BlackOut.BLACKOUT, "RPC", "Epic rpc.");
    }

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgText     = settings.createGroup("Text");
    private final SettingGroup sgBehavior = settings.createGroup("Behavior");
    private final SettingGroup sgImages   = settings.createGroup("Images");

    private final Setting<String> appId = sgGeneral.add(new StringSetting.Builder()
        .name("app-id")
        .description("Discord application ID for the RPC. Needs a reload (toggle module) to apply.")
        .defaultValue("1038168991258136576")
        .build()
    );

    private final Setting<Integer> refreshDelay = sgGeneral.add(new IntSetting.Builder()
        .name("refresh-delay")
        .description("Ticks between refreshing.")
        .defaultValue(100)
        .range(0, 1000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Boolean> showTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("show-timestamp")
        .description("Shows how long you've been playing (elapsed timer).")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> l1 = sgText.add(new StringListSetting.Builder()
        .name("line-1")
        .description("Rotating Details line (Starscript).")
        .defaultValue("Playing on {server}", "{player}")
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<List<String>> l2 = sgText.add(new StringListSetting.Builder()
        .name("line-2")
        .description("Rotating State line (Starscript).")
        .defaultValue("{server.player_count} Players online", "{round(player.health, 1)}hp")
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<String> menuDetails = sgText.add(new StringSetting.Builder()
        .name("menu-details")
        .description("Details text when you're in main menu.")
        .defaultValue("In Main Menu")
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<String> menuState = sgText.add(new StringSetting.Builder()
        .name("menu-state")
        .description("State text when you're in main menu.")
        .defaultValue("Chilling")
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    // ---------- Behavior ----------
    public enum CycleMode { Sequential, Random }

    private final Setting<CycleMode> cycleMode = sgBehavior.add(new EnumSetting.Builder<CycleMode>()
        .name("cycle-mode")
        .description("How the lines rotate.")
        .defaultValue(CycleMode.Sequential)
        .build()
    );

    private final Setting<Boolean> syncLines = sgBehavior.add(new BoolSetting.Builder()
        .name("sync-lines")
        .description("If true, both lines advance together (paired).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> cycleInMenu = sgBehavior.add(new BoolSetting.Builder()
        .name("cycle-in-menu")
        .description("If true, line cycling continues even in main menu (using your line lists).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> resetIndicesOnEnable = sgBehavior.add(new BoolSetting.Builder()
        .name("reset-indices-on-enable")
        .description("Resets the rotation indices when the module is enabled.")
        .defaultValue(true)
        .build()
    );

    // ---------- Images ----------
    private final Setting<Boolean> largeImageEnabled = sgImages.add(new BoolSetting.Builder()
        .name("large-image-enabled")
        .description("Show large image in RPC.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> largeImageKey = sgImages.add(new StringSetting.Builder()
        .name("large-image-key")
        .description("Large image asset key (must exist in your Discord app assets).")
        .defaultValue("logo1")
        .visible(largeImageEnabled::get)
        .build()
    );

    private final Setting<String> largeImageText = sgImages.add(new StringSetting.Builder()
        .name("large-image-text")
        .description("Large image hover text (Starscript).")
        .defaultValue("v." + BlackOut.BLACKOUT_VERSION)
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(largeImageEnabled::get)
        .build()
    );

    private final Setting<Boolean> smallImageEnabled = sgImages.add(new BoolSetting.Builder()
        .name("small-image-enabled")
        .description("Show small image in RPC.")
        .defaultValue(false)
        .build()
    );

    private final Setting<String> smallImageKey = sgImages.add(new StringSetting.Builder()
        .name("small-image-key")
        .description("Small image asset key (must exist in your Discord app assets).")
        .defaultValue("logo_small")
        .visible(smallImageEnabled::get)
        .build()
    );

    private final Setting<String> smallImageText = sgImages.add(new StringSetting.Builder()
        .name("small-image-text")
        .description("Small image hover text (Starscript).")
        .defaultValue("{player}")
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(smallImageEnabled::get)
        .build()
    );

    // ---------- Runtime ----------
    private int ticks;
    private int index1;
    private int index2;

    private long startSeconds;
    private final Random rng = new Random();
    private static final RichPresence presence = new RichPresence();

    @Override
    public void onActivate() {
        long parsedId = parseAppId(appId.get());

        DiscordIPC.start(parsedId, null);

        startSeconds = System.currentTimeMillis() / 1000L;
        if (resetIndicesOnEnable.get()) {
            index1 = 0;
            index2 = 0;
        }

        updatePresence();
    }

    @Override
    public void onDeactivate() {
        DiscordIPC.stop();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent.Pre event) {
        if (ticks > 0) ticks--;
        else updatePresence();
    }

    private void updatePresence() {
        ticks = refreshDelay.get();

        boolean inGame = mc.player != null && mc.world != null;

        presence.setStart(showTimestamp.get() ? startSeconds : 0);

        if (!inGame && !cycleInMenu.get()) {
            presence.setDetails(runStarscript(menuDetails.get(), "In Main Menu"));
            presence.setState(runStarscript(menuState.get(), "In Main Menu"));
        } else {
            List<String> messages1 = buildMessages(l1.get(), "Playing Minecraft");
            List<String> messages2 = buildMessages(l2.get(), "Doing stuff");

            int next1 = pickNextIndex(index1, messages1.size(), cycleMode.get(), rng);
            int next2 = syncLines.get()
                ? next1 % messages2.size()
                : pickNextIndex(index2, messages2.size(), cycleMode.get(), rng);

            String d = messages1.get(index1);
            String s = messages2.get(index2);

            presence.setDetails(d);
            presence.setState(s);

            index1 = next1;
            index2 = next2;
        }

        if (largeImageEnabled.get()) {
            presence.setLargeImage(
                largeImageKey.get().trim(),
                runStarscript(largeImageText.get(), "BlackOut")
            );
        } else {
            presence.setLargeImage(null, null);
        }

        if (smallImageEnabled.get()) {
            presence.setSmallImage(
                smallImageKey.get().trim(),
                runStarscript(smallImageText.get(), "")
            );
        } else {
            presence.setSmallImage(null, null);
        }

        DiscordIPC.setActivity(presence);
    }

    private List<String> buildMessages(List<String> raw, String fallback) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            out.add(fallback);
            return out;
        }

        for (String msg : raw) {
            String rendered = runStarscript(msg, msg);
            if (rendered != null && !rendered.isBlank()) out.add(rendered);
        }

        if (out.isEmpty()) out.add(fallback);
        return out;
    }

    private String runStarscript(String input, String fallback) {
        if (input == null) return fallback;

        Script script = MeteorStarscript.compile(input);
        if (script == null) return fallback;

        String res = MeteorStarscript.run(script);
        if (res == null || res.isBlank()) return fallback;

        return res;
    }

    private int pickNextIndex(int current, int size, CycleMode mode, Random rng) {
        if (size <= 1) return 0;

        if (mode == CycleMode.Random) {
            int next = rng.nextInt(size);
            if (size > 1 && next == current) next = (next + 1) % size;
            return next;
        }

        // Sequential
        return (current + 1) % size;
    }

    private long parseAppId(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ignored) {
            // fallback to old hardcoded id if user types nonsense
            return 1038168991258136576L;
        }
    }
}
