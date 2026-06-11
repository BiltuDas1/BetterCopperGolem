package ma.shaur.bettercoppergolem.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;

@Mixin(ItemContainerContents.class)
public interface ItemContainerContentsAccessor 
{
	@Accessor
	List<Optional<ItemStackTemplate>> getItems();

	@Mutable
	@Final
	@Accessor("items")
	void setItems(List<Optional<ItemStackTemplate>> stacks);
}
