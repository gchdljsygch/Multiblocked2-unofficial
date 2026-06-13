package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Getter
@Cancelable
@LDLRegister(name = "MachineJadeTooltipEvent", group = "MachineEvent")
public class MachineJadeTooltipEvent extends MachineEvent {
    @Nullable
    @GraphParameterGet
    public final Player player;
    @GraphParameterGet(displayName = "provider uid")
    public final ResourceLocation providerUid;
    @GraphParameterSet(identity = "tooltip", displayName = "tooltip", type = String.class)
    public String customText = "";
    @GraphParameterSet(identity = "tooltip.icon", displayName = "tooltip icon", type = ItemStack.class)
    public ItemStack customIcon = ItemStack.EMPTY;
    @GraphParameterSet(identity = "tooltip.same_line", displayName = "icon and text same line", type = Boolean.class)
    public boolean iconAndTextSameLine = true;
    private final List<TooltipLine> tooltipLines = new ArrayList<>();

    public MachineJadeTooltipEvent(MBDMachine machine, @Nullable Player player, ResourceLocation providerUid) {
        super(machine);
        this.player = player;
        this.providerUid = providerUid;
    }

    public void add(Component component) {
        if (component != null) {
            tooltipLines.add(TooltipLine.text(component));
        }
    }

    public void addText(String text) {
        if (text != null && !text.isBlank()) {
            add(Component.literal(text));
        }
    }

    public void addTranslatable(String key, Object... args) {
        if (key != null && !key.isBlank()) {
            add(Component.translatable(key, args));
        }
    }

    public void addIcon(ItemLike item) {
        if (item != null) {
            addIcon(new ItemStack(item));
        }
    }

