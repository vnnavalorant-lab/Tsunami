package com.orangetsunami.client;

import com.orangetsunami.OrangeTsunami;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class OrangeTsunamiClient implements ClientModInitializer {
    private static final String[] NAMES = {"BUILDING PHASE", "FLOODING PHASE", "INTERMISSION PHASE"};
    private static final String TITLE = "ORANGE TSUNAMI";
    private static final int ORANGE = 0xFFFF9F1C;
    private static final int BG = 0xB4141414;
    private static final float SCALE = 1.5f;

    private static int phase = -1;
    private static long endMs;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OrangeTsunami.SYNC, (client, handler, buf, sender) -> {
            int p = buf.readInt();
            int ticks = buf.readInt();
            client.execute(() -> {
                phase = p;
                endMs = System.currentTimeMillis() + ticks * 50L;
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> phase = -1);
        HudRenderCallback.EVENT.register(OrangeTsunamiClient::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        if (phase < 0 || phase >= NAMES.length) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        String name = NAMES[phase];
        boolean showTimer = phase != 2; // na Intermission a barra não mostra contador
        int w = Math.max(tr.getWidth(TITLE), tr.getWidth(name)) + 16;
        int h = showTimer ? 41 : 29;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(mc.getWindow().getScaledWidth() / 2f, 4, 0);
        ctx.getMatrices().scale(SCALE, SCALE, 1f);

        ctx.fill(-w / 2, 0, w / 2, h, BG);
        ctx.fill(-w / 2, 0, w / 2, 2, ORANGE);

        ctx.drawCenteredTextWithShadow(tr, TITLE, 0, 5, ORANGE);
        ctx.drawCenteredTextWithShadow(tr, name, 0, 17, 0xFFFFFFFF);
        if (showTimer) {
            int secs = (int) Math.max(0, Math.ceil((endMs - System.currentTimeMillis()) / 1000.0));
            ctx.drawCenteredTextWithShadow(tr, String.format("%02d:%02d", secs / 60, secs % 60), 0, 29, 0xFFFFFFFF);
        }
        ctx.getMatrices().pop();
    }
}
