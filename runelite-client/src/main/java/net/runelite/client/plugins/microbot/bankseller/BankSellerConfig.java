package net.runelite.client.plugins.microbot.bankseller;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bankseller")
public interface BankSellerConfig extends Config {

    enum ItemFilter {
        /** F2P world: only F2P items. Members world: everything. */
        AUTO_BY_WORLD,
        F2P_ONLY,
        P2P_ONLY
    }

    @ConfigItem(
            keyName = "itemFilter",
            name = "Items to sell",
            description = "AUTO_BY_WORLD sells only F2P items on F2P worlds and everything on members worlds",
            position = 0
    )
    default ItemFilter itemFilter() {
        return ItemFilter.AUTO_BY_WORLD;
    }

    @ConfigItem(
            keyName = "excludedItems",
            name = "Never sell",
            description = "Comma separated item names that are never sold, e.g. Abyssal whip,Dragon scimitar",
            position = 1
    )
    default String excludedItems() {
        return "";
    }

    @ConfigItem(
            keyName = "sellTimeoutSeconds",
            name = "Sell timeout (s)",
            description = "Abort the offer if it has not sold after this many seconds",
            position = 2
    )
    default int sellTimeoutSeconds() {
        return 5;
    }
}
