package ma.shaur.bettercoppergolem.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import ma.shaur.bettercoppergolem.config.Config;
import ma.shaur.bettercoppergolem.config.ConfigHandler;
import ma.shaur.bettercoppergolem.custom.entity.LastItemDataHolder;
import ma.shaur.bettercoppergolem.custom.world.CopperGolemChestLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers.TransportItemTarget;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(TransportItemsBetweenContainers.class)
public abstract class TransportItemsBetweenContainersMixin {
	@Shadow
	private static boolean isPickingUpItems(PathfinderMob entity) {
		return false;
	}

	@Shadow
	private static boolean matchesLeavingItemsRequirement(PathfinderMob entity, Container inventory) {
		return false;
	}

	@Shadow
	private static boolean matchesGettingItemsRequirement(Container inventory) {
		return false;
	}

	@Shadow
	private static boolean hasItemMatchingHandItem(PathfinderMob entity, Container inventory) {
		return false;
	}

	@Shadow
	protected abstract void clearMemoriesAfterMatchingTargetFound(PathfinderMob pathAwareEntity);

	@Shadow
	protected abstract void stopTargetingCurrentTarget(PathfinderMob pathAwareEntity);

	@Shadow
	protected abstract void setVisitedBlockPos(PathfinderMob entity, Level world, BlockPos pos);

	@Shadow
	private Optional<TransportItemTarget> getTransportTarget(ServerLevel level, PathfinderMob entity) {
		return Optional.empty();
	}

	@Shadow
	private boolean isWantedBlock(PathfinderMob entity, BlockState state) {
		return false;
	}

	@Shadow
	private boolean isContainerLocked(TransportItemTarget target) {
		return false;
	}

	@Shadow
	protected abstract void markVisitedBlockPosAsUnreachable(PathfinderMob entity, Level world, BlockPos pos);

	@Shadow
	private static java.util.Set<GlobalPos> getVisitedPositions(PathfinderMob entity) {
		return null;
	}

	@Shadow
	private static java.util.Set<GlobalPos> getUnreachablePositions(PathfinderMob entity) {
		return null;
	}

	@Shadow
	private boolean isPositionAlreadyVisited(java.util.Set<GlobalPos> visited, java.util.Set<GlobalPos> unreachable, TransportItemTarget target, Level level) {
		return false;
	}

	/**
	 * Vanilla additionally requires a ray-cast to a chest face before accepting a target.
	 * A chest selected by the golem is already a valid target, and the navigation path is
	 * sufficient to get the golem into interaction range. Do not reject the target merely
	 * because another block is between the golem and the chest.
	 */
	@Inject(method = "targetIsReachableFromPosition", at = @At("HEAD"), cancellable = true)
	private void betterTargetIsReachableFromPosition(Level level, boolean withinDistance,
			net.minecraft.world.phys.Vec3 position, TransportItemTarget target, PathfinderMob entity,
			CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(withinDistance);
	}

	@ModifyConstant(method = "onReachedTarget", constant = @Constant(intValue = 60))
	public int interactionTime(int constant) {
		return ConfigHandler.getConfig().interactionTime;
	}

	@ModifyConstant(method = "isWithinTargetDistance", constant = @Constant(doubleValue = 0.5))
	public double verticalRange(double constant) {
		return ConfigHandler.getConfig().verticalRange - 1.5;
	}

	@ModifyConstant(method = "setVisitedBlockPos", constant = @Constant(intValue = 10))
	private int maxChestCheckCount(int constant) {
		return ConfigHandler.getConfig().maxChestCheckCount;
	}

	@ModifyConstant(method = "enterCooldownAfterNoMatchingTargetFound", constant = @Constant(intValue = 140))
	private int cooldownTime(int constant) {
		return ConfigHandler.getConfig().cooldownTime;
	}

