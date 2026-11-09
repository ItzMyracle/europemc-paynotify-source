package com.europemc.paynotify;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalDouble;

public class PayNotifyMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("europemc_paynotify");
    public static PayConfig config;

    @Override
    public void onInitializeClient() {
        config = PayConfig.load();

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.of("europemc_paynotify", "toast"), new PayToastHud());

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handle(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> handle(message.getString()));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("paynotify")
                .then(ClientCommandManager.literal("min")
                    .then(ClientCommandManager.argument("amount", StringArgumentType.word())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "amount");
                            OptionalDouble v = PaymentParser.parseAmount(raw);
                            if (v.isEmpty()) {
                                ctx.getSource().sendFeedback(Text.literal(
                                    "Invalid amount. Examples: 5000, 250k, 1.5M, 2B, 1T").formatted(Formatting.RED));
                                return 0;
                            }
                            config.minAmount = v.getAsDouble();
                            config.save();
                            ctx.getSource().sendFeedback(Text.literal("Minimum payment for notification: "
                                + AmountFormat.abbreviate(config.minAmount)).formatted(Formatting.GREEN));
                            return 1;
                        })))
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
                        "EuropeMC Pay Notifier - " + (config.enabled ? "ON" : "OFF")
                        + " | min: " + AmountFormat.abbreviate(config.minAmount)
                        + "\n/paynotify min <amount e.g. 250k, 5M, 1B, 1T> | toggle | sound <true/false>"
                        + " | debug <true/false> | reload | test [amount]"));
                    return 1;
                })));
    }

    private static void handle(String raw) {
        if (config == null) return;
        if (config.debug && raw.toUpperCase().contains(config.prefix.toUpperCase())) {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                        Text.literal("[PayNotify debug] " + raw.replace('§', '&')).formatted(Formatting.GRAY), false);
                }
            });
        }
        if (!config.enabled) return;

        OptionalDouble amount = PaymentParser.parseIncoming(raw, config);
        if (amount.isPresent() && amount.getAsDouble() >= config.minAmount) {
            notifyPayment(amount.getAsDouble());
        }
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
