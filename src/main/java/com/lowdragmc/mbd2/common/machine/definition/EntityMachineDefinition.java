package com.lowdragmc.mbd2.common.machine.definition;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.blockentity.IMachineBlockEntity;
import com.lowdragmc.mbd2.api.entity.IMachineEntity;
import com.lowdragmc.mbd2.client.renderer.EntityMachineRenderer;
import com.lowdragmc.mbd2.common.entity.MBDEntityMachine;
import com.lowdragmc.mbd2.common.entity.RegisteredMBDLivingMachineEntity;
import com.lowdragmc.mbd2.common.entity.RegisteredMBDMachineEntity;
import com.lowdragmc.mbd2.common.item.EntityMachineItem;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.definition.config.*;
import com.lowdragmc.mbd2.common.trait.TraitDefinition;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Predicate;

@LDLRegister(name = "entity_machine", group = "machine_definition")
public class EntityMachineDefinition extends MBDMachineDefinition {
    public enum EntityKind {
        ENTITY,
        LIVING
    }

    @Persisted
    @Configurable(name = "config.entity_machine.entity_kind", tips = "config.require_restart")
    private EntityKind entityKind = EntityKind.ENTITY;
    @Persisted
    @Configurable(name = "config.entity_machine.width", tips = "config.require_restart")
    @NumberRange(range = {0.01, Float.MAX_VALUE})
    private float entityWidth = 0.98f;
    @Persisted
    @Configurable(name = "config.entity_machine.height", tips = "config.require_restart")
    @NumberRange(range = {0.01, Float.MAX_VALUE})
    private float entityHeight = 0.98f;
    @Persisted
    @Configurable(name = "config.entity_machine.client_tracking_range", tips = "config.require_restart")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private int clientTrackingRange = 8;
    @Persisted
    @Configurable(name = "config.entity_machine.update_interval", tips = "config.require_restart")
    @NumberRange(range = {1, Integer.MAX_VALUE})
    private int updateInterval = 3;

    private Predicate<TraitDefinition> entityTraitFilter = MBDEntityMachine::isDefaultEntitySafeTrait;
    private EntityType<?> entityType;
    private Item entityItem;
    @OnlyIn(Dist.CLIENT)
    private IRenderer entityBlockRenderer;
    @OnlyIn(Dist.CLIENT)
    private IRenderer entityItemRenderer;

    public EntityMachineDefinition(ResourceLocation id,
                                   @Nullable MachineState rootState,
                                   @Nullable ConfigBlockProperties blockProperties,
                                   @Nullable ConfigItemProperties itemProperties,
                                   @Nullable ConfigMachineSettingsFactory machineSettingsFactory,
                                   @Nullable ConfigRecipeLogicSettings recipeLogicSettings) {
        super(id, rootState, blockProperties, itemProperties, machineSettingsFactory, recipeLogicSettings, null);
    }

