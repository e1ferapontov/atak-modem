/*
 * Preferences.java
 *
 * Copyright (C) 2011 John Douyere (VK2ETA)
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.AndFlmsg;


import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

import java.util.Set;


public class myPreferences extends PreferenceActivity {

    protected static void setListPreferenceData(ListPreference lp) {
        CharSequence[] entries = new CharSequence[Modem.modesQty];
        CharSequence[] entryValues = new CharSequence[Modem.modesQty];

        Set<Integer> keyset = Modem.modemNamesByCode.keySet();

        int i = 0;
        for (int code : keyset) {
            String name = Modem.modemNamesByCode.get(code);

            entryValues[i] = String.valueOf(code);
            entries[i] = name;

            i += 1;
        }

        lp.setEntries(entries);
        lp.setEntryValues(entryValues);

        if (lp.getValue() == null) {
            int defaultModeCode = Modem.getModemCodeByName("8PSK1000");
            int savedModeCode = config.getPreferenceI("LASTMODEUSED", defaultModeCode);

            lp.setValue(String.valueOf(savedModeCode));
        }
        lp.setSummary(lp.getEntry());
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        final Activity activity = this;

        super.onCreate(savedInstanceState);
        //Start from the fixed section of the preferences
        addPreferencesFromResource(R.xml.preferences);

        // Add reset defaults preference
        Preference button = getPreferenceManager().findPreference("defaultPreferences");
        assert button != null;
        button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                config.restoreSettingsToDefault(activity);
                return true;
            }
        });

        // Modem digital modes list
        final ListPreference modemListPreference = (ListPreference) findPreference("mode");

        setListPreferenceData(modemListPreference);

        modemListPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                ListPreference lp = (ListPreference) preference;
                String oldValue = lp.getValue();

                if (newValue != oldValue) {
                    if (AndFlmsg.ProcessorON && !Processor.TXActive && Modem.modemState == Modem.RXMODEMRUNNING) {
                        Modem.changemode(Integer.parseInt((String) newValue));

                        lp.setSummary(Modem.getModemNameByCode(Integer.parseInt((String) newValue)));

                        loggingclass.writelog("Mode changed to " + Modem.getModemNameByCode(Integer.parseInt((String) newValue)), null);

                        return true;
                    }
                }

                return false;
            }
        });

        modemListPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                setListPreferenceData(modemListPreference);
                return false;
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Use instance field for listener
        // It will not be gc'd as long as this instance is kept referenced


        AndFlmsg.splistener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                // Implementation
                if (key.equals("AFREQUENCY") || key.equals("SLOWCPU") ||
                        key.startsWith("USE") || key.equals("RSID_ERRORS") ||
                        key.equals("RSIDWIDESEARCH")) {
                    AndFlmsg.RXParamsChanged = true;
                }
            }
        };

        AndFlmsg.mysp.registerOnSharedPreferenceChangeListener(AndFlmsg.splistener);

    }


}




