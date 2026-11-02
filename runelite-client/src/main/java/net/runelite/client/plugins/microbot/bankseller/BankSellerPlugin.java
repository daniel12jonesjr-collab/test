package net.runelite.client.plugins.microbot.bankseller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Bank Seller",
        description = "Sells every tradeable bank item on the Grand Exchange, one item at a time",
        tags = {"grand exchange", "ge", "bank", "sell", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class BankSellerPlugin extends Plugin {

    @Inject
    private BankSellerScript script;

    @Inject
    private BankSellerConfig config;

    @Provides
    BankSellerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BankSellerConfig.class);
    }

    @Override
    protected void startUp() {
        log.info("Bank Seller started");
        script.run(config);
    }

    @Override
    protected void shutDown() {
        log.info("Bank Seller stopped");
        script.shutdown();
    }
}
