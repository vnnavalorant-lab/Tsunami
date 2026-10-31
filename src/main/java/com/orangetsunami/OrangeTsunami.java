package com.orangetsunami;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.util.Identifier;

public class OrangeTsunami implements ModInitializer {
    public static final String MOD_ID = "orangetsunami";
    public static final Identifier SYNC = new Identifier(MOD_ID, "sync");

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(TsunamiCommand::register);
        ServerTickEvents.END_SERVER_TICK.register(TsunamiManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(TsunamiManager::stop);
    }
}
