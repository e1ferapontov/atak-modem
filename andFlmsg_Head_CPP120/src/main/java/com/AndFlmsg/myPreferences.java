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
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import java.util.Set;

import static com.AndFlmsg.AndFlmsg.mysp;
import static com.AndFlmsg.Modem.DEFAULT_SQL_LEVEL;


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
            int savedModeCode = config.getPreferenceI("LASTMODEUSED", Modem.DEFAULT_MODEM_CODE);

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

        // Squelch level input
        final int minSql = 0;
        final int maxSql = 100;

        final EditTextPreference editTextPreference = (EditTextPreference) findPreference("SQL");
        float etpInitialValue = AndFlmsg.mysp.getFloat("SQUELCHVALUE", DEFAULT_SQL_LEVEL);
        editTextPreference.setText(String.valueOf(etpInitialValue));
        editTextPreference.setSummary(editTextPreference.getText());

        editTextPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                EditTextPreference etp = (EditTextPreference) preference;

                float val = Float.parseFloat(newValue.toString());
                if ((val > minSql) && (val < maxSql)) {

                    Modem.setSquelch(val);
                    etp.setSummary(newValue.toString());

                    loggingclass.writelog("SQL set to " + newValue.toString(), null);

                    return true;
                }
                else {
                    // invalid you can show invalid message
                    Toast.makeText(getApplicationContext(), "SQL value must be more than 0 and less than 100! Changes weren't saved!", Toast.LENGTH_LONG).show();
                    return false;
                }
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
                    if (AndFlmsg.ProcessorON && !ModemService.TXActive && Modem.modemState == Modem.RXMODEMRUNNING) {
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

        mysp.registerOnSharedPreferenceChangeListener(AndFlmsg.splistener);

    }


}




