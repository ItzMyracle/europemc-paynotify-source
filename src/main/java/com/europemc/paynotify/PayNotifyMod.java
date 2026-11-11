package com.europemc.paynotify;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;
import java.util.function.Consumer;

public class PayNotifyMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("europemc_paynotify");
    public static PayConfig config;
    private static KeyBinding openKey;
    private static String lastRaw = "";
    private static long lastRawTime;

    @Override
    public void onInitializeClient() {
        config = PayConfig.load();
        Tracker.load();

        // keybind: B opens the tracker (changeable in Controls)
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("europemc_paynotify", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.europemc_paynotify.open_tracker", GLFW.GLFW_KEY_B, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new TrackerScreen());
            }
            Tracker.tick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Tracker.save());

        // HUD
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of("europemc_paynotify", "toast"), new PayToastHud());
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of("europemc_paynotify", "corner"), new HudOverlays.Corner());
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of("europemc_paynotify", "hotbar_bar"), new HudOverlays.HotbarBar());

        // chat
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handle(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> handle(message.getString()));

        // commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("paynotify")
                .then(amountCmd("min", v -> config.minAmount = v, "Pop-up minimum"))
                .then(amountCmd("betmin", v -> config.betMin = v, "Minimum bet"))
                .then(amountCmd("betmax", v -> config.betMax = v, "Maximum bet (0 = no limit)"))
                .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                    config.enabled = !config.enabled;
                    config.save();
                    ctx.getSource().sendFeedback(Text.literal("Pay notifications: " + (config.enabled ? "ON" : "OFF")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("sound")
                    .then(ClientCommandManager.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                        config.playSound = BoolArgumentType.getBool(ctx, "on");
                        config.save();
                        ctx.getSource().sendFeedback(Text.literal("Sound: " + (config.playSound ? "ON" : "OFF")));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("debug")
                    .then(ClientCommandManager.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                        config.debug = BoolArgumentType.getBool(ctx, "on");
                        config.save();
                        ctx.getSource().sendFeedback(Text.literal("Debug: " + (config.debug ? "ON" : "OFF")));
                        return 1;
                    })))
                .then(ClientCommandManager.literal("reload").executes(ctx -> {
                    config = PayConfig.load();
                    Tracker.invalidate();
                    ctx.getSource().sendFeedback(Text.literal("Config reloaded."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("test")
                    .executes(ctx -> {
                        notifyPayment(config.minAmount);
                        return 1;
                    })
                    .then(ClientCommandManager.argument("amount", StringArgumentType.word())
                        .executes(ctx -> {
                            OptionalDouble v = PaymentParser.parseAmount(StringArgumentType.getString(ctx, "amount"));
                            if (v.isEmpty()) {
                                ctx.getSource().sendFeedback(Text.literal("Invalid amount. Try: /paynotify test 2.5B")
                                    .formatted(Formatting.RED));
                                return 0;
                            }
                            notifyPayment(v.getAsDouble());
                            return 1;
                        })))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(
                        "EuropeMC Pay Notifier - press B for the Gamble Tracker"
                        + "\nPop-ups " + (config.enabled ? "ON" : "OFF") + " from " + AmountFormat.abbreviate(config.minAmount)
                        + " | bets: min " + AmountFormat.abbreviate(config.betMin)
                        + ", max " + (config.betMax <= 0 ? "none" : AmountFormat.abbreviate(config.betMax))
                        + "\n/paynotify min | betmin | betmax <250k, 5M, 1B, 1T> | toggle | sound <true/false>"
                        + " | debug <true/false> | reload | test [amount]"));
                    return 1;
                })));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> amountCmd(String name, Consumer<Double> setter, String label) {
        return ClientCommandManager.literal(name)
            .then(ClientCommandManager.argument("amount", StringArgumentType.word())
                .executes(ctx -> {
                    OptionalDouble v = PaymentParser.parseAmount(StringArgumentType.getString(ctx, "amount"));
                    if (v.isEmpty()) {
                        ctx.getSource().sendFeedback(Text.literal(
                            "Invalid amount. Examples: 5000, 250k, 1.5M, 2B, 1T").formatted(Formatting.RED));
                        return 0;
                    }
                    setter.accept(v.getAsDouble());
                    config.save();
                    Tracker.invalidate();
                    ctx.getSource().sendFeedback(Text.literal(label + ": " + AmountFormat.abbreviate(v.getAsDouble()))
                        .formatted(Formatting.GREEN));
                    return 1;
                }));
    }

    private static void handle(String raw) {
        if (config == null) return;

        // the same line can reach us twice (chat + game event): ignore exact repeats within 150 ms
        long now = System.currentTimeMillis();
        if (raw.equals(lastRaw) && now - lastRawTime < 150) return;
        lastRaw = raw;
        lastRawTime = now;

        if (config.debug && raw.toUpperCase().contains(config.prefix.toUpperCase())) {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("[PayNotify debug] " + raw.replace('\u00a7', '&')).formatted(Formatting.GRAY), false);
                }
            });
        }

        TxParser.Parsed p = TxParser.parse(raw, config);
        if (p == null) return;

        String flag = null;
        if (config.trackingEnabled) {
            Tracker.record(p, config);
            flag = Tracker.classify(p, config);
        }
        if (flag != null && config.flagAlerts) {
            notifyFlag(p, flag);
            return;
        }
        if (p.incoming() && config.enabled && p.amount() >= config.minAmount) {
            notifyPayment(p.amount());
        }
    }

    private static void notifyFlag(TxParser.Parsed p, String flag) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            String name = p.player().length() > 10 ? p.player().substring(0, 10) : p.player();
            boolean under = flag.equals("UNDER");
            PayToastHud.pushFlag((under ? "UNDER MIN: " : "OVER MAX: ") + name, under ? 0xFB923C : 0xF87171,
                    p.amount(), config.currencySymbol, (long) (config.durationSeconds * 1000));
            if (config.playSound && mc.player != null) {
                mc.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        });
    }

    private static void notifyPayment(double amount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            PayToastHud.push(amount, config.currencySymbol, (long) (config.durationSeconds * 1000));
            if (config.playSound && mc.player != null) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                if (amount >= 1_000_000) mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                if (amount >= 1_000_000_000) mc.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            }
        });
    }
}
