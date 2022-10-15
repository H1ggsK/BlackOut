package com.example.addon.modules;

import com.example.addon.Addon;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityDestroyEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.apache.commons.lang3.time.StopWatch;

import javax.xml.crypto.dom.DOMCryptoContext;

/*
Made by OLEPOSSU / Raksamies
*/

public class GhostCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("Swing")
        .description("Swings.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> slow = sgGeneral.add(new BoolSetting.Builder()
        .name("Slow")
        .description("Slow.")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("Slow")
        .description("Slow.")
        .defaultValue(10)
        .range(0, 10000)
        .sliderMax(10000)
        .build()
    );
    protected BlockPos placePos;
    protected boolean canBreak;
    protected boolean canPlace;
    protected final StopWatch stopWatch = new StopWatch();

    public GhostCrystal() {
        super(Addon.CATEGORY, "GhostCrystal", "Breaks crystals automatically.");
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onInteract(InteractBlockEvent event) {
        if (mc.player != null) {
            BlockHitResult result = event.result;
            boolean holding = mc.player.isHolding(Items.END_CRYSTAL);
            if (holding) {
                if (stopWatch.isStopped()) {
                    stopWatch.start();
                }
                if (stopWatch.getTime() > delay.get()) {
                    canBreak = true;
                    placePos = new BlockPos(result.getBlockPos().getX(), result.getBlockPos().getY() + 1, result.getBlockPos().getZ());
                } else {
                    event.cancel();
                }
            }
        }
    }


    @EventHandler(priority = EventPriority.LOW)
    private void onSpawn(EntityAddedEvent event) {
        if (mc.player != null && event.entity instanceof EndCrystalEntity) {
            BlockPos position = event.entity.getBlockPos();
            if (placePos != null) {
                if (position.equals(placePos)) {
                    if (canBreak) {
                        canBreak = false;
                        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(event.entity, mc.player.isSneaking()));
                        if (swing.get()) {
                            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                        }
                    }
                }
            }
        }
    }
    private void onKill(EntityRemovedEvent event) {
        if (mc.player != null && event.entity instanceof EndCrystalEntity) {
            BlockPos position = event.entity.getBlockPos();
            if (placePos != null) {
                if (position.equals(placePos) && slow.get()) {
                    ChatUtils.sendMsg(Text.of("reset"));
                    stopWatch.reset();
                }
            }
        }
    }
}
