package com.pockyl.handson_letsdo.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.satisfy.farm_and_charm.core.block.entity.CuttingBoardBlockEntity;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardAssemblyRecipe;
import net.satisfy.farm_and_charm.core.recipe.CuttingBoardRecipe;

import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.SlotStorage;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.api.station.StationContext;
import com.pockyl.diegesis.api.station.StationLogic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The board's rules (Hands-On Delight's board, brought to Farm &amp; Charm's): what lies there is cut once it had the strokes the board
 * counts for the tool, a batch of the stack at once, each item as the board's own cut makes it; a dish is put together as the board's
 * recipes accept it, and made as many times as its layers allow. What is made goes to the cook. Also runs on the clients around a
 * board nobody works at, to show what lies on it; there it only reads.
 */
public final class BoardLogic implements StationLogic {
    /** Ticks between two chops the server takes: a click as fast as a hand can go, not a macro. */
    private static final int CHOP_GAP = 3;
    /** As many marks as one cut shows at most: a tool that needs more needs no more strokes than this. */
    public static final int MOST_MARKS = 5;
    private static final float SPOT_FROM = 0.2F;
    private static final float SPOT_TO = 0.8F;
    private static final float SPOT_JITTER = 0.2F;
    /** How far a mark leans from square at most: the client leans less on a long item, never more. */
    public static final float MOST_LEAN = 35.0F * Mth.DEG_TO_RAD;

    private final FarmCharmBoard board;
    private int marks;
    /** How many marks the item lying there has, for the tool the first cook would cut it with. */
    private int planned = 1;
    private final float[] spots = new float[MOST_MARKS];
    private final float[] angles = new float[MOST_MARKS];
    private ItemStack cutting = ItemStack.EMPTY;
    private long lastChop = -CHOP_GAP;
    /** The dish the cook picked in the book: made at once when the layers make it. */
    private ResourceLocation picked;
    /** Experience earned by cuts and not yet whole, per cook: given as it grows to whole points. */
    private final java.util.Map<UUID, Float> earned = new java.util.HashMap<>();