	@ModifyArg(method = "onReachedTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;doReachedTargetInteraction(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/world/Container;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;)V"), index = 2)
	private BiConsumer<PathfinderMob, Container> pickupItemCallback(
			BiConsumer<PathfinderMob, Container> pickupItemCallback) {
		return this::betterPickUpItems;
	}

	@ModifyVariable(method = "isTargetValidToPick", at = @At(value = "STORE"), ordinal = 1)
	private boolean betterTestContainer(boolean valid, @Local() PathfinderMob entity,
			@Local TransportItemTarget storage) {
		return ConfigHandler.getConfig().matchOxidationLevel && entity instanceof CopperGolem copperGolem
				? storage.state().getBlock() instanceof CopperChestBlock chest
						&& chest.getState() == copperGolem.getWeatherState()
				: valid;
	}

	private void betterPickUpItems(PathfinderMob entity, Container inventory) {
		ItemStack itemStack = betterPickupItemFromContainer(entity, inventory);
		entity.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
		entity.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		inventory.setChanged();
		if (!(entity instanceof LastItemDataHolder lastStackHolder && !lastStackHolder.getLastItemStack().isEmpty()
				&& ItemStack.isSameItem(lastStackHolder.getLastItemStack(), itemStack)))
			this.clearMemoriesAfterMatchingTargetFound(entity);
	}

	private static ItemStack betterPickupItemFromContainer(PathfinderMob entity, Container inventory) {
		int i = 0, firstMatch = -1, matchAmmount = 0;
		Config config = ConfigHandler.getConfig();

		for (ItemStack itemStack : inventory) {
			if (!itemStack.isEmpty() && firstMatch < 0) {
				matchAmmount = Math.min(itemStack.getCount(), config.maxHeldItemStackSize);
				firstMatch = i;
				if (!(entity instanceof LastItemDataHolder))
					break;
			}
			if (entity instanceof LastItemDataHolder lastStackHolder && !lastStackHolder.getLastItemStack().isEmpty()
					&& ItemStack.isSameItem(lastStackHolder.getLastItemStack(), itemStack)) {
				return inventory.removeItem(i, Math.min(itemStack.getCount(), config.maxHeldItemStackSize));
			}
			i++;
		}

		return firstMatch < 0 ? ItemStack.EMPTY : inventory.removeItem(firstMatch, matchAmmount);
	}

	@ModifyArg(method = "onReachedTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;doReachedTargetInteraction(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/world/Container;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;Ljava/util/function/BiConsumer;)V"), index = 4)
	private BiConsumer<PathfinderMob, Container> placeItemCallback(
			BiConsumer<PathfinderMob, Container> pickupItemCallback) {
		return this::betterPutDownItem;
	}

	private void betterPutDownItem(PathfinderMob entity, Container inventory) {
		ItemStack itemStack = betterAddItemsToContainer(entity, inventory);
		inventory.setChanged();
		entity.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
		if (itemStack.isEmpty()) {
			if (!(entity instanceof LastItemDataHolder))
				this.clearMemoriesAfterMatchingTargetFound(entity);
		} else {
			this.stopTargetingCurrentTarget(entity);
			if (inventory instanceof BaseContainerBlockEntity blockEntity)
				setVisitedBlockPos(entity, entity.level(), blockEntity.getBlockPos());
		}
	}

	private static ItemStack betterAddItemsToContainer(PathfinderMob entity, Container inventory) {
		int i = 0;
		ItemStack hand = entity.getMainHandItem();
		ItemStack handCopy = hand.copy();

		for (ItemStack itemStack : inventory) {
			if (itemStack.isEmpty()) {
				inventory.setItem(i, hand);
				if (entity instanceof LastItemDataHolder lastStackHolder)
					lastStackHolder.setLastItemStack(handCopy);
				return ItemStack.EMPTY;
			}

			if (ItemStack.isSameItemSameComponents(itemStack, hand)
					&& itemStack.getCount() < itemStack.getMaxStackSize()) {
				int tillFullStack = itemStack.getMaxStackSize() - itemStack.getCount();
				int toInsert = Math.min(tillFullStack, hand.getCount());
				itemStack.setCount(itemStack.getCount() + toInsert);
				hand.setCount(hand.getCount() - tillFullStack);
				inventory.setItem(i, itemStack);
				if (hand.isEmpty()) {
					if (entity instanceof LastItemDataHolder lastStackHolder)
						lastStackHolder.setLastItemStack(handCopy);
					return ItemStack.EMPTY;
				}
			}

			if (ConfigHandler.getConfig().allowInsertingItemsIntoContainers) {
				DataComponentMap componentMap = itemStack.getComponents();

				if (componentMap.has(DataComponents.BUNDLE_CONTENTS)) {
					BundleContents component = componentMap.get(DataComponents.BUNDLE_CONTENTS);
					BundleContents.Mutable componentBuilder = new BundleContents.Mutable(component);
					if (componentBuilder.tryInsert(hand) > 0)
						itemStack.set(DataComponents.BUNDLE_CONTENTS, componentBuilder.toImmutable());

					if (hand.isEmpty()) {
						if (entity instanceof LastItemDataHolder lastStackHolder)
							lastStackHolder.setLastItemStack(handCopy);
						return ItemStack.EMPTY;
					}
				} else if (componentMap.has(DataComponents.CONTAINER)) {
					ItemContainerContents component = componentMap.get(DataComponents.CONTAINER);
					List<Optional<ItemStackTemplate>> stacks = ((ItemContainerContentsAccessor) (Object) component)
							.getItems();
					int j = 0;
					for (; j < stacks.size(); j++) {
						ItemStack stack = stacks.get(j).isEmpty() ? null : stacks.get(j).get().create();
						if (stack == null) {
							stacks.set(j, Optional.of(ItemStackTemplate.fromNonEmptyStack(hand)));
							if (entity instanceof LastItemDataHolder lastStackHolder)
								lastStackHolder.setLastItemStack(handCopy);
							return ItemStack.EMPTY;
						} else if (ItemStack.isSameItemSameComponents(stack, hand)) {
							int tillFullStack = stack.getMaxStackSize() - stack.getCount();
							int toInsert = Math.min(tillFullStack, hand.getCount());
							stack.setCount(stack.getCount() + toInsert);
							hand.setCount(hand.getCount() - tillFullStack);
							stacks.set(j, Optional.of(ItemStackTemplate.fromNonEmptyStack(stack)));

							if (hand.isEmpty()) {
								if (entity instanceof LastItemDataHolder lastStackHolder)
									lastStackHolder.setLastItemStack(handCopy);
								return ItemStack.EMPTY;
							}
						}
					}
					if (j < 27) // I CAN NOT find max slot amount for container component
					{
						stacks.add(Optional.of(ItemStackTemplate.fromNonEmptyStack(hand)));
						if (entity instanceof LastItemDataHolder lastStackHolder)
							lastStackHolder.setLastItemStack(handCopy);
						return ItemStack.EMPTY;
					}
				}
			}

			i++;
		}
		return hand;
	}

	// Kinda silly, can't think of a better way for now
	@Redirect(method = "updateInvalidTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;setVisitedBlockPos(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
	private void betterSetVisitedBlockPos(TransportItemsBetweenContainers moveItemsTask, PathfinderMob entity,
			Level world, BlockPos pos, ServerLevel paramWorld, PathfinderMob paramEntity) {
		if (!(entity instanceof LastItemDataHolder)) {
			setVisitedBlockPos(entity, world, pos);
			return;
		}

		Container inventory = null;

		BlockEntity blockEntity = world.getBlockEntity(pos);
		BlockState blockState = world.getBlockState(pos);
		Block block = blockState.getBlock();
		if (block instanceof ChestBlock chestBlock)
			inventory = ChestBlock.getContainer(chestBlock, blockState, world, pos, true);
		else if (blockEntity instanceof Container)
			inventory = (Container) blockEntity;

		if (inventory != null) {
			if (isPickingUpItems(entity)) {
				// Log the chest as a pickup source so the golem can remember it for future
				// trips
				if (world instanceof ServerLevel serverLevel && ConfigHandler.getConfig().maxGlobalChestLogSize > 0)
					CopperGolemChestLog.get(serverLevel).rememberPickupSource(serverLevel, pos, inventory);
				if (!matchesGettingItemsRequirement(inventory))
					setVisitedBlockPos(entity, world, pos);
			} else {
				// Log the chest as a placement target so the golem can remember it for future
				// trips
				if (world instanceof ServerLevel serverLevel && ConfigHandler.getConfig().maxGlobalChestLogSize > 0)
					CopperGolemChestLog.get(serverLevel).rememberPlacementTarget(serverLevel, pos, inventory);
				if (!matchesLeavingItemsRequirement(entity, inventory))
					setVisitedBlockPos(entity, world, pos);
			}
		}
	}

	@Redirect(method = "updateInvalidTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;getTransportTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;)Ljava/util/Optional;"))
	private Optional<TransportItemTarget> betterGetTransportTarget(TransportItemsBetweenContainers moveItemsTask,
			ServerLevel level, PathfinderMob entity) {
		Optional<TransportItemTarget> loggedTarget = getLoggedTransportTarget(level, entity);
		return loggedTarget.isPresent() ? loggedTarget : getTransportTarget(level, entity);
	}

	// Capture pickup/placement state BEFORE the interaction runs (at HEAD), because
	// by TAIL
	// betterPickUpItems has already placed an item in the entity's hand, making
	// isPickingUpItems
	// return false even for what was originally a pickup interaction.
	@Inject(method = "onReachedTarget", at = @At("HEAD"))
	private void rememberReachedChest(TransportItemTarget target, Level level, PathfinderMob entity,
			CallbackInfo info) {
		if (ConfigHandler.getConfig().maxGlobalChestLogSize <= 0)
			return;
		if (level instanceof ServerLevel serverLevel) {
			CopperGolemChestLog log = CopperGolemChestLog.get(serverLevel);
			if (isPickingUpItems(entity))
				log.rememberPickupSource(serverLevel, target.pos(), target.container());
			else
				log.rememberPlacementTarget(serverLevel, target.pos(), target.container());
		}
	}

	private Optional<TransportItemTarget> getLoggedTransportTarget(ServerLevel level, PathfinderMob entity) {
		if (ConfigHandler.getConfig().maxGlobalChestLogSize <= 0)
			return Optional.empty();

		ItemStack wantedStack = getWantedLoggedStack(entity);
		if (wantedStack.isEmpty())
			return Optional.empty();

		CopperGolemChestLog log = CopperGolemChestLog.get(level);
		List<GlobalPos> candidates = isPickingUpItems(entity)
				? log.pickupCandidatesFor(wantedStack.getItem(), level, entity.blockPosition(),
						ConfigHandler.getConfig().horizontalRange)
				: log.placementCandidatesFor(wantedStack.getItem(), level, entity.blockPosition(),
						ConfigHandler.getConfig().horizontalRange);
		for (GlobalPos candidate : candidates) {
			TransportItemTarget target = TransportItemTarget.tryCreatePossibleTarget(candidate.pos(), level);
			if (isLoggedTargetValid(entity, level, target, wantedStack)) {
				log.touch(candidate);
				return Optional.of(target);
			}
			log.remove(candidate);
		}

		return Optional.empty();
	}

	private ItemStack getWantedLoggedStack(PathfinderMob entity) {
		if (isPickingUpItems(entity)) {
			return entity instanceof LastItemDataHolder lastStackHolder ? lastStackHolder.getLastItemStack()
					: ItemStack.EMPTY;
		}
		return entity.getMainHandItem();
	}

	private boolean isLoggedTargetValid(PathfinderMob entity, Level level, TransportItemTarget target,
			ItemStack wantedStack) {
		if (target == null || wantedStack.isEmpty())
			return false;
		if (!isWantedBlock(entity, target.state()) || isContainerLocked(target))
			return false;
		if (isPositionAlreadyVisited(getVisitedPositions(entity), getUnreachablePositions(entity), target, level))
			return false;

		Container container = target.container();
		if (isPickingUpItems(entity)) {
			return hasItem(container, wantedStack);
		}

		return hasItem(container, wantedStack) && matchesLeavingItemsRequirement(entity, container);
	}

	private static boolean hasItem(Container container, ItemStack wantedStack) {
		for (ItemStack stack : container) {
			if (!stack.isEmpty() && ItemStack.isSameItem(stack, wantedStack))
				return true;
		}
		return false;
	}

	// I am become lag, the destroyer of TPS
	@Redirect(method = "matchesLeavingItemsRequirement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers;hasItemMatchingHandItem(Lnet/minecraft/world/entity/PathfinderMob;Lnet/minecraft/world/Container;)Z"))
	private static boolean betterHasItemMatchingHandItem(PathfinderMob entity, Container inventory,
			PathfinderMob paramEntity, Container paramInventory) {
		ItemStack hand = entity.getMainHandItem();
		Config config = ConfigHandler.getConfig();

		boolean emptySpaces = false, shouldPlace = false, shouldInsert = false;
		for (ItemStack stack : inventory) {
			if (shouldPlace && emptySpaces || shouldInsert)
				return true;

			if (stack.isEmpty()) {
				emptySpaces = true;
				continue;
			}

			if (!hand.getComponents().has(DataComponents.BUNDLE_CONTENTS)
					&& !hand.getComponents().has(DataComponents.CONTAINER) && ItemStack.isSameItem(stack, hand)) {
				shouldPlace = true;
				if (stack.getCount() < stack.getMaxStackSize() && ItemStack.isSameItemSameComponents(stack, hand)) {
					emptySpaces = true;
					continue;
				}
			}

			if (!config.shulkerAndBundleSorting || shouldPlace || shouldInsert)
				continue;

			DataComponentMap componentMap = stack.getComponents();
			if (componentMap.has(DataComponents.BUNDLE_CONTENTS)) {
				BundleContents component = componentMap.get(DataComponents.BUNDLE_CONTENTS);

				if (hand.getComponents().has(DataComponents.BUNDLE_CONTENTS)) {
					if (!config.ignoreColor
							&& (!(hand.getItem() instanceof BundleItem) || !hand.getItem().equals(Items.BUNDLE))) // afaik
																													// there
																													// is
																													// no
																													// way
																													// to
																													// just
																													// get
																													// dye
																													// color
																													// of
																													// an
																													// item
					{
						if (ItemStack.isSameItemSameComponents(stack, hand))
							shouldPlace = true;
						continue;
					} else if (hand.getComponents().get(DataComponents.BUNDLE_CONTENTS).itemCopyStream().parallel()
							.filter(i -> !component.itemCopyStream().parallel()
									.anyMatch(j -> ItemStack.isSameItemSameComponents(i, j)))
							.findAny().isEmpty()) {
						shouldPlace = true;
						continue;
					}
				} else if (config.allowInsertingItemsIntoContainers
						|| config.allowIndividualItemsMatchContainerContents) {
					Stream<ItemStack> stream = component.itemCopyStream().parallel();
					if (stream.anyMatch(i -> ItemStack.isSameItem(hand, i))) {
						shouldPlace = config.allowIndividualItemsMatchContainerContents;
						if (config.allowInsertingItemsIntoContainers)
							shouldInsert = new BundleContents.Mutable(component).tryInsert(hand.copy()) > 0;
						continue;
					}
				}
			}
			if (componentMap.has(DataComponents.CONTAINER)) {
				ItemContainerContents component = componentMap.get(DataComponents.CONTAINER);

				if (hand.getComponents().has(DataComponents.CONTAINER)) {
					if (!config.ignoreColor && (!(hand.getItem() instanceof BlockItem blockItem)
							|| !blockItem.getBlock().equals(Blocks.SHULKER_BOX)
							|| !hand.getItem().equals(Items.SHULKER_BOX))) {
						if (ItemStack.isSameItemSameComponents(stack, hand))
							shouldPlace = true;
						continue;
					} else if (hand.getComponents().get(DataComponents.CONTAINER).allItemsCopyStream().parallel()
							.filter(i -> !component.allItemsCopyStream().parallel()
									.anyMatch(j -> ItemStack.isSameItemSameComponents(i, j)))
							.findAny().isEmpty()) {
						shouldPlace = true;
						continue;
					}
				} else if (config.allowInsertingItemsIntoContainers
						|| config.allowIndividualItemsMatchContainerContents) {
					Stream<ItemStack> stream = component.allItemsCopyStream().parallel();
					if (stream.anyMatch(i -> ItemStack.isSameItem(hand, i))) {
						shouldPlace = config.allowIndividualItemsMatchContainerContents;
						if (config.allowInsertingItemsIntoContainers) {
							List<Optional<ItemStackTemplate>> stacks = ((ItemContainerContentsAccessor) (Object) component)
									.getItems();
							int i = 0;
							for (Optional<ItemStackTemplate> template : stacks) {
								ItemStack content = template.isEmpty() ? null : template.get().create();
								if (content == null || ItemStack.isSameItemSameComponents(hand, content)
										&& content.getCount() < content.getMaxStackSize()) {
									shouldInsert = true;
									break;
								}
								i++;
							}
							if (i < 27) {
								shouldInsert = true;
							}
						}
						continue;
					}
				}
			}
		}

		return shouldPlace && emptySpaces || shouldInsert;
	}
}
