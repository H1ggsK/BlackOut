package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.timers.BlockTimerList;
import kassuk.addon.blackout.managers.Managers;
import kassuk.addon.blackout.utils.SettingUtils;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.SafeWalk;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/*
Made by KassuK
Updated by OLEPOSSU
*/

public class ScaffoldPlus extends Module {
    public ScaffoldPlus() {
        super(BlackOut.BLACKOUT, "Scaffold+", "KasumsSoft blockwalk");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Boolean> sSprint = sgGeneral.add(new BoolSetting.Builder()
        .name("StopSprint")
        .description("Stops you from sprinting")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> useTimer = sgGeneral.add(new BoolSetting.Builder()
        .name("Use timer")
        .description("Should we use timer")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> timer = sgGeneral.add(new DoubleSetting.Builder()
        .visible(useTimer::get)
        .name("Timer")
        .description("Speed but better")
        .defaultValue(1.088)
        .min(0)
        .sliderMax(10)
        .build()
    );
    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("Blocks")
        .description("Blocks to use.")
        .build()
    );
    private final Setting<Double> placeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("Place Delay")
        .description(".")
        .defaultValue(0.1)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );
    private final Setting<Integer> places = sgGeneral.add(new IntSetting.Builder()
        .name("Places")
        .description("Blocks placed per place")
        .defaultValue(1)
        .range(1, 10)
        .sliderRange(1, 10)
        .build()
    );
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("Delay")
        .description("Delay.")
        .defaultValue(0.3)
        .range(0, 5)
        .sliderRange(0, 5)
        .build()
    );
    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("Silent")
        .description("Places even when not holding blocks")
        .defaultValue(true)
        .build()
    );
    private final Setting<Integer> extrapolation = sgGeneral.add(new IntSetting.Builder()
        .name("Extrapolation")
        .description("Predicts movement.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );
    private final Setting<Boolean> safeWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("SafeWalk")
        .description("Should SafeWalk be used")
        .defaultValue(true)
        .build()
    );

    BlockTimerList timers = new BlockTimerList();
    Vec3d motion = null;
    double placeTimer;
    int placesLeft = 0;

    @Override
    public void onDeactivate() {
        placeTimer = 0;
        placesLeft = places.get();
        Modules.get().get(Timer.class).setOverride(1);
        if (Modules.get().get(SafeWalk.class).isActive()) {
            Modules.get().get(SafeWalk.class).toggle();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        placeTimer = Math.min(placeDelay.get(), placeTimer + event.frameTime);
        if (placeTimer >= placeDelay.get()) {
            placesLeft = places.get();
            placeTimer = 0;
        }
    }

    @EventHandler
    private void onMove(PlayerMoveEvent event) {
        if (mc.player != null && mc.world != null) {
            int[] obsidian = findBlocks();
            if (obsidian[1] > 0 && (silent.get() || validItem(Managers.HOLDING.getStack().getItem()))) {
                if (safeWalk.get() && !Modules.get().get(SafeWalk.class).isActive()) {
                    Modules.get().get(SafeWalk.class).toggle();
                }
                motion = event.movement;
                if (sSprint.get()) mc.player.setSprinting(false);
                if (useTimer.get()) Modules.get().get(Timer.class).setOverride(timer.get());
                List<BlockPos> placements = getBlocks();
                if (!placements.isEmpty()) {
                    boolean swapped = false;
                    if (!Managers.HOLDING.isHolding(Items.OBSIDIAN) && silent.get()) {
                        InvUtils.swap(obsidian[0], true);
                        swapped = true;
                    }
                    int p = Math.min(Math.min(obsidian[1], placesLeft), placements.size());
                    for (int i = 0; i < p; i++) {
                        BlockPos toPlace = placements.get(i);
                        Direction[] result = SettingUtils.getPlaceDirection(toPlace);
                        if (result[0] != null || result[1] != null) {
                            place(toPlace, result);
                        }
                    }
                    if (swapped) {
                        InvUtils.swapBack();
                    }
                }
            } else {
                if (safeWalk.get() && Modules.get().get(SafeWalk.class).isActive()) {
                    Modules.get().get(SafeWalk.class).toggle();
                }
            }
        }
    }

    boolean validItem(Item item) {
        return item instanceof BlockItem && blocks.get().contains(((BlockItem) item).getBlock());
    }

    int[] findBlocks() {
        int num = 0;
        int slot = 0;
        if (mc.player != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (validItem(stack.getItem())) {
                    num = stack.getCount();
                    slot = i;
                }
            }
        }
        return new int[] {slot, num};
    }

    List<BlockPos> getBlocks() {
        List<BlockPos> list = new ArrayList<>();
        double x = motion.x;
        double z = motion.z;
        Vec3d vec = mc.player.getPos();
        for (int i = 0; i < extrapolation.get(); i++) {
            vec = vec.add(x, 0, z);
            if (inside(getBox(vec))) {
                break;
            } else {
                addBlocks(list, vec);
            }
        }
        return list;
    }

    void addBlocks(List<BlockPos> list, Vec3d vec) {
        BlockPos pos = new BlockPos(Math.floor(vec.x), Math.floor(vec.y), Math.floor(vec.z)).down();
        if (!timers.contains(pos) && air(pos) && !list.contains(pos)) {
            list.add(pos);
        }
    }

    Box getBox(Vec3d vec) {
        Box box = mc.player.getBoundingBox();
        return new Box(vec.x - 0.3, vec.y, vec.z - 0.3, vec.x + 0.3, vec.y + (box.maxY - box.minY), vec.z + 0.3);
    }

    boolean inside(Box bb) {
        return mc.world.getBlockCollisions(mc.player, bb).iterator().hasNext();
    }

    boolean air(BlockPos pos) {return mc.world.getBlockState(pos).getBlock().equals(Blocks.AIR);}

    void place(BlockPos toPlace, Direction[] result) {
        timers.add(toPlace, delay.get());
        placesLeft--;
        if (result[1] != null) {
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                new BlockHitResult(new Vec3d(toPlace.getX() + 0.5, toPlace.getY() + 0.5, toPlace.getZ() + 0.5),
                    result[1], toPlace, false), 0));
        } else {
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND,
                new BlockHitResult(new Vec3d(toPlace.offset(result[0]).getX() + 0.5, toPlace.offset(result[0]).getY() + 0.5, toPlace.offset(result[0]).getZ() + 0.5),
                    result[0].getOpposite(), toPlace.offset(result[0]), false), 0));
        }
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }
}