    public static EntityMachineDefinition createDefault() {
        return new EntityMachineDefinition(
                MBD2.id("dummy"),
                StateMachine.createDefault(MachineState::builder),
                ConfigBlockProperties.builder().rotationState(com.lowdragmc.mbd2.api.block.RotationState.NONE).build(),
                ConfigItemProperties.builder().build(),
                () -> ConfigMachineSettings.builder().build(),
                ConfigRecipeLogicSettings.builder().build());
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean allowPartSettings() {
        return false;
    }

    @Override
    public MBDMachineDefinition loadProductiveTag(@Nullable File file, net.minecraft.nbt.CompoundTag projectTag, Deque<Runnable> postTask) {
        super.loadProductiveTag(file, projectTag, postTask);
        var definitionTag = projectTag.getCompound("definition");
        if (definitionTag.contains("entityKind")) {
            try {
                entityKind = EntityKind.valueOf(definitionTag.getString("entityKind"));
            } catch (IllegalArgumentException ignored) {
                entityKind = EntityKind.ENTITY;
            }
        }
        if (definitionTag.contains("entityWidth")) {
            entityWidth = definitionTag.getFloat("entityWidth");
        }
        if (definitionTag.contains("entityHeight")) {
            entityHeight = definitionTag.getFloat("entityHeight");
        }
        if (definitionTag.contains("clientTrackingRange")) {
            clientTrackingRange = definitionTag.getInt("clientTrackingRange");
        }
        if (definitionTag.contains("updateInterval")) {
            updateInterval = definitionTag.getInt("updateInterval");
        }
        return this;
    }

    @Override
    public void onRegistry(RegisterEvent event) {
        event.register(ForgeRegistries.ENTITY_TYPES.getRegistryKey(), helper -> helper.register(id(), entityType = createEntityType()));
        event.register(ForgeRegistries.ITEMS.getRegistryKey(), helper -> helper.register(id(), entityItem = createItem()));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initRenderer(EntityRenderersEvent.RegisterRenderers event) {
        entityBlockRenderer = createBlockRenderer();
        entityItemRenderer = createItemRenderer();
        if (entityType != null) {
            event.registerEntityRenderer((EntityType<Entity>) entityType, EntityMachineRenderer::new);
        }
    }

    public void registerEntityAttributes(EntityAttributeCreationEvent event) {
        if (entityKind == EntityKind.LIVING && entityType != null) {
            event.put((EntityType<? extends LivingEntity>) entityType, LivingEntity.createLivingAttributes().build());
        }
    }

    public EntityType<?> createEntityType() {
        var width = Math.max(0.01f, entityWidth);
        var height = Math.max(0.01f, entityHeight);
        var trackingRange = Math.max(1, clientTrackingRange);
        var interval = Math.max(1, updateInterval);
        if (entityKind == EntityKind.LIVING) {
            return EntityType.Builder.<RegisteredMBDLivingMachineEntity>of(
                            (type, level) -> new RegisteredMBDLivingMachineEntity(type, level, this), MobCategory.MISC)
                    .sized(width, height)
                    .clientTrackingRange(trackingRange)
                    .updateInterval(interval)
                    .build(id().toString());
        }
        return EntityType.Builder.<RegisteredMBDMachineEntity>of(
                        (type, level) -> new RegisteredMBDMachineEntity(type, level, this), MobCategory.MISC)
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(interval)
                .build(id().toString());
    }

    public Item createItem() {
        return new EntityMachineItem(this, itemProperties().apply(new Item.Properties()));
    }

    @Override
    public Item item() {
        return entityItem;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public IRenderer blockRenderer() {
        return entityBlockRenderer;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public IRenderer itemRenderer() {
        return entityItemRenderer;
    }

    public EntityType<?> entityType() {
        return entityType;
    }

    public EntityType<?> getOrCreateEntityType() {
        if (entityType == null) {
            entityType = createEntityType();
        }
        return entityType;
    }

    public EntityKind entityKind() {
        return entityKind;
    }

    public float entityWidth() {
        return entityWidth;
    }

    public float entityHeight() {
        return entityHeight;
    }

    @Override
    public MBDEntityMachine createMachine(IMachineBlockEntity blockEntity) {
        return new MBDEntityMachine(blockEntity, this);
    }

    @Nullable
    public MBDEntityMachine getMachine(Entity entity) {
        if (entity instanceof IMachineEntity machineEntity && machineEntity.getMetaMachine() instanceof MBDEntityMachine machine) {
            return machine.getDefinition() == this ? machine : null;
        }
        return null;
    }

    @Override
    public String getDescriptionId() {
        return id().toLanguageKey("entity");
    }

    @Override
    public ItemStack asStack(int count) {
        return new ItemStack(item(), count);
    }

    @Override
    protected Component getMachineName(MBDMachine machine) {
        return Component.translatable(id().toLanguageKey("machine"));
    }

    public boolean isTraitSupportedOnEntity(TraitDefinition traitDefinition) {
        return entityTraitFilter.test(traitDefinition);
    }

    public EntityMachineDefinition entityTraitFilter(Predicate<TraitDefinition> entityTraitFilter) {
        this.entityTraitFilter = entityTraitFilter == null ? trait -> false : entityTraitFilter;
        return this;
    }

    public Optional<Direction> getEntityFrontFacing(Entity entity) {
        return Optional.ofNullable(entity.getDirection());
    }

    public void setEntityFrontFacing(Entity entity, Direction facing) {
        entity.setYRot(facing.toYRot());
        entity.setYHeadRot(facing.toYRot());
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    public static class Builder extends MBDMachineDefinition.Builder {
        private Predicate<TraitDefinition> entityTraitFilter = MBDEntityMachine::isDefaultEntitySafeTrait;
        private EntityKind entityKind = EntityKind.ENTITY;
        private float entityWidth = 0.98f;
        private float entityHeight = 0.98f;
        private int clientTrackingRange = 8;
        private int updateInterval = 3;

        protected Builder() {
        }

        public Builder entityTraitFilter(Predicate<TraitDefinition> entityTraitFilter) {
            this.entityTraitFilter = entityTraitFilter == null ? trait -> false : entityTraitFilter;
            return this;
        }

        @Override
        public Builder setMaxParallel(int maxParallel) {
            super.setMaxParallel(maxParallel);
            return this;
        }

        @Override
        public Builder maxParallel(int maxParallel) {
            super.maxParallel(maxParallel);
            return this;
        }

        public EntityMachineDefinition build() {
            var definition = new EntityMachineDefinition(id, rootState, blockProperties, itemProperties, machineSettings, recipeLogicSettings)
                    .entityTraitFilter(entityTraitFilter);
            definition.entityKind = entityKind == null ? EntityKind.ENTITY : entityKind;
            definition.entityWidth = entityWidth;
            definition.entityHeight = entityHeight;
            definition.clientTrackingRange = clientTrackingRange;
            definition.updateInterval = updateInterval;
            return definition;
        }
    }
}
