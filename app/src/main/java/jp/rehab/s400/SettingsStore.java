package jp.rehab.s400;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    private static final String PREFS = "s400_settings";
    private final SharedPreferences p;

    SettingsStore(Context context) {
        p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String getMac() { return p.getString("mac", ""); }
    String getBindKey() { return p.getString("bindkey", ""); }
    String getSex() { return p.getString("sex", ""); }
    int getAge() { return p.getInt("age", 0); }
    int getHeightCm() { return p.getInt("height_cm", 0); }

    void save(String mac, String bindKey, String sex, int age, int heightCm) {
        p.edit()
                .putString("mac", mac)
                .putString("bindkey", bindKey)
                .putString("sex", sex)
                .putInt("age", age)
                .putInt("height_cm", heightCm)
                .apply();
    }
}
