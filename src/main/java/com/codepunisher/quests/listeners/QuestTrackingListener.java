package com.codepunisher.quests.listeners;

import com.codepunisher.quests.cache.QuestCache;
import com.codepunisher.quests.cache.QuestPlayerCache;
import com.codepunisher.quests.models.Quest;
import com.codepunisher.quests.models.ActiveQuestPlayerData;
import com.codepunisher.quests.models.QuestType;
import com.codepunisher.quests.util.ItemBuilder;
import com.codepunisher.quests.util.UtilChat;
import lombok.AllArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

@AllArgsConstructor
public class QuestTrackingListener implements Listener {
  private final Map<UUID, BossBar> bossBarMap = new HashMap<>();
  private final JavaPlugin plugin;
  private final QuestPlayerCache playerCache;
  private final QuestCache questCache;

  @EventHandler
  public void onBreak(BlockBreakEvent event) {
    handleQuestProgressIncrease(
        event.getPlayer(), QuestType.BLOCK_BREAK, event.getBlock().getType(), 1);
  }

  @EventHandler
  public void onCraft(CraftItemEvent event) {
    ItemStack test = event.getRecipe().getResult().clone();
    ClickType click = event.getClick();

    int recipeAmount = test.getAmount();
    switch (click) {
      case NUMBER_KEY:
        if (event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) != null)
          recipeAmount = 0;
        break;

      case DROP:
      case CONTROL_DROP:
        ItemStack cursor = event.getCursor();
        if (!ItemBuilder.isAir(cursor)) recipeAmount = 0;
        break;

      case SHIFT_RIGHT:
      case SHIFT_LEFT:
        if (recipeAmount == 0) break;

        int maxCraftable = getMaxCraftAmount(event.getInventory());
        int capacity = fits(test, event.getView().getBottomInventory());
        if (capacity < maxCraftable)
          maxCraftable = ((capacity + recipeAmount - 1) / recipeAmount) * recipeAmount;

        recipeAmount = maxCraftable;
        break;
      default:
    }

    // No use continuing if we haven't actually crafted a thing
    if (recipeAmount == 0) return;

    Player player = (Player) event.getWhoClicked();
    handleQuestProgressIncrease(
        player, QuestType.CRAFTING, event.getRecipe().getResult().getType(), recipeAmount);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    bossBarMap.remove(event.getPlayer().getUniqueId());
  }

  private <T> void handleQuestProgressIncrease(
      Player player, QuestType questType, T associatedObject, int progressIncrease) {
    UUID uuid = player.getUniqueId();
    Optional<ActiveQuestPlayerData> playerDataOptional = playerCache.getActiveQuestPlayerData(uuid);
    if (playerDataOptional.isEmpty()) {
      return;
    }

    ActiveQuestPlayerData playerData = playerDataOptional.get();
    String questId = playerData.getCurrentQuestId();
    Optional<Quest> questOptional = questCache.getQuest(questId);
    if (questOptional.isEmpty()) {
      return;
    }

    Quest quest = questOptional.get();
    if (quest.getQuestType() != questType) {
      return;
    }

    // Checking if the associated type (material, etc.) matches
    if (!questType
        .getInputFromAssociatedObject(associatedObject)
        .equalsIgnoreCase(questType.getInputFromAssociatedObject(quest.getAssociatedObject()))) {
      return;
    }

    Optional<Integer> requirementOptional = questCache.getRequirement(questId);
    if (requirementOptional.isEmpty()) {
      return;
    }

    int requirement = requirementOptional.get();
    playerData.incrementQuestProgress(progressIncrease);
    displayBossBarProgress(player, playerData);

    // Quest completion
    int progress = playerData.getCurrentQuestProgress();
    if (progress >= requirement) {
      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.75f);
      player.sendTitle(UtilChat.colorize("&aQuest Complete"), UtilChat.colorize("good job!!!"));
      playerData.setCurrentQuestIdAsCompleted();
      Arrays.stream(quest.getRewards())
          .forEach(
              reward -> {
                Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(), reward.replaceAll("%player%", player.getName()));
              });

      // Checking if they've completed all daily quests (new rewards)
      if (playerData.getCompletedDailyQuests().size()
          >= questCache.getActiveQuestsEntrySet().size()) {
        player.sendMessage(UtilChat.colorize("&aOmg you totally completed all quests!"));
        player.getInventory().addItem(new ItemStack(Material.DIAMOND_BLOCK, 64));
      }
    }

    player.sendActionBar(
        UtilChat.colorize(
            String.format(
                "&aTotal broken for quest: &8(&f%s&7/&f%s&8)",
                Math.min(progress, requirement), requirement)));
  }

  /**
   * Displays current progress on player boss bar object and goes away after a few seconds. This is
   * working with the boss bar map
   */
  private void displayBossBarProgress(Player player, ActiveQuestPlayerData playerData) {
    UUID uuid = player.getUniqueId();

    // Removing previous boss bar
    BossBar bossBar = bossBarMap.get(uuid);
    if (bossBar != null) {
      bossBar.removePlayer(player);
    }

    // Creating new boss bar/displaying
    BossBar newBossBar = Bukkit.createBossBar(
            UtilChat.colorize("&a" + playerData.getCurrentQuestId()),
            BarColor.GREEN,
            BarStyle.SOLID);

    newBossBar.setVisible(true);
    bossBarMap.put(uuid, newBossBar);

    questCache.getRequirement(playerData.getCurrentQuestId()).ifPresent(requirement -> {
      int currentProgress = playerData.getCurrentQuestProgress();
      double completionPercentage = (double) currentProgress / requirement;
      double normalizedProgress = Math.min(1.0, Math.max(0.0, completionPercentage));
      newBossBar.setProgress(normalizedProgress);
    });

    newBossBar.addPlayer(player);

    // Removing after 3 seconds (if not already removed)
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
      newBossBar.removePlayer(player);
    }, 60L);
  }

  private int getMaxCraftAmount(CraftingInventory inv) {
    if (inv.getResult() == null) return 0;

    int resultCount = inv.getResult().getAmount();
    int materialCount = Integer.MAX_VALUE;

    for (ItemStack is : inv.getMatrix())
      if (is != null && is.getAmount() < materialCount) materialCount = is.getAmount();

    return resultCount * materialCount;
  }

  private int fits(ItemStack stack, Inventory inv) {
    ItemStack[] contents = inv.getContents();
    int result = 0;

    for (ItemStack is : contents)
      if (is == null) result += stack.getMaxStackSize();
      else if (is.isSimilar(stack)) result += Math.max(stack.getMaxStackSize() - is.getAmount(), 0);

    return result;
  }
}
