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

/**
 * Fired while the Jade integration builds a tooltip for a machine.
 * <p>
 * Canceling this event prevents MBD2's Jade provider from adding its tooltip
 * lines. Handlers can either use the graph parameters {@code tooltip},
 * {@code tooltip.icon}, and {@code tooltip.same_line}, or call the Java helper
 * methods to build rich lines containing text, item icons, Jade icons, and
 * spacers.
 * <p>
 * The event is client-facing tooltip data. Do not perform server-authoritative
 * machine mutation from this hook.
 */
@Getter
@Cancelable
@LDLRegister(name = "MachineJadeTooltipEvent", group = "MachineEvent")
public class MachineJadeTooltipEvent extends MachineEvent {
    /**
     * Player viewing the tooltip, if Jade supplied one.
     */
    @Nullable
    @GraphParameterGet
    public final Player player;
    /**
     * Jade provider id currently building the tooltip.
     */
    @GraphParameterGet(displayName = "provider uid")
    public final ResourceLocation providerUid;
    /**
     * Simple text line accepted from graph output.
     */
    @GraphParameterSet(identity = "tooltip", displayName = "tooltip", type = String.class)
    public String customText = "";
    /**
     * Optional icon accepted from graph output.
     */
    @GraphParameterSet(identity = "tooltip.icon", displayName = "tooltip icon", type = ItemStack.class)
    public ItemStack customIcon = ItemStack.EMPTY;
    /**
     * Whether graph-provided text and icon should be combined into one line.
     */
    @GraphParameterSet(identity = "tooltip.same_line", displayName = "icon and text same line", type = Boolean.class)
    public boolean iconAndTextSameLine = true;
    private final List<TooltipLine> tooltipLines = new ArrayList<>();

    /**
     * Creates a tooltip event for one Jade provider pass.
     * <p>
     * Handlers can mutate {@link #tooltipLines} through the helper methods or use graph output parameters gathered after
     * graph execution. Canceling the event prevents the provider from consuming the accumulated lines.
     *
     * @param machine     machine being inspected
     * @param player      viewing player, or {@code null} when Jade did not provide one
     * @param providerUid Jade provider id building this tooltip
     */
    public MachineJadeTooltipEvent(MBDMachine machine, @Nullable Player player, ResourceLocation providerUid) {
        super(machine);
        this.player = player;
        this.providerUid = providerUid;
    }

    /**
     * Appends a new text-only tooltip line.
     *
     * @param component component to append; {@code null} is ignored
     */
    public void add(Component component) {
        if (component != null) {
            tooltipLines.add(TooltipLine.text(component));
        }
    }

    /**
     * Appends a new literal text line.
     *
     * @param text non-blank literal text
     */
    public void addText(String text) {
        if (text != null && !text.isBlank()) {
            add(Component.literal(text));
        }
    }

    /**
     * Appends a new translatable text line.
     *
     * @param key  translation key; blank keys are ignored
     * @param args translation arguments
     */
    public void addTranslatable(String key, Object... args) {
        if (key != null && !key.isBlank()) {
            add(Component.translatable(key, args));
        }
    }

    /**
     * Appends a new full-size item icon line from an item-like object.
     *
     * @param item item to render; {@code null} is ignored
     */
    public void addIcon(ItemLike item) {
        if (item != null) {
            addIcon(new ItemStack(item));
        }
    }

