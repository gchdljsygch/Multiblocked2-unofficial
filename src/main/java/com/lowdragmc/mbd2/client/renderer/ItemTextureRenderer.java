package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider;
import com.lowdragmc.lowdraglib.client.renderer.ISerializableRenderer;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.StringConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.configurator.WrapperConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.gui.editor.texture.IRendererSlotTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Serializable item-only renderer backed by one PNG texture.
 *
 * <p>The texture is baked through vanilla's {@code item/generated} pipeline, so transparent pixels produce the same
 * thin extruded edges and display transforms as a normal Minecraft item. This renderer intentionally contributes no
 * block quads; machine block rendering continues to use the machine-state renderer.</p>
 */
@LDLRegisterClient(name = "item_texture", group = "renderer")
@OnlyIn(Dist.CLIENT)
public class ItemTextureRenderer implements ISerializableRenderer {
    public static final ResourceLocation DEFAULT_TEXTURE = MBD2.id("item/mbd_multiblock_builder");
    private static final ResourceLocation GENERATED_MODEL_LOCATION = MBD2.id("item/generated_texture");

    @Persisted
    private ResourceLocation texture = DEFAULT_TEXTURE;

    @Nullable
    private BakedModel itemModel;

    /**
     * Creates a renderer using the built-in MBD item texture.
     */
    public ItemTextureRenderer() {
    }

    /**
     * Creates and registers a renderer for a logical atlas texture location.
     *
     * @param texture texture location without the {@code textures/} prefix or {@code .png} suffix
     */
    public ItemTextureRenderer(ResourceLocation texture) {
        setTexture(texture);
        initRenderer();
    }

    /**
     * Returns the logical texture location used as generated-model layer zero.
     *
     * @return non-null texture location without a file prefix or suffix
     */
    public ResourceLocation texture() {
        return texture == null ? DEFAULT_TEXTURE : texture;
    }

    /**
     * Replaces the item texture and invalidates the baked item model.
     *
     * @param texture logical texture location without the {@code textures/} prefix or {@code .png} suffix
     */
    @ConfigSetter(field = "texture")
    public void setTexture(ResourceLocation texture) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.itemModel = null;
    }

    @Override
    public void initRenderer() {
        if (texture == null) {
            texture = DEFAULT_TEXTURE;
        }
        if (LDLib.isClient()) {
            registerEvent();
        }
    }

    @Override
    public void renderItem(ItemStack stack, ItemDisplayContext transformType, boolean leftHand, PoseStack poseStack,
                           MultiBufferSource buffer, int combinedLight, int combinedOverlay, BakedModel model) {
        var generatedModel = getItemModel();
        var renderingWasDisabled = IItemRendererProvider.disabled.get();
        IItemRendererProvider.disabled.set(true);
        try {
            Minecraft.getInstance().getItemRenderer().render(stack, transformType, leftHand, poseStack, buffer,
                    combinedLight, combinedOverlay, generatedModel);
        } finally {
            IItemRendererProvider.disabled.set(renderingWasDisabled);
        }
    }

    @Override
    public boolean useBlockLight(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture());
    }

    @Override
    public void onPrepareTextureAtlas(ResourceLocation atlasName, Consumer<ResourceLocation> register) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(atlasName)) {
            itemModel = null;
            register.accept(texture());
        }
    }

    /**
     * Builds the generated item model lazily after the texture atlas is available.
     */
    private BakedModel getItemModel() {
        if (itemModel == null) {
            var sourceModel = BlockModel.fromString("""
                    {
                      "parent": "minecraft:item/generated",
                      "textures": { "layer0": "%s" }
                    }
                    """.formatted(texture()));
            sourceModel.name = GENERATED_MODEL_LOCATION.toString();

            var baker = ModelFactory.getModeBaker();
            sourceModel.resolveParents(baker::getModel);
            var generatedModel = ModelFactory.ITEM_MODEL_GENERATOR.generateBlockModel(Material::sprite, sourceModel);
            itemModel = generatedModel.bake(baker, Material::sprite, BlockModelRotation.X0_Y0,
                    GENERATED_MODEL_LOCATION);
        }
        return itemModel;
    }

    @Override
    public void createPreview(ConfiguratorGroup father) {
        father.addConfigurators(new WrapperConfigurator("ldlib.gui.editor.group.preview",
                new ImageWidget(0, 0, 100, 100, new IRendererSlotTexture(() -> this))));
    }

    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        initRenderer();
        ISerializableRenderer.super.buildConfigurator(father);

        var textureGroup = new ConfiguratorGroup("item_texture_renderer.texture", false);
        textureGroup.setCanCollapse(false);
        textureGroup.setTips("item_texture_renderer.texture.tooltip");

        var textureConfigurator = new StringConfigurator("", () -> texture().toString(), value -> {
            var parsed = ResourceLocation.tryParse(value);
            if (parsed != null) {
                setTexture(parsed);
            }
        }, DEFAULT_TEXTURE.toString(), false);
        textureConfigurator.setResourceLocation(true);

        var selectTexture = new GuiTextureGroup(
                ColorPattern.T_GRAY.rectTexture().setRadius(5),
                new TextTexture("editor.select_from_file"));
        var selectTextureHover = ColorPattern.WHITE.borderTexture(1).setRadius(5);
        var selectButton = new WrapperConfigurator(wrapper -> new ButtonWidget(0, 0, 90, 10, selectTexture, click -> {
            if (Editor.INSTANCE == null) {
                return;
            }
            var workspace = Editor.INSTANCE.getWorkSpace();
            var textureDirectory = new File(workspace, "textures");
            DialogWidget.showFileDialog(Editor.INSTANCE, "item_texture_renderer.texture", textureDirectory, true,
                    DialogWidget.suffixFilter(".png"), selected -> {
                        var selectedTexture = textureLocationFromFile(workspace, selected);
                        if (selectedTexture == null || selectedTexture.equals(texture())) {
                            return;
                        }
                        textureConfigurator.setValue(selectedTexture.toString());
                        setTexture(selectedTexture);
                        wrapper.notifyChanges();
                        Minecraft.getInstance().reloadResourcePacks();
                    });
        }).setHoverTexture(selectTextureHover)).setRemoveTitleBar(true);

        textureGroup.addConfigurators(textureConfigurator, selectButton);
        father.addConfigurators(textureGroup);
    }

    /**
     * Converts a selected workspace PNG into the logical texture location used by block-atlas materials.
     */
    @Nullable
    static ResourceLocation textureLocationFromFile(File workspace, @Nullable File selected) {
        if (workspace == null || selected == null || !selected.isFile()) {
            return null;
        }

        Path textureRoot = new File(workspace, "textures").toPath().toAbsolutePath().normalize();
        Path selectedPath = selected.toPath().toAbsolutePath().normalize();
        if (!selectedPath.startsWith(textureRoot)) {
            return null;
        }

        var relativePath = textureRoot.relativize(selectedPath).toString().replace('\\', '/');
        if (!relativePath.endsWith(".png")) {
            return null;
        }
        relativePath = relativePath.substring(0, relativePath.length() - ".png".length());
        return ResourceLocation.tryBuild(workspace.getName(), relativePath);
    }
}
