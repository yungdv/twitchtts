package com.twitchtts.item;

import com.twitchtts.TwitchTts;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item TWITCH_BERRY = register("twitch_berry", new Item(new Item.Settings()));
    public static final Item STREAMER_COIN = register("streamer_coin", new Item(new Item.Settings()));
    
    // НОВЫЙ ПРЕДМЕТ: Корона Стримера
    public static final Item STREAMER_CROWN = register("streamer_crown", new Item(new Item.Settings().maxCount(1)));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(TwitchTts.MOD_ID, name), item);
    }

    public static void registerModItems() {
        TwitchTts.LOGGER.info("Registering Mod Items for " + TwitchTts.MOD_ID);
        
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(TWITCH_BERRY);
            entries.add(STREAMER_COIN);
            entries.add(STREAMER_CROWN); // Добавляем в творческую вкладку
        });
    }
}