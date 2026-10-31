package com.orangetsunami;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TsunamiManager {
    // ===== Ajustes =====
    public static final int RADIUS = 24;      // raio da área de construção (blocos)
    public static final int HEIGHT = 12;      // altura máxima da onda acima do Y inicial do player
    public static final int DEPTH = 3;        // quantos blocos abaixo do Y inicial a água também alcança
    public static final int STEP_TICKS = 10;  // a onda avança 1 bloco a cada 10 ticks (0,5s)

    public enum Phase {
        IDLE(-1, 0),
        BUILDING(0, 3 * 60),
        FLOODING(1, 2 * 60),
        INTERMISSION(2, 60);

        public final int id;
        public final int seconds;
        Phase(int id, int seconds) { this.id = id; this.seconds = seconds; }
    }

    private static Phase phase = Phase.IDLE;
    private static int ticksLeft;
    private static final Map<UUID, Arena> ARENAS = new HashMap<>();
    private static final Map<World, LongOpenHashSet> WAVES = new HashMap<>();

    public static boolean isWave(World world, BlockPos pos) {
        if (phase == Phase.IDLE) return false;
        LongOpenHashSet set = WAVES.get(world);
        return set != null && set.contains(pos.asLong());
    }

    public static boolean start(MinecraftServer server) {
        if (phase != Phase.IDLE) return false;
        ARENAS.clear();
        WAVES.clear();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ARENAS.put(p.getUuid(), new Arena(p.getServerWorld(), p.getBlockPos()));
            p.changeGameMode(GameMode.CREATIVE);
        }
        enter(server, Phase.BUILDING);
        return true;
    }

    public static boolean skip(MinecraftServer server) {
        if (phase == Phase.IDLE) return false;
        advance(server);
        return true;
    }

    public static void stop(MinecraftServer server) {
        if (phase == Phase.IDLE) return;
        for (Arena a : ARENAS.values()) a.restore();
        WAVES.clear();
        for (UUID id : ARENAS.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) p.changeGameMode(GameMode.SURVIVAL);
        }
        ARENAS.clear();
        phase = Phase.IDLE;
        ticksLeft = 0;
        sync(server);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE) return;
        ticksLeft--;
        if (phase == Phase.FLOODING && ticksLeft % STEP_TICKS == 0) {
            for (Arena a : ARENAS.values()) a.step();
        }
        if (ticksLeft <= 0) advance(server);
        else if (ticksLeft % 20 == 0) sync(server);
    }

    private static void advance(MinecraftServer server) {
        switch (phase) {
            case BUILDING -> enter(server, Phase.FLOODING);
            case FLOODING -> enter(server, Phase.INTERMISSION);
            case INTERMISSION -> stop(server); // ondas somem instantaneamente
            default -> { }
        }
    }

    private static void enter(MinecraftServer server, Phase next) {
        phase = next;
        ticksLeft = next.seconds * 20;
        if (next == Phase.FLOODING) {
            for (Map.Entry<UUID, Arena> e : ARENAS.entrySet()) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) p.changeGameMode(GameMode.SURVIVAL);
                e.getValue().begin();
            }
        }
        sync(server);
    }

    private static void sync(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(phase.id);
            buf.writeInt(ticksLeft);
            ServerPlayNetworking.send(p, OrangeTsunami.SYNC, buf);
        }
    }

    /** Área de um player: a água entra pelas bordas e avança para dentro, só preenchendo ar ligado ao lado de fora. */
    private static final class Arena {
        final ServerWorld world;
        final int minX, maxX, minY, maxY, minZ, maxZ;
        final Long2ObjectOpenHashMap<BlockState> original = new Long2ObjectOpenHashMap<>();
        final LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayList layer = new LongArrayList();

        Arena(ServerWorld world, BlockPos c) {
            this.world = world;
            minX = c.getX() - RADIUS; maxX = c.getX() + RADIUS;
            minZ = c.getZ() - RADIUS; maxZ = c.getZ() + RADIUS;
            minY = Math.max(world.getBottomY(), c.getY() - DEPTH);
            maxY = Math.min(world.getTopY() - 1, c.getY() + HEIGHT);
        }

        void begin() {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
                    for (int y = minY; y <= maxY; y++) {
                        long l = BlockPos.asLong(x, y, z);
                        if (visited.add(l)) layer.add(l);
                    }
                }
            }
            step();
        }

        void step() {
            if (layer.isEmpty()) return;
            LongOpenHashSet set = WAVES.computeIfAbsent(world, w -> new LongOpenHashSet());
            LongArrayList next = new LongArrayList();
            for (int i = 0; i < layer.size(); i++) {
                long l = layer.getLong(i);
                BlockPos pos = BlockPos.fromLong(l);
                if (!world.isChunkLoaded(pos)) continue;
                BlockState st = world.getBlockState(pos);
                if (st.isOf(Blocks.WATER)) {
                    // já é água: apenas deixa passar
                } else if (st.isAir() || st.isReplaceable()) {
                    original.put(l, st);
                    set.add(l);
                    world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                } else {
                    continue; // bloco sólido barra a água
                }
                for (Direction d : Direction.values()) {
                    BlockPos n = pos.offset(d);
                    if (n.getX() < minX || n.getX() > maxX || n.getZ() < minZ || n.getZ() > maxZ
                        || n.getY() < minY || n.getY() > maxY) continue;
                    long nl = n.asLong();
                    if (visited.add(nl)) next.add(nl);
                }
            }
            layer = next;
        }

        void restore() {
            LongOpenHashSet set = WAVES.get(world);
            for (Long2ObjectMap.Entry<BlockState> e : original.long2ObjectEntrySet()) {
                if (set != null) set.remove(e.getLongKey());
                world.setBlockState(BlockPos.fromLong(e.getLongKey()), e.getValue(),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
            original.clear();
            visited.clear();
            layer.clear();
        }
    }
}
