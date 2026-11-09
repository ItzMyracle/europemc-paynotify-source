package com.europemc.paynotify;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.OptionalDouble;

public class PayNotifyMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("europemc_paynotify");
    public static PayConfig config;

    private static final SystemToast.Type TOAST_TYPE = new SystemToast.Type(6000L);
    private static final DecimalFormat FMT = new DecimalFormat("#,##0.##");

    @Override
    public void onInitializeClient() {
        config = PayConfig.load();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handle(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) -> handle(message.getString()));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("paynotify")
                .then(ClientCommandManager.literal("min")
                    .then(ClientCommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(ctx -> {
                            config.minAmount = DoubleArgumentType.getDouble(ctx, "amount");
                            config.save();
                            ctx.getSource().sendFeedback(Text.literal("Minimum payment for notification: "
                                + FMT.format(config.minAmount)).formatted(Formatting.GREEN));
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
                .then(ClientCommandManager.literal("test").executes(ctx -> {
                    notifyPayment(config.minAmount);
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(
                        "EuropeMC Pay Notifier - " + (config.enabled ? "ON" : "OFF")
                        + " | min: " + FMT.format(config.minAmount)
                        + "\n/paynotify min <amount> | toggle | sound <true/false> | debug <true/false> | reload | test"));
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
            SystemToast.add(mc.getToastManager(), TOAST_TYPE,
                Text.literal("💰 Payment received!").formatted(Formatting.GOLD),
                Text.literal("+" + config.currencySymbol + FMT.format(amount)).formatted(Formatting.GREEN));
            if (config.playSound && mc.player != null) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        });
    }
}
