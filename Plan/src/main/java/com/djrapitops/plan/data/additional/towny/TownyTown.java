package main.java.com.djrapitops.plan.data.additional.towny;

import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.TownyUniverse;
import java.io.Serializable;
import java.util.UUID;
import main.java.com.djrapitops.plan.Phrase;
import main.java.com.djrapitops.plan.data.additional.PluginData;
import static org.bukkit.Bukkit.getOfflinePlayer;
import org.bukkit.OfflinePlayer;

/**
 * PluginData class for Towny-plugin.
 *
 * Registered to the plugin by TownyHook
 *
 * Gives Town name as String.
 *
 * @author Rsl1122
 * @since 3.1.0
 * @see TownyHook
 */
public class TownyTown extends PluginData {

    /**
     * Class Constructor, sets the parameters of the PluginData object.
     */
    public TownyTown() {
        super("Towny", "town");
        super.setAnalysisOnly(false);
        super.setIcon("bank");
        super.setPrefix("Town: ");
    }

    @Override
    public String getHtmlReplaceValue(String modifierPrefix, UUID uuid) {
        OfflinePlayer offlinePlayer = getOfflinePlayer(uuid);
        if (!offlinePlayer.hasPlayedBefore()) {
            return parseContainer(modifierPrefix, Phrase.NOT_IN_TOWN + "");
        }
        String name = offlinePlayer.getName();
        try {
            Resident res = TownyUniverse.getDataSource().getResident(name);
            String town;
            if (res.hasTown()) {
                town = res.getTown().getName();
            } else {
                town = Phrase.NOT_IN_TOWN + "";
            }
            return parseContainer("", town);
        } catch (NotRegisteredException ex) {
            return parseContainer(modifierPrefix, Phrase.NOT_IN_TOWN + "");
        }
    }

    @Override
    public Serializable getValue(UUID uuid) {
        OfflinePlayer offlinePlayer = getOfflinePlayer(uuid);
        if (!offlinePlayer.hasPlayedBefore()) {
            return "";
        }
        String name = offlinePlayer.getName();
        try {
            Resident res = TownyUniverse.getDataSource().getResident(name);
            String town;
            if (res.hasTown()) {
                town = res.getTown().getName();
            } else {
                town = Phrase.NOT_IN_TOWN + "";
            }
            return town;
        } catch (NotRegisteredException ex) {
            return "";
        }
    }

}