    public void addIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            tooltipLines.add(TooltipLine.icon(stack));
        }
    }

    public void addIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::addIcon);
    }

    public void addSmallIcon(ItemLike item) {
        if (item != null) {
            addSmallIcon(new ItemStack(item));
        }
    }

    public void addSmallIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            tooltipLines.add(TooltipLine.smallIcon(stack));
        }
    }

    public void addSmallIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::addSmallIcon);
    }

    public void addJadeIcon(String iconName) {
        if (iconName != null && !iconName.isBlank()) {
            tooltipLines.add(TooltipLine.jadeIcon(iconName));
        }
    }

    public void addIconText(ItemLike item, String text) {
        if (item != null && text != null && !text.isBlank()) {
            addIconText(new ItemStack(item), Component.literal(text));
        }
    }

    public void addIconText(ItemStack stack, String text) {
        if (text != null && !text.isBlank()) {
            addIconText(stack, Component.literal(text));
        }
    }

    public void addIconText(String itemId, String text) {
        resolveItem(itemId).ifPresent(stack -> addIconText(stack, text));
    }

    public void addIconText(ItemLike item, Component component) {
        if (item != null) {
            addIconText(new ItemStack(item), component);
        }
    }

    public void addIconText(ItemStack stack, Component component) {
        if (isValidIcon(stack) && component != null) {
            tooltipLines.add(TooltipLine.iconText(stack, component));
        }
    }

    public void addIconText(String itemId, Component component) {
        resolveItem(itemId).ifPresent(stack -> addIconText(stack, component));
    }

    public void addJadeIconText(String iconName, String text) {
        if (text != null && !text.isBlank()) {
            addJadeIconText(iconName, Component.literal(text));
        }
    }

    public void addJadeIconText(String iconName, Component component) {
        if (iconName != null && !iconName.isBlank() && component != null) {
            tooltipLines.add(TooltipLine.jadeIconText(iconName, component));
        }
    }

    public void append(Component component) {
        if (component != null) {
            getOrCreateLastLine().addText(component);
        }
    }

    public void appendText(String text) {
        if (text != null && !text.isBlank()) {
            append(Component.literal(text));
        }
    }

    public void appendTranslatable(String key, Object... args) {
        if (key != null && !key.isBlank()) {
            append(Component.translatable(key, args));
        }
    }

    public void appendIcon(ItemLike item) {
        if (item != null) {
            appendIcon(new ItemStack(item));
        }
    }

    public void appendIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            getOrCreateLastLine().addIcon(stack);
        }
    }

    public void appendIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::appendIcon);
    }

    public void appendSmallIcon(ItemLike item) {
        if (item != null) {
            appendSmallIcon(new ItemStack(item));
        }
    }

    public void appendSmallIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            getOrCreateLastLine().addSmallIcon(stack);
        }
    }

    public void appendSmallIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::appendSmallIcon);
    }

    public void appendJadeIcon(String iconName) {
        if (iconName != null && !iconName.isBlank()) {
            getOrCreateLastLine().addJadeIcon(iconName);
        }
    }

    public void appendSpacer(int width, int height) {
        getOrCreateLastLine().addSpacer(width, height);
    }

    public int getTooltipLineCount() {
        return tooltipLines.size();
    }

    public int getLineCount() {
        return getTooltipLineCount();
    }

    public int lineCount() {
        return getTooltipLineCount();
    }

    public boolean hasTooltipLine(int index) {
        return index >= 0 && index < tooltipLines.size();
    }

    public boolean hasLine(int index) {
        return hasTooltipLine(index);
    }

    public int clearLines() {
        int removed = tooltipLines.size();
        tooltipLines.clear();
        return removed;
    }

    public int clearTooltipLines() {
        return clearLines();
    }

    public boolean removeLine(int index) {
        if (!hasTooltipLine(index)) {
            return false;
        }
        tooltipLines.remove(index);
        return true;
    }

    public boolean removeTooltipLine(int index) {
        return removeLine(index);
    }

    public boolean removeLastLine() {
        return removeLine(tooltipLines.size() - 1);
    }

    public boolean removeFirstLine() {
        return removeLine(0);
    }

    public int removeLines(int fromIndex, int count) {
        if (count <= 0 || tooltipLines.isEmpty()) {
            return 0;
        }
        int start = Math.max(0, fromIndex);
        if (start >= tooltipLines.size()) {
            return 0;
        }
        int end = count > tooltipLines.size() - start ? tooltipLines.size() : start + count;
        int removed = end - start;
        tooltipLines.subList(start, end).clear();
        return removed;
    }

    public int removeLinesFrom(int fromIndex) {
        return removeLines(fromIndex, tooltipLines.size());
    }

    public int removeLinesContaining(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return removeLinesIf(line -> line.containsText(text));
    }

    public int removeLinesContaining(String text, boolean ignoreCase) {
        if (!ignoreCase) {
            return removeLinesContaining(text);
        }
        if (text == null || text.isBlank()) {
            return 0;
        }
        var lowerText = text.toLowerCase(Locale.ROOT);
        return removeLinesIf(line -> line.getText().toLowerCase(Locale.ROOT).contains(lowerText));
    }

    public int removeExactTextLines(String text) {
        if (text == null) {
            return 0;
        }
        return removeLinesIf(line -> line.getText().equals(text));
    }

    public int removeEmptyLines() {
        return removeLinesIf(TooltipLine::isEmpty);
    }

    public int removeLinesIf(Predicate<TooltipLine> predicate) {
        if (predicate == null || tooltipLines.isEmpty()) {
            return 0;
        }
        int size = tooltipLines.size();
        tooltipLines.removeIf(predicate);
        return size - tooltipLines.size();
    }

    public List<Component> getTooltips() {
        return tooltipLines.stream()
                .map(TooltipLine::asComponent)
                .toList();
    }

    public boolean isShiftKeyDown() {
        return player != null && player.isShiftKeyDown();
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("player")).ifPresent(p -> p.setValue(player));
        Optional.ofNullable(exposedParameters.get("providerUid")).ifPresent(p -> p.setValue(providerUid));
    }

    @Override
    public void gatherParameters(Map<String, ExposedParameter> exposedParameters) {
        super.gatherParameters(exposedParameters);
        var text = Optional.ofNullable(exposedParameters.get("tooltip"))
                .map(ExposedParameter::getValue)
                .map(Object::toString)
                .filter(value -> !value.isBlank());
        var icon = Optional.ofNullable(exposedParameters.get("tooltip.icon"))
                .map(ExposedParameter::getValue)
                .filter(ItemStack.class::isInstance)
                .map(ItemStack.class::cast)
                .filter(MachineJadeTooltipEvent::isValidIcon);
        var sameLine = Optional.ofNullable(exposedParameters.get("tooltip.same_line"))
                .map(ExposedParameter::getValue)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(true);
        if (icon.isPresent() && text.isPresent()) {
            if (sameLine) {
                addIconText(icon.get(), text.get());
            } else {
                addIcon(icon.get());
                addText(text.get());
            }
        } else {
            icon.ifPresent(this::addIcon);
            text.ifPresent(this::addText);
        }
    }

    private TooltipLine getOrCreateLastLine() {
        if (tooltipLines.isEmpty()) {
            tooltipLines.add(new TooltipLine());
        }
        return tooltipLines.get(tooltipLines.size() - 1);
    }

    private static Optional<ItemStack> resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(itemId))
                .map(ForgeRegistries.ITEMS::getValue)
                .map(ItemStack::new)
                .filter(MachineJadeTooltipEvent::isValidIcon);
    }

    private static boolean isValidIcon(ItemStack stack) {
        return stack != null && !stack.isEmpty();
    }

    public static class TooltipLine {
        @Getter
        private final List<TooltipPart> parts = new ArrayList<>();

        public static TooltipLine text(Component component) {
            return new TooltipLine().addText(component);
        }

        public static TooltipLine icon(ItemStack stack) {
            return new TooltipLine().addIcon(stack);
        }

        public static TooltipLine smallIcon(ItemStack stack) {
            return new TooltipLine().addSmallIcon(stack);
        }

        public static TooltipLine jadeIcon(String iconName) {
            return new TooltipLine().addJadeIcon(iconName);
        }

        public static TooltipLine iconText(ItemStack stack, Component component) {
            return new TooltipLine()
                    .addIcon(stack)
                    .addSpacer(2, 0)
                    .addText(component);
        }

        public static TooltipLine jadeIconText(String iconName, Component component) {
            return new TooltipLine()
                    .addJadeIcon(iconName)
                    .addSpacer(2, 0)
                    .addText(component);
        }

        public TooltipLine addText(Component component) {
            if (component != null) {
                parts.add(new TextPart(component));
            }
            return this;
        }

        public TooltipLine addIcon(ItemStack stack) {
            if (isValidIcon(stack)) {
                parts.add(new ItemIconPart(stack.copy(), false));
            }
            return this;
        }

        public TooltipLine addSmallIcon(ItemStack stack) {
            if (isValidIcon(stack)) {
                parts.add(new ItemIconPart(stack.copy(), true));
            }
            return this;
        }

        public TooltipLine addJadeIcon(String iconName) {
            if (iconName != null && !iconName.isBlank()) {
                parts.add(new JadeIconPart(iconName));
            }
            return this;
        }

        public TooltipLine addSpacer(int width, int height) {
            if (width > 0 || height > 0) {
                parts.add(new SpacerPart(Math.max(0, width), Math.max(0, height)));
            }
            return this;
        }

        public Component asComponent() {
            MutableComponent component = Component.empty();
            for (var part : parts) {
                if (part instanceof TextPart textPart) {
                    component.append(textPart.component());
                }
            }
            return component;
        }

        public String getText() {
            return asComponent().getString();
        }

        public boolean containsText(String text) {
            return text != null && getText().contains(text);
        }

        public boolean isEmpty() {
            return parts.isEmpty() || parts.stream().allMatch(SpacerPart.class::isInstance);
        }
    }

    public interface TooltipPart {
    }

    public record TextPart(Component component) implements TooltipPart {
    }

    public record ItemIconPart(ItemStack stack, boolean small) implements TooltipPart {
    }

    public record JadeIconPart(String iconName) implements TooltipPart {
    }

    public record SpacerPart(int width, int height) implements TooltipPart {
    }
}
