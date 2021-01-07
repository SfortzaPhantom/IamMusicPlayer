package red.felnull.imp.music;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.fml.network.PacketDistributor;
import red.felnull.imp.data.IMPWorldData;
import red.felnull.imp.music.resource.PlayMusic;
import red.felnull.imp.packet.MusicRingMessage;
import red.felnull.imp.packet.PacketHandler;
import red.felnull.otyacraftengine.api.ResponseSender;
import red.felnull.otyacraftengine.util.IKSGPlayerUtil;
import red.felnull.otyacraftengine.util.IKSGServerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class WorldMusicRinger {
    private final List<UUID> playingPlayers = new ArrayList<>();
    private final List<UUID> loadingPlayers = new ArrayList<>();
    private final List<UUID> loadWaitingPlayers = new ArrayList<>();
    private final List<UUID> waitingPlayers = new ArrayList<>();
    private final UUID uuid;
    private final ResourceLocation dimension;
    private final PlayMusic playMusic;
    private final IWorldRingWhether whether;
    private long lastUpdateTime;
    private long ringStartTime;
    private long ringStartElapsedTime;
    private boolean playWaitingPrev;
    private boolean playWaiting;
    private long waitTime;
    private boolean playing;
    private boolean ringin;

    public WorldMusicRinger(UUID uuid, ResourceLocation dimension, PlayMusic playMusic, IWorldRingWhether whether) {
        this.uuid = uuid;
        this.dimension = dimension;
        this.playMusic = playMusic;
        this.whether = whether;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public void play() {
        if (whether.canMusicPlay())
            whether.musicPlayed();

        ringStartElapsedTime = getCurrentMusicPlayPosition();

        if (IKSGServerUtil.getOnlinePlayers().stream().anyMatch(this::canListen)) {
            playWaitingPrev = true;
            playWaiting = true;
        } else {
            playing = true;
            ringin = true;
            ringStartTime = System.currentTimeMillis();
        }
    }

    public void stop() {
        pause();
        whether.musicStoped();
    }

    public long getCurrentMusicPlayPosition() {
        return whether.getCurrentMusicPlayPosition();
    }

    public void pause() {
        playing = false;
        ringin = false;
        playingPlayers.forEach(n -> {
            ResponseSender.sendToClient(n.toString(), IKSGServerUtil.getMinecraftServer(), IMPWorldData.WORLD_RINGD, 1, uuid.toString(), new CompoundNBT());
        });
        playingPlayers.clear();
        loadingPlayers.clear();
        loadWaitingPlayers.clear();
        waitingPlayers.clear();
    }


    public boolean tick() {

        if (playMusic == null) {
            stop();
        }

        if (!whether.canMusicPlay() || playMusic == null)
            return true;

        if (playWaiting)
            waitTime += System.currentTimeMillis() - lastUpdateTime;
        else
            waitTime = 0;

        Stream<ServerPlayerEntity> listenPlayers = IKSGServerUtil.getOnlinePlayers().stream().filter(this::canListen);

        if (playWaiting && playWaitingPrev) {
            listenPlayers.filter(n -> !(loadingPlayers.contains(UUID.fromString(IKSGPlayerUtil.getUUID(n))) || loadWaitingPlayers.contains(UUID.fromString(IKSGPlayerUtil.getUUID(n))) || playingPlayers.contains(UUID.fromString(IKSGPlayerUtil.getUUID(n))) || waitingPlayers.contains(UUID.fromString(IKSGPlayerUtil.getUUID(n))))).forEach(n -> {
                loadingPlayers.add(UUID.fromString(IKSGPlayerUtil.getUUID(n)));
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> n), new MusicRingMessage(uuid, getMusicPos(), getPlayMusic(), getCurrentMusicPlayPosition()));
                playWaitingPrev = false;
            });
        } else if (playWaiting && loadingPlayers.isEmpty()) {
            loadWaitingPlayers.forEach(n -> {
                ResponseSender.sendToClient(n.toString(), IKSGServerUtil.getMinecraftServer(), IMPWorldData.WORLD_RINGD, 0, uuid.toString(), new CompoundNBT());
            });
            playingPlayers.addAll(loadWaitingPlayers);
            playWaiting = false;
            ringin = true;
            ringStartTime = System.currentTimeMillis();
        }

        if (ringin) {
            long cur = ringStartElapsedTime + System.currentTimeMillis() - ringStartTime;
            if (cur >= Long.MAX_VALUE) {
                stop();
                whether.setCurrentMusicPlayPosition(0);
            } else {
                whether.setCurrentMusicPlayPosition(cur);
            }
        }

        lastUpdateTime = System.currentTimeMillis();

        return false;
    }

    public PlayMusic getPlayMusic() {
        return playMusic;
    }

    public Vector3d getMusicPos() {
        return whether.getMusicPos();
    }

    public float getMusicVolume() {
        return whether.getMusicVolume();
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    public float getListenRange() {

        return 30f * getMusicVolume();
    }

    private boolean canListen(ServerPlayerEntity player) {
        return player.world.getDimensionKey().getLocation().equals(getDimension()) && Math.sqrt(player.getDistanceSq(getMusicPos())) <= getListenRange();
    }

    public void musicLoadingFinish(UUID playerUUID) {
        loadingPlayers.remove(playerUUID);
        loadWaitingPlayers.add(playerUUID);
    }
}
