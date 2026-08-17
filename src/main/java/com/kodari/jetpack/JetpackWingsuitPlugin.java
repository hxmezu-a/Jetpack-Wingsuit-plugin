package com.kodari.jetpack;

import com.kodari.jetpack.command.JetpackCommand;
import com.kodari.jetpack.flight.FlightManager;
import com.kodari.jetpack.item.FlightItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class JetpackWingsuitPlugin extends JavaPlugin {
    private FlightItemFactory itemFactory;
    private FlightManager flightManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        itemFactory = new FlightItemFactory(this);
        flightManager = new FlightManager(this, itemFactory);
        Bukkit.getPluginManager().registerEvents(flightManager, this);
        Bukkit.getScheduler().runTaskTimer(this, flightManager::tick, 1L, 1L);

        JetpackCommand command = new JetpackCommand(this, itemFactory);
        getCommand("jpwsadmin").setExecutor(command);
        getCommand("jpwsadmin").setTabCompleter(command);
    }

    public FlightManager getFlightManager() {
        return flightManager;
    }
}
