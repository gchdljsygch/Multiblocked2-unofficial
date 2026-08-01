package com.lowdragmc.mbd2.client.renderer;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.client.renderer.impl.UIResourceRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.mojang.datafixers.util.Either;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTextureRendererTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsWorkspacePngToLogicalTextureLocation() throws IOException {
        var workspace = temporaryDirectory.resolve("mbd2");
        var textureFile = workspace.resolve("textures/item/machine_icon.png");
        Files.createDirectories(textureFile.getParent());
        Files.createFile(textureFile);

        assertEquals(ResourceLocation.fromNamespaceAndPath("mbd2", "item/machine_icon"),
                ItemTextureRenderer.textureLocationFromFile(workspace.toFile(), textureFile.toFile()));
    }

    @Test
    void rejectsFilesThatAreNotWorkspacePngTextures() throws IOException {
        var workspace = temporaryDirectory.resolve("mbd2");
        var jsonFile = workspace.resolve("textures/item/machine_icon.json");
        var outsideFile = temporaryDirectory.resolve("outside.png");
        Files.createDirectories(jsonFile.getParent());
        Files.createFile(jsonFile);
        Files.createFile(outsideFile);

        assertNull(ItemTextureRenderer.textureLocationFromFile(workspace.toFile(), jsonFile.toFile()));
        assertNull(ItemTextureRenderer.textureLocationFromFile(workspace.toFile(), outsideFile.toFile()));
    }

    @Test
    void pngRendererUsesFlatItemFlagsWithoutChangingModelRendererFlags() {
        var textureRenderer = new ItemTextureRenderer();
        var textureItemRenderer = new MBDItemRenderer(() -> true, () -> true, () -> textureRenderer);

        assertFalse(textureItemRenderer.useBlockLight(null));
        assertFalse(textureItemRenderer.isGui3d());

        IRenderer existingModelRenderer = new IRenderer() {
        };
        var modelItemRenderer = new MBDItemRenderer(() -> true, () -> true, () -> existingModelRenderer);

        assertTrue(modelItemRenderer.useBlockLight(null));
        assertTrue(modelItemRenderer.isGui3d());
    }

    @Test
    void wrappedPngRendererAlsoUsesFlatItemFlags() {
        var textureRenderer = new ItemTextureRenderer();
        IRenderer wrappedRenderer = new UIResourceRenderer(Either.left("test")) {
            @Override
            public IRenderer getRenderer() {
                return textureRenderer;
            }
        };
        var itemRenderer = new MBDItemRenderer(() -> true, () -> true, () -> wrappedRenderer);

        assertFalse(itemRenderer.useBlockLight(null));
        assertFalse(itemRenderer.isGui3d());
    }

    @Test
    void declaresStableRegistrationAndPersistenceContract() throws ReflectiveOperationException {
        var texture = ResourceLocation.fromNamespaceAndPath("example", "item/machine_icon");
        var renderer = new ItemTextureRenderer();
        renderer.setTexture(texture);

        var registration = ItemTextureRenderer.class.getAnnotation(LDLRegisterClient.class);
        var textureField = ItemTextureRenderer.class.getDeclaredField("texture");
        var textureSetter = ItemTextureRenderer.class.getMethod("setTexture", ResourceLocation.class);

        assertEquals("item_texture", registration.name());
        assertEquals("renderer", registration.group());
        assertTrue(textureField.isAnnotationPresent(Persisted.class));
        assertEquals("texture", textureSetter.getAnnotation(ConfigSetter.class).field());
        assertEquals(texture, renderer.texture());
    }
}
