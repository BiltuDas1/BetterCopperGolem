package ma.shaur.bettercoppergolem.custom.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import ma.shaur.bettercoppergolem.BetterCopperGolem;
import ma.shaur.bettercoppergolem.config.ConfigHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class CopperGolemChestLog extends SavedData
{
	private static final Codec<List<Item>> ITEM_LIST_CODEC = BuiltInRegistries.ITEM.byNameCodec().listOf();
	private static final Codec<ChestLogEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		GlobalPos.CODEC.fieldOf("pos").forGetter(ChestLogEntry::pos),
		ITEM_LIST_CODEC.optionalFieldOf("pickup_items", List.of()).forGetter(ChestLogEntry::pickupItems),
		ITEM_LIST_CODEC.optionalFieldOf("placement_items", List.of()).forGetter(ChestLogEntry::placementItems)
	).apply(instance, ChestLogEntry::new));

	public static final Codec<CopperGolemChestLog> CODEC = ENTRY_CODEC.listOf().xmap(CopperGolemChestLog::new, log -> log.entries);
	public static final SavedDataType<CopperGolemChestLog> TYPE = new SavedDataType<>(
		Identifier.fromNamespaceAndPath(BetterCopperGolem.MOD_ID, "copper_golem_chest_log"),
		CopperGolemChestLog::new,
		CODEC,
		DataFixTypes.SAVED_DATA_COMMAND_STORAGE
	);

	private final List<ChestLogEntry> entries;

	public CopperGolemChestLog()
	{
		this.entries = new ArrayList<>();
	}

	private CopperGolemChestLog(List<ChestLogEntry> entries)
	{
		this.entries = new ArrayList<>(entries);
		trimToLimit();
	}

	public static CopperGolemChestLog get(ServerLevel level)
	{
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public void rememberPickupSource(ServerLevel level, BlockPos pos, Container container)
	{
		remember(level, pos, container, true);
	}

	public void rememberPlacementTarget(ServerLevel level, BlockPos pos, Container container)
	{
		remember(level, pos, container, false);
	}

	public List<GlobalPos> pickupCandidatesFor(Item item, ServerLevel level, BlockPos origin, int horizontalRange)
	{
		return candidatesFor(item, level, origin, horizontalRange, true);
	}

	public List<GlobalPos> placementCandidatesFor(Item item, ServerLevel level, BlockPos origin, int horizontalRange)
	{
		return candidatesFor(item, level, origin, horizontalRange, false);
	}

	public void touch(GlobalPos pos)
	{
		Optional<ChestLogEntry> entry = removeInternal(pos);
		if(entry.isPresent())
		{
			entries.add(0, entry.get());
			setDirty();
		}
	}

	public void remove(GlobalPos pos)
	{
		if(removeInternal(pos).isPresent()) setDirty();
	}

	private void remember(ServerLevel level, BlockPos pos, Container container, boolean pickupSource)
	{
		GlobalPos globalPos = GlobalPos.of(level.dimension(), pos.immutable());
		ChestLogEntry oldEntry = removeInternal(globalPos).orElse(null);
		List<Item> pickupItems = oldEntry == null ? List.of() : oldEntry.pickupItems();
		List<Item> placementItems = oldEntry == null ? List.of() : oldEntry.placementItems();

		if(pickupSource) pickupItems = getDistinctItems(container);
		else placementItems = getDistinctItems(container);

		if(!pickupItems.isEmpty() || !placementItems.isEmpty())
		{
			entries.add(0, new ChestLogEntry(globalPos, pickupItems, placementItems));
		}

		trimToLimit();
		setDirty();
	}

	private List<GlobalPos> candidatesFor(Item item, ServerLevel level, BlockPos origin, int horizontalRange, boolean pickupSource)
	{
		List<GlobalPos> candidates = new ArrayList<>();
		for(ChestLogEntry entry : entries)
		{
			GlobalPos globalPos = entry.pos();
			if(!globalPos.dimension().equals(level.dimension())) continue;
			if(!isWithinHorizontalRange(origin, globalPos.pos(), horizontalRange)) continue;

			List<Item> items = pickupSource ? entry.pickupItems() : entry.placementItems();
			if(items.contains(item)) candidates.add(globalPos);
		}
		return candidates;
	}

	private Optional<ChestLogEntry> removeInternal(GlobalPos pos)
	{
		for(int i = 0; i < entries.size(); i++)
		{
			ChestLogEntry entry = entries.get(i);
			if(entry.pos().equals(pos))
			{
				entries.remove(i);
				return Optional.of(entry);
			}
		}
		return Optional.empty();
	}

	private void trimToLimit()
	{
		int limit = Math.max(0, ConfigHandler.getConfig().maxGlobalChestLogSize);
		while(entries.size() > limit)
		{
			entries.remove(entries.size() - 1);
		}
	}

	private static List<Item> getDistinctItems(Container container)
	{
		Set<Item> items = new LinkedHashSet<>();
		for(ItemStack stack : container)
		{
			if(!stack.isEmpty()) items.add(stack.getItem());
		}
		return List.copyOf(items);
	}

	private static boolean isWithinHorizontalRange(BlockPos origin, BlockPos target, int horizontalRange)
	{
		long dx = origin.getX() - target.getX();
		long dz = origin.getZ() - target.getZ();
		long range = Math.max(0, horizontalRange);
		return dx * dx + dz * dz <= range * range;
	}

	private record ChestLogEntry(GlobalPos pos, List<Item> pickupItems, List<Item> placementItems) { }
}