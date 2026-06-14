package com.lowdragmc.mbd2.common.item;

import com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.mbd2.common.machine.definition.EntityMachineDefinition;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class EntityMachineItem extends Item implements IItemRendererProvider {
    private final EntityMachineDefinition definition;

    public EntityMachineItem(EntityMachineDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    public EntityMachineDefinition getDefinition() {
        return definition;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Entity entity = definition.getOrCreateEntityType().spawn(serverLevel, stack, context.getPlayer(), spawnPos,
                MobSpawnType.SPAWN_EGG, true, false);
        if (entity == null) {
            return InteractionResult.FAIL;
        }
        var machine = definition.getMachine(entity);
        if (machine != null) {
            machine.onMachinePlaced(context.getPlayer(), stack);
        }
        level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, entity.position());
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public IRenderer getRenderer(ItemStack stack) {
        return definition.itemRenderer();
    }
}
