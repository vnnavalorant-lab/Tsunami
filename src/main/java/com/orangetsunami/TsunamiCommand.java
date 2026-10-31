package com.orangetsunami;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class TsunamiCommand {
    public static void register(CommandDispatcher<ServerCommandSource> d, CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        d.register(CommandManager.literal("tsunami")
            .requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.literal("start").executes(c -> {
                boolean ok = TsunamiManager.start(c.getSource().getServer());
                c.getSource().sendFeedback(() -> Text.literal(ok ? "Orange Tsunami iniciado!" : "Já está em andamento."), true);
                return ok ? 1 : 0;
            }))
            .then(CommandManager.literal("skip").executes(c -> {
                boolean ok = TsunamiManager.skip(c.getSource().getServer());
                c.getSource().sendFeedback(() -> Text.literal(ok ? "Fase pulada." : "Nenhuma partida em andamento."), true);
                return ok ? 1 : 0;
            }))
            .then(CommandManager.literal("stop").executes(c -> {
                TsunamiManager.stop(c.getSource().getServer());
                c.getSource().sendFeedback(() -> Text.literal("Orange Tsunami parado."), true);
                return 1;
            })));
    }
}