    public BoardLogic(StationContext station) {
        board = FarmCharmBoard.of(station.blockEntity());
        station.state().bindSlots(CuttingBoardStation.BOARD, new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                return onBoard(station.level());
            }

            @Override
            public void set(int index, ItemStack stack) {
                setOnBoard(stack);
            }
        });
        station.state().bindSlots(CuttingBoardStation.LAYERS, new SlotStorage() {
            @Override
            public int size() {
                return CuttingBoardStation.MOST_LAYERS;
            }

            @Override
            public ItemStack get(int index) {
                List<ItemStack> layers = layers(station.level());
                return index < layers.size() ? layers.get(index) : ItemStack.EMPTY;
            }

            @Override
            public void set(int index, ItemStack stack) {
                List<ItemStack> layers = new ArrayList<>(layers(station.level()));
                if (index < layers.size()) {
                    layers.set(index, stack);
                } else if (!stack.isEmpty()) {
                    layers.add(stack);
                }
                board.setItems(layers);
            }
        });
        station.state().bindSlots(CuttingBoardStation.KNIFE, new SlotStorage() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public ItemStack get(int index) {
                return board.knife();
            }

            @Override
            public void set(int index, ItemStack stack) {
                board.setKnife(stack);
            }
        });
        restart(station);
    }

    /**
     * What lies to be cut: what the board's stuck tool was cutting, or the one stack lying there when the board cuts or strips it;
     * else nothing, and what lies there is the layers of a dish.
     */
    private ItemStack onBoard(Level level) {
        if (!board.pending().isEmpty()) {
            return board.pending();
        }
        List<ItemStack> items = board.items();
        return items.size() == 1 && FarmCharmBoard.work(level, items.get(0)) != FarmCharmBoard.Work.NONE ? items.get(0) : ItemStack.EMPTY;
    }

    /** The layers of the dish being put together, bottom first; none while something lies to be cut. */
    private List<ItemStack> layers(Level level) {
        return onBoard(level).isEmpty() ? board.items() : List.of();
    }

    /** The layers as the clients have them in the station's slots, bottom first. */
    public static List<ItemStack> layers(StationState state) {
        List<ItemStack> layers = new ArrayList<>();
        for (ItemStack layer : state.getItems(CuttingBoardStation.LAYERS)) {
            if (!layer.isEmpty()) {
                layers.add(layer);
            }
        }
        return layers;
    }

    /** What lies to be cut from now on, the board's one stack; the stuck tool's own cut is over. */
    private void setOnBoard(ItemStack stack) {
        if (!board.pending().isEmpty()) {
            board.clearPending();
        }
        board.setItems(stack.isEmpty() ? List.of() : List.of(stack));
    }

    @Override
    public boolean mayPlace(StationContext station, Player player, SlotRef slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayTake(StationContext station, Player player, SlotRef slot) {
        return slot.group().equals(CuttingBoardStation.BOARD);
    }

    @Override
    public void tick(StationContext station) {
        if (station.level().isClientSide()) {
            return;
        }
        ItemStack onBoard = onBoard(station.level());
        if (!ItemStack.isSameItemSameComponents(onBoard, cutting)) {
            // Something else lies there now: taken by the board's own use, or cut by someone without the station.
            cutting = onBoard.copyWithCount(1);
            restart(station);
        } else if (marks == 0 && !onBoard.isEmpty() && chopsFor(station, onBoard) != planned) {
            // The cook took another tool (a cleaver cuts in fewer strokes): the marks are laid for it.
            restart(station);
        }
        publish(station);
    }

    /** A cut the stuck tool was making when the cook came becomes what lies to be cut at the station, its strokes starting over. */
    @Override
    public void onOpen(StationContext station, Player player) {
        if (station.level().isClientSide() || board.pending().isEmpty()) {
            return;
        }
        ItemStack pending = board.pending().copy();
        board.clearPending();
        if (board.items().isEmpty()) {
            board.setItems(List.of(pending));
        } else {
            give(player, pending);
        }
    }

    @Override
    public void onClose(StationContext station, Player player) {
        if (station.users().isEmpty()) {
            restart(station);
            picked = null;
        }
        publish(station);
    }

    @Override
    public void onCustomAction(StationContext station, Player player, String action, CompoundTag data) {
        switch (action) {
            case CuttingBoardStation.ACTION_PLACE -> place(station, player, data.getBoolean(CuttingBoardStation.ONE),
                    data.contains(CuttingBoardStation.FROM) ? data.getInt(CuttingBoardStation.FROM) : -1,
                    data.getBoolean(CuttingBoardStation.BY_USE));
            case CuttingBoardStation.ACTION_CARVE -> carve(station, player);
            case CuttingBoardStation.ACTION_CHOP -> chop(station, player, data.getInt(CuttingBoardStation.MARK), data.getFloat(CuttingBoardStation.AT),
                    data.contains(CuttingBoardStation.AT_X) ? data.getFloat(CuttingBoardStation.AT_X) : Float.NaN);
            case CuttingBoardStation.ACTION_TAKE -> take(station, player);
            case CuttingBoardStation.ACTION_ASSEMBLE -> assemble(station, player);
            case CuttingBoardStation.ACTION_PICK -> pick(station, player, ResourceLocation.tryParse(data.getString(CuttingBoardStation.DISH)),
                    data.getBoolean(CuttingBoardStation.ALL));
            default -> {
            }
        }
        publish(station);
    }

    /** Whether {@code stack} is a tool here: it cuts or strips, and never lies down. */
    public static boolean isTool(ItemStack stack) {
        return FarmCharmBoard.isKnife(stack) || FarmCharmBoard.isAxe(stack);
    }

    /**
     * The stack on the cursor, or the one in inventory slot {@code from}, goes onto the board: on the dish when a dish takes it there,
     * else to be cut. {@code byUse}: the stack held when the board was opened, which stays in the hand without a word when the board
     * cannot take it.
     */
    private void place(StationContext station, Player player, boolean one, int from, boolean byUse) {
        if (from >= Inventory.INVENTORY_SIZE) {
            return;
        }
        ItemStack carried = from >= 0 ? player.getInventory().getItem(from) : player.inventoryMenu.getCarried();
        if (carried.isEmpty() || isTool(carried)) {
            return;
        }
        Level level = station.level();
        List<ItemStack> layers = layers(level);
        ItemStack onBoard = onBoard(level);
        boolean workable = FarmCharmBoard.work(level, carried) != FarmCharmBoard.Work.NONE;
        if (layers.isEmpty() && !onBoard.isEmpty() && !ItemStack.isSameItemSameComponents(onBoard, carried)
                && FarmCharmBoard.accepts(level, List.of(onBoard, carried))) {
            // What lay there to be cut is the start of a dish this one goes on: it becomes its first layer, a batch of it at most.
            layers = startDish(station, player, onBoard);
            onBoard = ItemStack.EMPTY;
        }
        if (!layers.isEmpty() || onBoard.isEmpty() && !workable && FarmCharmBoard.accepts(level, List.of(carried))) {
            List<ItemStack> next = new ArrayList<>(layers);
            next.add(carried);
            if (layers.size() >= CuttingBoardStation.MOST_LAYERS || !FarmCharmBoard.accepts(level, next)) {
                refuse(station, byUse ? ItemStack.EMPTY : carried, byUse);
                return;
            }
            // A batch: the first layer sets how many, every layer after it as many as the hand has, up to that.
            int batch = layers.isEmpty() ? CuttingBoardStation.BATCH : layers.get(0).getCount();
            ItemStack put = carried.split(one ? 1 : Math.min(carried.getCount(), batch));
            putBack(player, from, carried);
            List<ItemStack> laid = new ArrayList<>(layers.size() + 1);
            layers.forEach(layer -> laid.add(layer.copy()));
            laid.add(put);
            board.setItems(laid);
            CompoundTag where = new CompoundTag();
            where.putInt(CuttingBoardStation.AT, laid.size() - 1);
            station.effect(CuttingBoardStation.EFFECT_PLACED, "surface", where);
            finishIfWanted(station, player);
            return;
        }
        boolean fits = onBoard.isEmpty() ? workable && board.items().isEmpty() : ItemStack.isSameItemSameComponents(onBoard, carried)
                && board.pending().isEmpty();
        int room = carried.getMaxStackSize() - onBoard.getCount();
        int moved = Math.min(one ? 1 : carried.getCount(), room);
        if (!fits || moved <= 0) {
            refuse(station, byUse ? ItemStack.EMPTY : carried, byUse);
            return;
        }
        ItemStack put = onBoard.isEmpty() ? carried.copyWithCount(moved) : onBoard.copyWithCount(onBoard.getCount() + moved);
        carried.shrink(moved);
        putBack(player, from, carried);
        setOnBoard(put);
        if (onBoard.isEmpty()) {
            cutting = put.copyWithCount(1);
            restart(station);
        }
        station.effect(CuttingBoardStation.EFFECT_PLACED, "surface", new CompoundTag());
    }

    /** What lay there to be cut becomes the first layer of a dish, a batch of it at most; the rest goes back to the cook. */
    private List<ItemStack> startDish(StationContext station, Player player, ItemStack onBoard) {
        ItemStack base = onBoard.copyWithCount(Math.min(onBoard.getCount(), CuttingBoardStation.BATCH));
        ItemStack rest = onBoard.copyWithCount(onBoard.getCount() - base.getCount());
        // One layer that the board would cut is no dish yet: it stays in the list as the dish's first layer once the next goes on.
        cutting = ItemStack.EMPTY;
        if (!board.pending().isEmpty()) {
            board.clearPending();
        }
        board.setItems(List.of(base));
        restart(station);
        if (!rest.isEmpty()) {
            give(player, rest);
        }
        return List.of(base);
    }

    /** The layers make their dish at once when it is the one picked in the book, or when they could become nothing bigger. */
    private void finishIfWanted(StationContext station, Player player) {
        Level level = station.level();
        List<ItemStack> layers = layers(level);
        Optional<RecipeHolder<CuttingBoardAssemblyRecipe>> dish = FarmCharmBoard.dish(level, layers, picked);
        if (dish.isPresent() && (dish.get().id().equals(picked) || !FarmCharmBoard.canGrow(level, layers))) {
            assemble(station, player);
        }
    }

    /** The tool on the cursor is stuck into the bare board, as the board's own use sticks it in. */
    private void carve(StationContext station, Player player) {
        ItemStack carried = player.inventoryMenu.getCarried();
        if (!isTool(carried) || !board.items().isEmpty() || !board.pending().isEmpty() || !board.knife().isEmpty()) {
            refuse(station, carried, false);
            return;
        }
        board.setKnife(carried.copyWithCount(1));
        carried.shrink(1);
        player.inventoryMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        station.level().playSound(null, station.pos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.2F);
        station.effect(CuttingBoardStation.EFFECT_CARVED, "surface", new CompoundTag());
    }

    /** Where the tool that cuts {@code worked} for {@code player} is: the cursor, the board's stuck tool, an inventory slot. */
    private record Tool(ItemStack stack, int slot, boolean stuck) {
    }

    private Optional<Tool> tool(Level level, Player player, ItemStack worked) {
        ItemStack carried = player.inventoryMenu.getCarried();
        if (FarmCharmBoard.worksWith(level, worked, carried)) {
            return Optional.of(new Tool(carried, -1, false));
        }
        if (!carried.isEmpty()) {
            return Optional.empty();
        }
        if (FarmCharmBoard.worksWith(level, worked, board.knife())) {
            return Optional.of(new Tool(board.knife(), -1, true));
        }
        int slot = toolSlot(level, worked, player.getInventory());
        return slot < 0 ? Optional.empty() : Optional.of(new Tool(player.getInventory().getItem(slot), slot, false));
    }

    /** The inventory slot of a tool that works {@code worked}: the selected one first, then the rest in order; -1 for none. */
    public static int toolSlot(Level level, ItemStack worked, Inventory inventory) {
        if (FarmCharmBoard.worksWith(level, worked, inventory.getSelected())) {
            return inventory.selected;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (FarmCharmBoard.worksWith(level, worked, inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** How many strokes the item lying there takes for the tool the first cook at the board would cut it with, at most {@link #MOST_MARKS}. */
    private int chopsFor(StationContext station, ItemStack worked) {
        Level level = station.level();
        ItemStack tool = board.knife();
        for (Player player : station.users()) {
            tool = tool(level, player, worked).map(Tool::stack).orElse(tool);
            break;
        }
        int chops = FarmCharmBoard.chops(level, worked, tool);
        return Mth.clamp(chops, 1, MOST_MARKS);
    }

    /**
     * The tool comes down on the item; with its last stroke the board's cut goes through a batch of the stack, one item after the
     * other (each wears the tool and gives its experience), and the pieces go to the cook.
     */
    private void chop(StationContext station, Player player, int mark, float at, float atX) {
        Level level = station.level();
        ItemStack onBoard = onBoard(level);
        long now = level.getGameTime();
        if (onBoard.isEmpty() || mark != marks || now - lastChop < CHOP_GAP) {
            refuse(station);
            return;
        }
        Optional<Tool> found = tool(level, player, onBoard);
        if (found.isEmpty()) {
            refuse(station);
            return;
        }
        lastChop = now;
        Tool tool = found.get();
        ItemStack stack = tool.stack();
        int needed = Mth.clamp(FarmCharmBoard.chops(level, onBoard, stack), 1, MOST_MARKS);
        FarmCharmBoard.Work work = FarmCharmBoard.work(level, onBoard);
        CompoundTag chopped = new CompoundTag();
        chopped.putFloat(CuttingBoardStation.AT, Mth.clamp(at, 0.0F, 16.0F));
        if (!Float.isNaN(atX)) {
            chopped.putFloat(CuttingBoardStation.AT_X, Mth.clamp(atX, 0.0F, 16.0F));
        }
        chopped.putFloat(CuttingBoardStation.SPOT, spots[Math.min(mark, MOST_MARKS - 1)]);
        chopped.putFloat(CuttingBoardStation.ANGLE, angles[Math.min(mark, MOST_MARKS - 1)]);
        chopped.putString(CuttingBoardStation.TOOL, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        marks++;
        // Every stroke sounds as the board's own: the knife on meat, fish or greens, the axe on bark.
        if (work == FarmCharmBoard.Work.STRIP) {
            level.playSound(null, station.pos(), SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.6F, 1.3F + level.getRandom().nextFloat() * 0.2F);
        } else {
            CuttingBoardBlockEntity.playChopSound(level, station.pos(), onBoard);
        }
        if (marks < needed) {
            station.effect(CuttingBoardStation.EFFECT_CHOPPED, "item", chopped);
            return;
        }
        List<ItemStack> pieces = new ArrayList<>();
        float experience = 0.0F;
        int done = 0;
        ItemStack lying = onBoard.copy();
        Optional<CuttingBoardRecipe> recipe = work == FarmCharmBoard.Work.CUT ? FarmCharmBoard.cut(level, lying) : Optional.empty();
        while (done < CuttingBoardStation.BATCH && !lying.isEmpty() && !stack.isEmpty()) {
            if (recipe.isPresent()) {
                pieces.addAll(FarmCharmBoard.pieces(level, recipe.get(), lying));
                experience += FarmCharmBoard.experience(recipe.get());
            } else {
                Optional<ItemStack> stripped = FarmCharmBoard.stripped(lying);
                if (stripped.isEmpty()) {
                    break;
                }
                pieces.add(stripped.get());
                experience += FarmCharmBoard.STRIP_EXPERIENCE;
            }
            lying.shrink(1);
            wear(level, player, stack);
            done++;
        }
        setOnBoard(lying);
        if (tool.stuck()) {
            board.setKnife(stack.isEmpty() ? ItemStack.EMPTY : stack);
        } else if (tool.slot() < 0) {
            player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        } else {
            player.getInventory().setItem(tool.slot(), stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        pieces.forEach(piece -> give(player, piece));
        award(level, player, experience);
        cutting = lying.copyWithCount(1);
        restart(station);
        if (done == 0) {
            refuse(station);
            return;
        }
        chopped.putInt(CuttingBoardStation.COUNT, done);
        chopped.putBoolean(CuttingBoardStation.DONE, true);
        station.effect(CuttingBoardStation.EFFECT_CHOPPED, "item", chopped);
    }

    /** One use of the tool, as the board wears it; a tool that breaks is heard. */
    private static void wear(Level level, Player player, ItemStack tool) {
        if (!(level instanceof ServerLevel server) || !tool.isDamageableItem()) {
            return;
        }
        tool.hurtAndBreak(1, server, player instanceof ServerPlayer serverPlayer ? serverPlayer : null, item -> level.playSound(null,
                player.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F));
    }

    /** The cuts' experience, a whole point at a time, the rest kept for the cook's next cuts at this board. */
    private void award(Level level, Player player, float experience) {
        if (!(level instanceof ServerLevel server) || experience <= 0.0F) {
            return;
        }
        float total = earned.getOrDefault(player.getUUID(), 0.0F) + experience;
        int whole = Mth.floor(total);
        earned.put(player.getUUID(), total - whole);
        if (whole > 0) {
            ExperienceOrb.award(server, player.position(), whole);
        }
    }

    /** The top layer comes off the dish; with none, what lies to be cut goes back; with nothing there, the stuck tool comes out. */
    private void take(StationContext station, Player player) {
        Level level = station.level();
        List<ItemStack> layers = layers(level);
        if (!layers.isEmpty()) {
            List<ItemStack> left = new ArrayList<>();
            layers.forEach(layer -> left.add(layer.copy()));
            ItemStack taken = left.remove(left.size() - 1);
            board.setItems(left);
            taken(station, taken, left.size(), false);
            give(player, taken);
            return;
        }
        ItemStack onBoard = onBoard(level);
        if (!onBoard.isEmpty()) {
            ItemStack taken = onBoard.copy();
            setOnBoard(ItemStack.EMPTY);
            cutting = ItemStack.EMPTY;
            restart(station);
            taken(station, taken, -1, false);
            give(player, taken);
            return;
        }
        ItemStack knife = board.knife();
        if (!knife.isEmpty()) {
            board.setKnife(ItemStack.EMPTY);
            taken(station, knife, -1, true);
            give(player, knife.copy());
            level.playSound(null, station.pos(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
    }

    /**
     * The layers become their dish, round after round, as many times as the thinnest layer allows; the dishes and what is left of the
     * layers go to the cook.
     */
    private void assemble(StationContext station, Player player) {
        Level level = station.level();
        List<ItemStack> layers = layers(level);
        Optional<RecipeHolder<CuttingBoardAssemblyRecipe>> dish = FarmCharmBoard.dish(level, layers, picked);
        if (dish.isEmpty()) {
            refuse(station);
            return;
        }
        int rounds = CuttingBoardStation.BATCH;
        for (ItemStack layer : layers) {
            rounds = Math.min(rounds, layer.getCount());
        }
        List<ItemStack> made = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            ItemStack result = FarmCharmBoard.make(level, dish.get().value(), layers);
            result.onCraftedBy(level, player, result.getCount());
            made.add(result);
        }
        CompoundTag how = new CompoundTag();
        ListTag from = new ListTag();
        layers.forEach(layer -> from.add(layer.copyWithCount(1).saveOptional(level.registryAccess())));
        how.put(CuttingBoardStation.LAYERS, from);
        how.put(CuttingBoardStation.STACK, FarmCharmBoard.result(dish.get().value()).copyWithCount(1).saveOptional(level.registryAccess()));
        int count = rounds;
        List<ItemStack> left = new ArrayList<>();
        layers.forEach(layer -> {
            if (layer.getCount() > count) {
                left.add(layer.copyWithCount(layer.getCount() - count));
            }
        });
        board.setItems(List.of());
        left.forEach(rest -> give(player, rest));
        made.forEach(result -> give(player, result));
        level.playSound(null, station.pos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        station.effect(CuttingBoardStation.EFFECT_ASSEMBLED, "item", how);
    }

    /**
     * A dish picked in the book is the one the board makes, and what it still lacks comes from the cook's inventory, laid in its
     * order: onto an empty board one set or with {@code all} a batch; onto layers already there, as many as lie there.
     */
    private void pick(StationContext station, Player player, ResourceLocation id, boolean all) {
        Level level = station.level();
        Optional<RecipeHolder<CuttingBoardAssemblyRecipe>> found = FarmCharmBoard.assemblies(level).stream()
                .filter(recipe -> recipe.id().equals(id)).findFirst();
        if (found.isEmpty()) {
            return;
        }
        picked = id;
        CuttingBoardAssemblyRecipe dish = found.get().value();
        List<Ingredient> wanted = FarmCharmBoard.laid(dish);
        List<ItemStack> layers = new ArrayList<>();
        ItemStack onBoard = onBoard(level);
        if (!onBoard.isEmpty()) {
            if (!wanted.get(0).test(onBoard) && !FarmCharmBoard.isOrdered(dish)) {
                // Food lying to be cut keeps the board, unless the dish starts with it.
                if (wanted.stream().noneMatch(ingredient -> ingredient.test(onBoard))) {
                    return;
                }
            } else if (!wanted.get(0).test(onBoard)) {
                return;
            }
            layers.add(onBoard.copyWithCount(Math.min(onBoard.getCount(), CuttingBoardStation.BATCH)));
        } else {
            layers(level).forEach(layer -> layers.add(layer.copy()));
        }
        // Which of the dish's ingredients the layers already are: in order for an ordered dish, else each the first that takes it.
        boolean[] used = new boolean[wanted.size()];
        for (int i = 0; i < layers.size(); i++) {
            int match = -1;
            for (int w = 0; w < wanted.size() && match < 0; w++) {
                boolean finisher = FarmCharmBoard.hasFinisher(dish) && w == wanted.size() - 1;
                if (!used[w] && (!FarmCharmBoard.isOrdered(dish) || w == i) && !finisher && wanted.get(w).test(layers.get(i))) {
                    match = w;
                }
            }
            if (match < 0) {
                return;
            }
            used[match] = true;
        }
        List<Integer> missing = new ArrayList<>();
        for (int w = 0; w < wanted.size(); w++) {
            if (!used[w]) {
                missing.add(w);
            }
        }
        if (missing.isEmpty() || layers.size() + missing.size() > CuttingBoardStation.MOST_LAYERS) {
            if (missing.isEmpty() && !layers.isEmpty()) {
                finishIfWanted(station, player);
            }
            return;
        }
        int want = layers.isEmpty() ? all ? CuttingBoardStation.BATCH : 1 : layers.get(0).getCount();
        Inventory inventory = player.getInventory();
        ItemStack[] left = new ItemStack[Inventory.INVENTORY_SIZE];
        for (int slot = 0; slot < left.length; slot++) {
            left[slot] = inventory.getItem(slot).copy();
        }
        ItemStack[] kinds = new ItemStack[missing.size()];
        int[] counts = new int[missing.size()];
        List<int[]> removals = new ArrayList<>();
        int sets = 0;
        while (sets < want) {
            List<int[]> set = new ArrayList<>();
            boolean whole = true;
            for (int m = 0; m < missing.size() && whole; m++) {
                if (kinds[m] != null && counts[m] >= kinds[m].getMaxStackSize()) {
                    whole = false;
                    break;
                }
                int slot = source(left, wanted.get(missing.get(m)), kinds[m]);
                if (slot < 0) {
                    whole = false;
                    break;
                }
                if (kinds[m] == null) {
                    kinds[m] = left[slot].copyWithCount(1);
                }
                left[slot].shrink(1);
                set.add(new int[] {slot, m});
            }
            if (!whole) {
                break;
            }
            for (int[] one : set) {
                counts[one[1]]++;
            }
            removals.addAll(set);
            sets++;
        }
        if (sets == 0) {
            return;
        }
        List<ItemStack> next = new ArrayList<>(layers);
        for (int m = 0; m < missing.size(); m++) {
            next.add(kinds[m].copyWithCount(counts[m]));
        }
        if (!FarmCharmBoard.accepts(level, next)) {
            return;
        }
        for (int[] removal : removals) {
            inventory.removeItem(removal[0], 1);
        }
        if (!onBoard.isEmpty()) {
            ItemStack rest = onBoard.copyWithCount(onBoard.getCount() - layers.get(0).getCount());
            if (!rest.isEmpty()) {
                give(player, rest);
            }
            if (!board.pending().isEmpty()) {
                board.clearPending();
            }
            cutting = ItemStack.EMPTY;
        }
        board.setItems(next);
        restart(station);
        CompoundTag where = new CompoundTag();
        where.putInt(CuttingBoardStation.AT, next.size() - 1);
        station.effect(CuttingBoardStation.EFFECT_PLACED, "surface", where);
        finishIfWanted(station, player);
    }

    private static int source(ItemStack[] inventory, Ingredient ingredient, ItemStack kind) {
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (!stack.isEmpty() && ingredient.test(stack) && (kind == null || ItemStack.isSameItemSameComponents(kind, stack))) {
                return slot;
            }
        }
        return -1;
    }

    private static void refuse(StationContext station) {
        refuse(station, ItemStack.EMPTY, false);
    }

    private static void refuse(StationContext station, ItemStack stack, boolean quiet) {
        if (quiet) {
            return;
        }
        CompoundTag data = new CompoundTag();
        if (!stack.isEmpty()) {
            data.put(CuttingBoardStation.STACK, stack.copyWithCount(1).saveOptional(station.level().registryAccess()));
        }
        station.effect(CuttingBoardStation.EFFECT_REFUSED, "surface", data);
    }

    private static void taken(StationContext station, ItemStack stack, int layer, boolean carved) {
        CompoundTag data = new CompoundTag();
        data.put(CuttingBoardStation.STACK, stack.saveOptional(station.level().registryAccess()));
        data.putInt(CuttingBoardStation.AT, layer);
        if (carved) {
            data.putBoolean(CuttingBoardStation.CARVED, true);
        }
        station.effect(CuttingBoardStation.EFFECT_TAKEN, "surface", data);
    }

    /** No cuts on the item yet, and new marks for it, as many as the tool's strokes, at even places a little askew. */
    private void restart(StationContext station) {
        marks = 0;
        ItemStack onBoard = onBoard(station.level());
        planned = onBoard.isEmpty() ? 1 : chopsFor(station, onBoard);
        RandomSource random = station.level().getRandom();
        int count = planned;
        float room = (SPOT_TO - SPOT_FROM) / count;
        for (int mark = 0; mark < count; mark++) {
            float even = SPOT_FROM + (mark + 0.5F) * room;
            float stray = count == 1 ? 0.15F : room * SPOT_JITTER;
            spots[mark] = even + Mth.nextFloat(random, -stray, stray);
            angles[mark] = Mth.nextFloat(random, -1.0F, 1.0F);
        }
        // Cut in no fixed order from one end to the other; lean so two never meet on the item.
        for (int attempt = 0; attempt < 30 && crosses(count); attempt++) {
            for (int mark = 0; mark < count; mark++) {
                angles[mark] = Mth.nextFloat(random, -1.0F, 1.0F);
            }
        }
        if (crosses(count)) {
            java.util.Arrays.fill(angles, 0, count, angles[0]);
        }
        for (int mark = count - 1; mark > 0; mark--) {
            int other = random.nextInt(mark + 1);
            float spot = spots[mark];
            spots[mark] = spots[other];
            spots[other] = spot;
            float angle = angles[mark];
            angles[mark] = angles[other];
            angles[other] = angle;
        }
    }

    private boolean crosses(int count) {
        for (int a = 0; a < count; a++) {
            for (int b = a + 1; b < count; b++) {
                if (crossOnTheItem(spots[a], angles[a] * MOST_LEAN, spots[b], angles[b] * MOST_LEAN)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether two marks meet on the item, taken as a square with its length from far to near (Hands-On Delight's board). */
    public static boolean crossOnTheItem(float spotA, float angleA, float spotB, float angleB) {
        float ax = Mth.cos(angleA);
        float az = Mth.sin(angleA);
        float bx = Mth.cos(angleB);
        float bz = Mth.sin(angleB);
        float across = ax * bz - az * bx;
        if (Math.abs(across) < 1.0e-4F) {
            return false;
        }
        float t = (0.0F * bz - (spotB - spotA) * bx) / across;
        float x = 0.5F + t * ax;
        float z = spotA + t * az;
        return x >= 0.0F && x <= 1.0F && z >= 0.0F && z <= 1.0F;
    }

    private static void putBack(Player player, int from, ItemStack stack) {
        if (from >= 0) {
            player.getInventory().setItem(from, stack.isEmpty() ? ItemStack.EMPTY : stack);
        } else {
            player.inventoryMenu.setCarried(stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }

    /** Into the inventory, what does not fit at the cook's feet, in stacks no bigger than the item allows. */
    private static void give(Player player, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(stack.getMaxStackSize());
            if (!player.getInventory().add(part)) {
                player.drop(part, false);
            }
        }
    }

    /** The cuts and the dish the layers make, for the clients; sent only when they change. */
    private void publish(StationContext station) {
        StationState state = station.state();
        Level level = station.level();
        CompoundTag data = new CompoundTag();
        if (marks > 0) {
            data.putInt(CuttingBoardStation.MARKS, marks);
        }
        if (!onBoard(level).isEmpty()) {
            ListTag where = new ListTag();
            ListTag how = new ListTag();
            for (int mark = 0; mark < planned; mark++) {
                where.add(FloatTag.valueOf(spots[mark]));
                how.add(FloatTag.valueOf(angles[mark]));
            }
            data.put(CuttingBoardStation.SPOTS, where);
            data.put(CuttingBoardStation.ANGLES, how);
        }
        FarmCharmBoard.dish(level, layers(level), picked).ifPresent(dish -> data.putString(CuttingBoardStation.DISH,
                BuiltInRegistries.ITEM.getKey(FarmCharmBoard.result(dish.value()).getItem()).toString()));
        if (!data.equals(state.data())) {
            state.setData(data);
        }
    }
}
