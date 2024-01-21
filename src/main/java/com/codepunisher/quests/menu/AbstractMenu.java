package com.codepunisher.quests.menu;

import com.codepunisher.quests.config.QuestsConfig;
import com.codepunisher.quests.models.ButtonType;
import com.codepunisher.quests.models.GuiInventory;
import com.codepunisher.quests.models.GuiItem;
import com.codepunisher.quests.util.UtilChat;
import fr.mrmicky.fastinv.FastInv;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractMenu extends FastInv {
    private final Map<ButtonType, Consumer<InventoryClickEvent>> clickHandlers = new HashMap<>();
    private final GuiInventory guiInventory;

    public AbstractMenu(Player player, QuestsConfig config, GuiInventory guiInventory) {
        super(guiInventory.getSize(), UtilChat.colorize(guiInventory.getTitle()));
        this.guiInventory = guiInventory;

        // Filling background (if the item matches the inventory type)
        config.getBackGroundItems().stream()
                .filter(guiItem -> guiItem.getGuiTypes().contains(guiInventory.getGuiType()))
                .forEach(guiItem -> {
                    guiItem.getSlots().forEach(slot -> {
                        setItem(slot, guiItem.getItemStack(), (event) -> {
                            handleGenericGuiItemDefaults(player, guiItem);
                        });
                    });
                });

        // Filling items from the gui inventory
        guiInventory.getGuiItems().forEach(guiItem -> {
            guiItem.getSlots().forEach(slot -> {
                setItem(slot, guiItem.getItemStack(), (event) -> {
                    handleGenericGuiItemDefaults(player, guiItem);

                    // Custom click handler (if present)
                    Optional.ofNullable(clickHandlers.get(guiItem.getButtonType())).ifPresent(eventConsumer -> {
                        eventConsumer.accept(event);
                    });
                });
            });
        });
    }

    @Override
    public void open(Player player) {
        super.open(player);

        // Gui open sound
        if (guiInventory.getOpenSound() != null) {
            player.playSound(player.getLocation(), guiInventory.getOpenSound(), 0.75f, 1.25f);
        }
    }

    protected void addClickHandler(ButtonType buttonType, Consumer<InventoryClickEvent> clickHandler) {
        this.clickHandlers.put(buttonType, clickHandler);
    }

    private void handleGenericGuiItemDefaults(Player player, GuiItem guiItem) {
        if (guiItem.getClickSound() != null) {
            player.playSound(player.getLocation(), guiItem.getClickSound(), 0.75f, 1.25f);
        }

        if (guiItem.isCloseOnClick()) {
            player.closeInventory();
        }
    }
}