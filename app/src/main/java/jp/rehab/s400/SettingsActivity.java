package jp.rehab.s400;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public final class SettingsActivity extends Activity {
    private EditText macInput;
    private EditText bindKeyInput;
    private RadioButton male;
    private RadioButton female;
    private EditText ageInput;
    private EditText heightInput;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("S400設定");
        SettingsStore settings = new SettingsStore(this);

        LinearLayout root = column(18);
        root.setPadding(28, 26, 28, 26);

        TextView note = text("この試作版はS400のBLE広告を直接受信します。\nMACアドレスとBLE bindkeyは端末内にのみ保存します。", 16);
        root.addView(note, marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, 18));

        root.addView(label("S400 MACアドレス (XX:XX:XX:XX:XX:XX)"));
        macInput = edit(settings.getMac(), false);
        root.addView(macInput);

        root.addView(label("BLE bindkey (32桁hex)"));
        bindKeyInput = edit(settings.getBindKey(), false);
        bindKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(bindKeyInput);

        root.addView(label("プロフィール（体組成計算を後で追加するための項目）"));
        RadioGroup sex = new RadioGroup(this);
        sex.setOrientation(RadioGroup.HORIZONTAL);
        male = new RadioButton(this); male.setText("男性");
        female = new RadioButton(this); female.setText("女性");
        sex.addView(male); sex.addView(female);
        String savedSex = settings.getSex();
        if ("male".equals(savedSex)) male.setChecked(true);
        if ("female".equals(savedSex)) female.setChecked(true);
        root.addView(sex);

        root.addView(label("年齢"));
        ageInput = edit(settings.getAge() > 0 ? Integer.toString(settings.getAge()) : "", false);
        ageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(ageInput);

        root.addView(label("身長(cm)"));
        heightInput = edit(settings.getHeightCm() > 0 ? Integer.toString(settings.getHeightCm()) : "", false);
        heightInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(heightInput);

        Button save = new Button(this);
        save.setText("保存");
        save.setOnClickListener(v -> save(settings));
        root.addView(save, marginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 18, 0, 0));

        Button cancel = new Button(this);
        cancel.setText("戻る");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void save(SettingsStore settings) {
        String mac = macInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        String bind = bindKeyInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!mac.matches("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")) {
            toast("MACアドレスの形式が正しくありません"); return;
        }
        if (!bind.matches("^[0-9a-f]{32}$")) {
            toast("bindkeyは32桁の16進数です"); return;
        }
        int age = parseInt(ageInput.getText().toString(), 0);
        int height = parseInt(heightInput.getText().toString(), 0);
        if (age != 0 && (age < 10 || age > 120)) { toast("年齢は10〜120歳"); return; }
        if (height != 0 && (height < 50 || height > 250)) { toast("身長は50〜250cm"); return; }
        String sex = male.isChecked() ? "male" : (female.isChecked() ? "female" : "");
        settings.save(mac, bind, sex, age, height);
        toast("保存しました");
        finish();
    }

    private static int parseInt(String s, int fallback) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; } }
    private EditText edit(String value, boolean multi) {
        EditText e = new EditText(this); e.setText(value); e.setTextSize(17); e.setSingleLine(!multi); return e;
    }
    private TextView label(String s) { TextView t = text(s, 14); t.setTextColor(Color.DKGRAY); return t; }
    private TextView text(String s, float size) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); return t; }
    private LinearLayout column(int spacing) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout.LayoutParams marginParams(int w,int h,int l,int top,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(l,top,r,b);return p;}
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