    /**
     * Appends a new full-size item icon line.
     *
     * @param stack icon stack; empty stacks are ignored and copied internally
     */
    public void addIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            tooltipLines.add(TooltipLine.icon(stack));
        }
    }

    /**
     * Appends a new full-size item icon line by registry id.
     *
     * @param itemId item id such as {@code minecraft:iron_ingot}
     */
    public void addIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::addIcon);
    }

    /**
     * Appends a new small item icon line from an item-like object.
     *
     * @param item item to render; {@code null} is ignored
     */
    public void addSmallIcon(ItemLike item) {
        if (item != null) {
            addSmallIcon(new ItemStack(item));
        }
    }

    /**
     * Appends a new small item icon line.
     *
     * @param stack icon stack; empty stacks are ignored and copied internally
     */
    public void addSmallIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            tooltipLines.add(TooltipLine.smallIcon(stack));
        }
    }

    /**
     * Appends a new small item icon line by registry id.
     *
     * @param itemId item id such as {@code minecraft:iron_ingot}; invalid or blank ids are ignored
     */
    public void addSmallIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::addSmallIcon);
    }

    /**
     * Appends a new Jade built-in icon line.
     *
     * @param iconName Jade icon name; blank names are ignored
     */
    public void addJadeIcon(String iconName) {
        if (iconName != null && !iconName.isBlank()) {
            tooltipLines.add(TooltipLine.jadeIcon(iconName));
        }
    }

    /**
     * Appends a line with a full-size item icon and literal text.
     *
     * @param item item to render; {@code null} is ignored
     * @param text non-blank literal text
     */
    public void addIconText(ItemLike item, String text) {
        if (item != null && text != null && !text.isBlank()) {
            addIconText(new ItemStack(item), Component.literal(text));
        }
    }

    /**
     * Appends a line with a full-size item icon stack and literal text.
     *
     * @param stack item icon stack; empty stacks are ignored
     * @param text  non-blank literal text
     */
    public void addIconText(ItemStack stack, String text) {
        if (text != null && !text.isBlank()) {
            addIconText(stack, Component.literal(text));
        }
    }

    /**
     * Appends a line with a full-size item icon resolved from a registry id and literal text.
     *
     * @param itemId item id such as {@code minecraft:iron_ingot}; invalid or blank ids are ignored
     * @param text   non-blank literal text
     */
    public void addIconText(String itemId, String text) {
        resolveItem(itemId).ifPresent(stack -> addIconText(stack, text));
    }

    /**
     * Appends a line with a full-size item icon and component text.
     *
     * @param item      item to render; {@code null} is ignored
     * @param component text component; {@code null} is ignored
     */
    public void addIconText(ItemLike item, Component component) {
        if (item != null) {
            addIconText(new ItemStack(item), component);
        }
    }

    /**
     * Appends a line with a full-size item icon, spacer, and text.
     *
     * @param stack     item icon stack
     * @param component text component
     */
    public void addIconText(ItemStack stack, Component component) {
        if (isValidIcon(stack) && component != null) {
            tooltipLines.add(TooltipLine.iconText(stack, component));
        }
    }

    /**
     * Appends a line with a full-size item icon resolved from a registry id and component text.
     *
     * @param itemId    item id such as {@code minecraft:iron_ingot}; invalid or blank ids are ignored
     * @param component text component; {@code null} is ignored
     */
    public void addIconText(String itemId, Component component) {
        resolveItem(itemId).ifPresent(stack -> addIconText(stack, component));
    }

    /**
     * Appends a line with a Jade built-in icon and literal text.
     *
     * @param iconName Jade icon name; blank names are ignored
     * @param text     non-blank literal text
     */
    public void addJadeIconText(String iconName, String text) {
        if (text != null && !text.isBlank()) {
            addJadeIconText(iconName, Component.literal(text));
        }
    }

    /**
     * Appends a line with a Jade icon, spacer, and text.
     *
     * @param iconName  Jade icon name
     * @param component text component
     */
    public void addJadeIconText(String iconName, Component component) {
        if (iconName != null && !iconName.isBlank() && component != null) {
            tooltipLines.add(TooltipLine.jadeIconText(iconName, component));
        }
    }

    /**
     * Appends text to the current last line, creating a line when necessary.
     *
     * @param component component to append; {@code null} is ignored
     */
    public void append(Component component) {
        if (component != null) {
            getOrCreateLastLine().addText(component);
        }
    }

    /**
     * Appends literal text to the current last line, creating one if necessary.
     *
     * @param text non-blank literal text
     */
    public void appendText(String text) {
        if (text != null && !text.isBlank()) {
            append(Component.literal(text));
        }
    }

    /**
     * Appends translatable text to the current last line, creating one if necessary.
     *
     * @param key  translation key; blank keys are ignored
     * @param args translation arguments
     */
    public void appendTranslatable(String key, Object... args) {
        if (key != null && !key.isBlank()) {
            append(Component.translatable(key, args));
        }
    }

    /**
     * Appends a full-size item icon to the current last line from an item-like object.
     *
     * @param item item to render; {@code null} is ignored
     */
    public void appendIcon(ItemLike item) {
        if (item != null) {
            appendIcon(new ItemStack(item));
        }
    }

    /**
     * Appends a full-size item icon to the current last line.
     *
     * @param stack item icon stack
     */
    public void appendIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            getOrCreateLastLine().addIcon(stack);
        }
    }

    /**
     * Appends a full-size item icon to the current last line by registry id.
     *
     * @param itemId item id such as {@code minecraft:iron_ingot}; invalid or blank ids are ignored
     */
    public void appendIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::appendIcon);
    }

    /**
     * Appends a small item icon to the current last line from an item-like object.
     *
     * @param item item to render; {@code null} is ignored
     */
    public void appendSmallIcon(ItemLike item) {
        if (item != null) {
            appendSmallIcon(new ItemStack(item));
        }
    }

    /**
     * Appends a small item icon to the current last line.
     *
     * @param stack item icon stack
     */
    public void appendSmallIcon(ItemStack stack) {
        if (isValidIcon(stack)) {
            getOrCreateLastLine().addSmallIcon(stack);
        }
    }

    /**
     * Appends a small item icon to the current last line by registry id.
     *
     * @param itemId item id such as {@code minecraft:iron_ingot}; invalid or blank ids are ignored
     */
    public void appendSmallIcon(String itemId) {
        resolveItem(itemId).ifPresent(this::appendSmallIcon);
    }

    /**
     * Appends a Jade icon to the current last line.
     *
     * @param iconName Jade icon name
     */
    public void appendJadeIcon(String iconName) {
        if (iconName != null && !iconName.isBlank()) {
            getOrCreateLastLine().addJadeIcon(iconName);
        }
    }

    /**
     * Appends spacing to the current last line.
     *
     * @param width  horizontal spacer pixels; negative values are clamped to
     *               {@code 0}
     * @param height vertical spacer pixels; negative values are clamped to
     *               {@code 0}
     */
    public void appendSpacer(int width, int height) {
        getOrCreateLastLine().addSpacer(width, height);
    }

    /**
     * Returns the number of accumulated rich tooltip lines.
     *
     * @return current tooltip line count
     */
    public int getTooltipLineCount() {
        return tooltipLines.size();
    }

    /**
     * Alias for {@link #getTooltipLineCount()}.
     *
     * @return current tooltip line count
     */
    public int getLineCount() {
        return getTooltipLineCount();
    }

    /**
     * Alias for {@link #getTooltipLineCount()} for script-style access.
     *
     * @return current tooltip line count
     */
    public int lineCount() {
        return getTooltipLineCount();
    }

    /**
     * Checks whether a tooltip line index is valid.
     *
     * @param index zero-based line index
     * @return {@code true} when {@code index} points at an existing line
     */
    public boolean hasTooltipLine(int index) {
        return index >= 0 && index < tooltipLines.size();
    }

    /**
     * Alias for {@link #hasTooltipLine(int)}.
     *
     * @param index zero-based line index
     * @return {@code true} when {@code index} points at an existing line
     */
    public boolean hasLine(int index) {
        return hasTooltipLine(index);
    }

    /**
     * Removes all tooltip lines.
     *
     * @return number of removed lines
     */
    public int clearLines() {
        int removed = tooltipLines.size();
        tooltipLines.clear();
        return removed;
    }

    /**
     * Alias for {@link #clearLines()}.
     *
     * @return number of removed lines
     */
    public int clearTooltipLines() {
        return clearLines();
    }

    /**
     * Removes one tooltip line by index.
     *
     * @param index zero-based line index
     * @return {@code true} if a line was removed
     */
    public boolean removeLine(int index) {
        if (!hasTooltipLine(index)) {
            return false;
        }
        tooltipLines.remove(index);
        return true;
    }

    /**
     * Alias for {@link #removeLine(int)}.
     *
     * @param index zero-based line index
     * @return {@code true} if a line was removed
     */
    public boolean removeTooltipLine(int index) {
        return removeLine(index);
    }

    /**
     * Removes the current last tooltip line.
     *
     * @return {@code true} if a line was removed
     */
    public boolean removeLastLine() {
        return removeLine(tooltipLines.size() - 1);
    }

    /**
     * Removes the first tooltip line.
     *
     * @return {@code true} if a line was removed
     */
    public boolean removeFirstLine() {
        return removeLine(0);
    }

    /**
     * Removes a range of tooltip lines.
     *
     * @param fromIndex zero-based start index; negative values are treated as
     *                  {@code 0}
     * @param count     maximum number of lines to remove
     * @return number of removed lines
     */
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

    /**
     * Removes every tooltip line from a start index through the end of the list.
     *
     * @param fromIndex zero-based start index; negative values are treated as {@code 0}
     * @return number of removed lines
     */
    public int removeLinesFrom(int fromIndex) {
        return removeLines(fromIndex, tooltipLines.size());
    }

    /**
     * Removes lines whose text representation contains a case-sensitive substring.
     *
     * @param text non-blank substring to search for
     * @return number of removed lines
     */
    public int removeLinesContaining(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return removeLinesIf(line -> line.containsText(text));
    }

    /**
     * Removes lines whose text representation contains a substring.
     *
     * @param text       non-blank substring to search for
     * @param ignoreCase whether the match should ignore case using {@link Locale#ROOT}
     * @return number of removed lines
     */
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

    /**
     * Removes lines whose text representation exactly equals the supplied string.
     *
     * @param text exact text to match; {@code null} removes nothing
     * @return number of removed lines
     */
    public int removeExactTextLines(String text) {
        if (text == null) {
            return 0;
        }
        return removeLinesIf(line -> line.getText().equals(text));
    }

    /**
     * Removes lines that contain no renderable text or icon parts.
     *
     * @return number of removed empty lines
     */
    public int removeEmptyLines() {
        return removeLinesIf(TooltipLine::isEmpty);
    }

    /**
     * Removes lines matching a predicate.
     *
     * @param predicate line predicate; {@code null} removes nothing
     * @return number of removed lines
     */
    public int removeLinesIf(Predicate<TooltipLine> predicate) {
        if (predicate == null || tooltipLines.isEmpty()) {
            return 0;
        }
        int size = tooltipLines.size();
        tooltipLines.removeIf(predicate);
        return size - tooltipLines.size();
    }

    /**
     * Returns the current tooltip lines converted to text components.
     * <p>
     * Non-text visual parts are ignored in this representation; Jade rendering
     * code can inspect {@link #getTooltipLines()} for rich parts.
     *
     * @return immutable list of text components for the current lines
     */
    public List<Component> getTooltips() {
        return tooltipLines.stream()
                .map(TooltipLine::asComponent)
                .toList();
    }

    /**
     * Checks whether the viewing player is holding Shift.
     *
     * @return {@code true} when a player exists and is sneaking/holding Shift
     */
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

    /**
     * One rich tooltip line composed from text, icons, and spacer parts.
     */
    public static class TooltipLine {
        @Getter
        private final List<TooltipPart> parts = new ArrayList<>();

        /**
         * Creates a tooltip line containing one text component.
         *
         * @param component component to add; {@code null} creates an empty line
         * @return new tooltip line
         */
        public static TooltipLine text(Component component) {
            return new TooltipLine().addText(component);
        }

        /**
         * Creates a tooltip line containing one full-size item icon.
         *
         * @param stack icon stack; copied when valid
         * @return new tooltip line
         */
        public static TooltipLine icon(ItemStack stack) {
            return new TooltipLine().addIcon(stack);
        }

        /**
         * Creates a tooltip line containing one small item icon.
         *
         * @param stack icon stack; copied when valid
         * @return new tooltip line
         */
        public static TooltipLine smallIcon(ItemStack stack) {
            return new TooltipLine().addSmallIcon(stack);
        }

        /**
         * Creates a tooltip line containing one Jade built-in icon.
         *
         * @param iconName Jade icon name; blank names create an empty line
         * @return new tooltip line
         */
        public static TooltipLine jadeIcon(String iconName) {
            return new TooltipLine().addJadeIcon(iconName);
        }

        /**
         * Creates a tooltip line containing a full-size item icon, spacer, and text.
         *
         * @param stack     icon stack; copied when valid
         * @param component text component; {@code null} is ignored
         * @return new tooltip line
         */
        public static TooltipLine iconText(ItemStack stack, Component component) {
            return new TooltipLine()
                    .addIcon(stack)
                    .addSpacer(2, 0)
                    .addText(component);
        }

        /**
         * Creates a tooltip line containing a Jade built-in icon, spacer, and text.
         *
         * @param iconName  Jade icon name; blank names skip the icon part
         * @param component text component; {@code null} is ignored
         * @return new tooltip line
         */
        public static TooltipLine jadeIconText(String iconName, Component component) {
            return new TooltipLine()
                    .addJadeIcon(iconName)
                    .addSpacer(2, 0)
                    .addText(component);
        }

        /**
         * Appends text to this line.
         *
         * @param component component to append; {@code null} is ignored
         * @return this line for chaining
         */
        public TooltipLine addText(Component component) {
            if (component != null) {
                parts.add(new TextPart(component));
            }
            return this;
        }

        /**
         * Appends a full-size item icon to this line.
         *
         * @param stack icon stack; copied when valid
         * @return this line for chaining
         */
        public TooltipLine addIcon(ItemStack stack) {
            if (isValidIcon(stack)) {
                parts.add(new ItemIconPart(stack.copy(), false));
            }
            return this;
        }

        /**
         * Appends a small item icon to this line.
         *
         * @param stack icon stack; copied when valid
         * @return this line for chaining
         */
        public TooltipLine addSmallIcon(ItemStack stack) {
            if (isValidIcon(stack)) {
                parts.add(new ItemIconPart(stack.copy(), true));
            }
            return this;
        }

        /**
         * Appends a Jade built-in icon to this line.
         *
         * @param iconName Jade icon name; blank names are ignored
         * @return this line for chaining
         */
        public TooltipLine addJadeIcon(String iconName) {
            if (iconName != null && !iconName.isBlank()) {
                parts.add(new JadeIconPart(iconName));
            }
            return this;
        }

        /**
         * Appends a spacer to this line.
         *
         * @param width  horizontal spacer pixels; negative values are clamped to {@code 0}
         * @param height vertical spacer pixels; negative values are clamped to {@code 0}
         * @return this line for chaining
         */
        public TooltipLine addSpacer(int width, int height) {
            if (width > 0 || height > 0) {
                parts.add(new SpacerPart(Math.max(0, width), Math.max(0, height)));
            }
            return this;
        }

        /**
         * Converts text parts on this line into one component.
         * <p>
         * Icon and spacer parts are intentionally omitted; callers that need rich rendering should inspect
         * {@link #getParts()}.
         *
         * @return combined text component
         */
        public Component asComponent() {
            MutableComponent component = Component.empty();
            for (var part : parts) {
                if (part instanceof TextPart textPart) {
                    component.append(textPart.component());
                }
            }
            return component;
        }

        /**
         * Returns the plain string produced by this line's text parts.
         *
         * @return text-only representation
         */
        public String getText() {
            return asComponent().getString();
        }

        /**
         * Tests the text-only representation for a case-sensitive substring.
         *
         * @param text substring to search for; {@code null} returns {@code false}
         * @return {@code true} when the line text contains {@code text}
         */
        public boolean containsText(String text) {
            return text != null && getText().contains(text);
        }

        /**
         * Reports whether this line has no renderable content.
         *
         * @return {@code true} when the line has no parts or only spacer parts
         */
        public boolean isEmpty() {
            return parts.isEmpty() || parts.stream().allMatch(SpacerPart.class::isInstance);
        }
    }

    /**
     * Marker interface for rich tooltip line parts.
     */
    public interface TooltipPart {
    }

    /**
     * Text component part of a tooltip line.
     */
    public record TextPart(Component component) implements TooltipPart {
    }

    /**
     * Item icon part of a tooltip line.
     *
     * @param stack copied stack to render
     * @param small whether Jade should render the icon in small form
     */
    public record ItemIconPart(ItemStack stack, boolean small) implements TooltipPart {
    }

    /**
     * Jade built-in icon part of a tooltip line.
     */
    public record JadeIconPart(String iconName) implements TooltipPart {
    }

    /**
     * Empty space part of a tooltip line.
     */
    public record SpacerPart(int width, int height) implements TooltipPart {
    }
}
